package net.subsloth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.domain.policy.ApiBaseUrlPolicy
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.domain.policy.QualityPolicy
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.domain.port.StoragePort
import net.subsloth.core.media.download.ConnectivityChecker
import net.subsloth.core.media.download.DownloadController
import net.subsloth.core.media.download.DownloadForegroundService
import net.subsloth.core.media.download.parseResolution
import net.subsloth.core.media.download.DownloadStorageManager
import net.subsloth.core.media.download.DownloadTarget
import net.subsloth.core.media.download.DownloadTransferCoordinator
import net.subsloth.core.media.download.DownloadTransferer
import net.subsloth.core.media.download.SeasonQueueController
import net.subsloth.core.media.download.SeasonQueueDriver
import net.subsloth.core.media.download.StorageProvider
import net.subsloth.core.media.download.TransferEvent
import net.subsloth.core.media.playback.OfflineFirstPlaybackPort
import net.subsloth.core.media.playback.OfflineSourceResolver
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.fold
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.core.network.media.mapper.Mapper
import net.subsloth.core.network.media.playback.ApiPlaybackPort
import net.subsloth.database.LibraryPortAdapter
import net.subsloth.database.SubSlothDatabase
import net.subsloth.database.createSubSlothDatabase
import net.subsloth.database.dao.CachedCatalogDao
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.CachedCatalogCountryEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.WatchedStateEntity
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity
import net.subsloth.database.entity.CachedCatalogItemWithMetadata
import net.subsloth.preferences.AccountProfileStore
import net.subsloth.preferences.CredentialStore
import net.subsloth.preferences.CredentialsStoreAdapter
import net.subsloth.preferences.UserPreferences
import net.subsloth.catalog.HomeViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Application-level dependency container.
 *
 * Initialised once in [SubSlothApplication.onCreate] and exposed via
 * the Application instance. Survives configuration changes and
 * Activity recreation. This is a manual-service-locator pattern
 * (no DI framework) — only dependencies that need to outlive a
 * screen or Activity live here.
 */
@Suppress("TooManyFunctions") // Composition root: one small function per port callback, by design.
class AppContainer(context: Context) {
    private val log = Logger.withTag("AppContainer")

    /** System clock implementation. */
    val clock: Clock = Clock.System

    /**
     * Process-lifetime coroutine scope. Never cancelled — [AppContainer]
     * lives as long as [SubSlothApplication], so a [SupervisorJob]-backed
     * scope that outlives every launch is the accepted pattern here (see
     * [dataStore]'s own scope below), not a leak.
     */
    private val containerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Read-only view of [containerScope] for work that must outlive a
     * ViewModel — e.g. the player's final progress flush, which runs after
     * AndroidX has already cancelled `viewModelScope`.
     */
    val externalScope: CoroutineScope get() = containerScope

    /** DataStore for user preferences. */
    val dataStore: DataStore<Preferences> by lazy {
        val appContext = context.applicationContext
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { File(appContext.filesDir, "subsloth.preferences_pb") },
        )
    }

    /** User preferences backed by DataStore. */
    val userPreferences: UserPreferences by lazy {
        UserPreferences(dataStore)
    }

    /**
     * Derives non-reversible [AccountProfileKey]s from logins via
     * HMAC-SHA256 and an app-local, per-install salt (see its own doc).
     * Shares [dataStore] rather than opening a second `DataStore` against
     * the same backing file, which would throw at runtime (see
     * `LogoutCleanupInstrumentedTest`'s doc for why this project never
     * opens two `DataStore` instances over one file within a process).
     */
    private val accountProfileStore: AccountProfileStore by lazy {
        AccountProfileStore(dataStore)
    }

    /** Room database for cached catalog, library, and playback state. */
    val database: SubSlothDatabase by lazy {
        createSubSlothDatabase("subsloth_db")
    }

    /** Cached catalog DAO. */
    val cachedCatalogDao by lazy { database.cachedCatalogDao() }

    /**
     * Delegates every [CachedCatalogDao] call through to [cachedCatalogDao],
     * resolved lazily at call time rather than when this property itself is
     * constructed. [buildCatalogRepository] passes this instead of
     * [cachedCatalogDao] directly so building a [CatalogRepository] — including
     * [currentCatalogRepository]'s eager initial value, assigned as part of
     * [AppContainer]'s own constructor running on the caller's thread (the main
     * thread, per `SubSlothApplication.onCreate`) — never forces [database]'s
     * lazy Room initialization onto that thread. Room only actually opens once a
     * DAO method is invoked, and every real call site reaches these methods via
     * a suspend function or a collected [kotlinx.coroutines.flow.Flow], already
     * off the main thread.
     */
    private val deferredCachedCatalogDao: CachedCatalogDao = object : CachedCatalogDao {
        override fun getAllByType(contentType: String) = cachedCatalogDao.getAllByType(contentType)
        override fun getAllGenres() = cachedCatalogDao.getAllGenres()
        override fun getAllCountries() = cachedCatalogDao.getAllCountries()
        override suspend fun upsertAll(items: List<CachedCatalogItemEntity>) = cachedCatalogDao.upsertAll(items)
        override suspend fun deleteAll() = cachedCatalogDao.deleteAll()
        override suspend fun count() = cachedCatalogDao.count()
        override suspend fun deleteAllGenres() = cachedCatalogDao.deleteAllGenres()
        override suspend fun upsertAllGenres(items: List<CachedCatalogGenreEntity>) =
            cachedCatalogDao.upsertAllGenres(items)
        override suspend fun deleteAllCountries() = cachedCatalogDao.deleteAllCountries()
        override suspend fun upsertAllCountries(items: List<CachedCatalogCountryEntity>) =
            cachedCatalogDao.upsertAllCountries(items)
        override suspend fun replaceAll(items: List<CachedCatalogItemWithMetadata>) =
            cachedCatalogDao.replaceAll(items)
    }

    /**
     * Production session port. Persists credentials via the Keystore-backed
     * [CredentialsStoreAdapter]/[CredentialStore] and validates them against
     * the same API base URL resolution [resolveApiBaseUrl] uses.
     *
     * Kept as the concrete [AndroidSessionState] type privately so [init]
     * below can call its [AndroidSessionState.recover] — not part of the
     * [SessionPort] interface — exactly once at cold start; [sessionPort]
     * exposes it to the rest of the app as the plain [SessionPort] contract.
     */
    private val androidSessionState = AndroidSessionState(
        credentialsPort = CredentialsStoreAdapter(CredentialStore()),
        baseUrlProvider = { resolveApiBaseUrl() },
        accountProfileStore = accountProfileStore,
        clock = clock,
    )

    /** [SessionPort] state starts as `Anonymous` until cold-start recovery (see [init]) completes. */
    val sessionPort: SessionPort = androidSessionState

    /**
     * Media API client reflecting the current [sessionPort] session:
     * authenticated (BasicAuth) when [Session.Authenticated], anonymous
     * otherwise. Starts as an anonymous client built against
     * [ClientFactory]'s default base URL and is replaced — together with
     * [catalogRepository], which wraps it — every time [sessionPort]'s
     * state actually changes (see the `collect` launch in `init` below).
     */
    @Volatile
    private var currentApi: Api = Api(ClientFactory.create())

    /**
     * Catalog repository combining API sync with Room caching. Its initial
     * value is built eagerly, same as [currentApi] — safe despite [database]
     * being Room-backed, because [buildCatalogRepository] routes DAO access
     * through [deferredCachedCatalogDao], which never actually touches
     * [database] until a query genuinely runs (see its doc for why that
     * matters here specifically).
     */
    @Volatile
    private var currentCatalogRepository: CatalogRepository = buildCatalogRepository(currentApi)

    private val downloadStorageManager: DownloadStorageManager by lazy { DownloadStorageManager(context) }
    private val storageProvider: StoragePort by lazy { StorageProvider(context) }
    private val connectivityChecker: ConnectivityPort by lazy { ConnectivityChecker(context) }

    /**
     * Production [net.subsloth.core.domain.port.DownloadsPort] implementation.
     * Downloaded media is shared across accounts and logged-out state (its
     * backing DAOs carry no profile key), so unlike [catalogRepository] this
     * never needs to be rebuilt when the session changes.
     */
    val downloadController: DownloadController by lazy {
        DownloadController(
            storageManager = downloadStorageManager,
            storageProvider = storageProvider,
            connectivityChecker = connectivityChecker,
            downloadedMediaDao = database.downloadedMediaDao(),
            downloadedSubtitleDao = database.downloadedSubtitleDao(),
            offlineDisplayMetadataDao = database.offlineDisplayMetadataDao(),
        )
    }

    /**
     * Offline playback source resolution over [downloadController]'s stored
     * assets, verified through [downloadStorageManager]. Session-independent
     * (like [downloadController]); consumed by [currentPlaybackPort]'s
     * [OfflineFirstPlaybackPort] wrapper.
     *
     * Declared after its [lazy] dependencies on purpose: [currentPlaybackPort]
     * below is an eager initializer that forces this lazy during construction,
     * and a `lazy` delegate reading a property declared later in the class
     * would hit a null delegate (Kotlin assigns delegate fields in
     * declaration order — this ordering bug crashed both apps at startup).
     */
    private val offlineSourceResolver: OfflineSourceResolver by lazy {
        OfflineSourceResolver(
            offlineAssets = { downloadController.listOfflineAssets() },
            files = downloadStorageManager,
        )
    }

    /**
     * Production [net.subsloth.core.domain.port.PlaybackPort] implementation
     * over the session's [Api], wrapped by [OfflineFirstPlaybackPort] so a
     * verified local download is played directly (offline mode, no network)
     * before falling back to stream resolution. Rebuilt together with
     * [catalogRepository] whenever the session changes, so stream-URL
     * resolution always uses the current credentials (signed stream URLs
     * come from authenticated detail responses); the offline resolver is
     * session-independent (downloads are shared across accounts — see
     * [downloadController]'s doc).
     */
    @Volatile
    private var currentPlaybackPort: PlaybackPort = OfflineFirstPlaybackPort(
        offlineSourceResolver = offlineSourceResolver,
        online = ApiPlaybackPort(currentApi),
    )

    val api: Api get() = currentApi

    val catalogRepository: CatalogRepository get() = currentCatalogRepository

    val playbackPort: PlaybackPort get() = currentPlaybackPort

    /**
     * Production [net.subsloth.core.domain.port.LibraryPort] implementation:
     * favorites, watch later, and custom-list membership, persisted to Room
     * and scoped by
     * the active session's user profile key (read fresh from [sessionPort]
     * on every call — see [LibraryPortAdapter.profileKey]), so a single
     * instance stays correct across login/logout/account switches.
     */
    val libraryPortAdapter: LibraryPortAdapter by lazy {
        LibraryPortAdapter(
            favoriteDao = database.favoriteDao(),
            watchLaterDao = database.watchLaterDao(),
            localLibraryDao = database.localLibraryRecordDao(),
            sessionPort = sessionPort,
        )
    }

    /** Season-level download queue orchestration, wrapping [downloadController]. */
    private val seasonQueueController: SeasonQueueController by lazy {
        SeasonQueueController(
            downloadsPort = downloadController,
            seasonQueueDao = database.seasonQueueDao(),
            clock = clock,
        )
    }

    /** Advances a confirmed season queue as each episode transfer finishes. */
    private val seasonQueueDriver: SeasonQueueDriver by lazy {
        SeasonQueueDriver(
            controller = seasonQueueController,
            downloadsPort = downloadController,
        )
    }


    /**
     * Byte-transfer client for downloads. Deliberately a bare anonymous
     * client: download URLs are server-signed (`wmsAuthSign`), so no
     * auth headers are needed, and a session-scoped client would go stale
     * when the session rebuilds (the resolver below reads [api] live
     * instead).
     */
    private val downloadTransferer: DownloadTransferer by lazy {
        DownloadTransferer(
            client = ClientFactory.create(),
            store = downloadStorageManager,
        )
    }

    /**
     * Drives real byte transfers for queued downloads (see
     * [DownloadTransferCoordinator]). The download-URL resolver reads the
     * session-scoped [api] live on every call, so transfers always use
     * current credentials despite the coordinator itself being
     * session-independent.
     */
    val downloadTransferCoordinator: DownloadTransferCoordinator by lazy {
        DownloadTransferCoordinator(
            downloadedMediaDao = database.downloadedMediaDao(),
            downloadedSubtitleDao = database.downloadedSubtitleDao(),
            store = downloadStorageManager,
            transferer = downloadTransferer,
            connectivityChecker = connectivityChecker,
            clock = clock,
            resolveDownloadUrl = ::resolveDownloadTarget,
            resolveSubtitles = ::resolveSubtitleTracks,
        )
    }


    init {
        // Cold-start session recovery — invoked exactly once, unconditionally,
        // as part of container construction.
        containerScope.launch { androidSessionState.recover() }

        // Drive real download byte transfers for the process lifetime and
        // surface progress through the foreground service. The app is in
        // the foreground whenever a download is enqueued (user action), so
        // the foreground-service start satisfies the background-start
        // restriction.
        containerScope.launch { downloadTransferCoordinator.runWatcher() }
        containerScope.launch {
            val active = mutableSetOf<String>()
            val appContext = context.applicationContext
            downloadTransferCoordinator.events.collect { event ->
                when (event) {
                    is TransferEvent.Progress -> {
                        if (active.add(event.localId.value)) DownloadForegroundService.start(appContext)
                        val percent = event.totalBytes?.takeIf { it > 0 }
                            ?.let { total -> event.bytesWritten * 100 / total }
                            ?.toInt()
                            ?: 0
                        DownloadForegroundService.updateProgress(appContext, active.size, percent)
                    }

                    is TransferEvent.Completed -> {
                        active.remove(event.localId.value)
                        if (active.isEmpty()) DownloadForegroundService.stop(appContext)
                    }

                    is TransferEvent.Failed -> {
                        active.remove(event.localId.value)
                        if (active.isEmpty()) DownloadForegroundService.stop(appContext)
                    }
                }
            }
        }

        // Rebuild the authenticated client (and the repository wrapping it)
        // whenever the session's credentials actually change. StateFlow only
        // emits on a structural change, so this does not rebuild on every
        // access or poll for changes.
        containerScope.launch {
            sessionPort.state.collect { session ->
                val previousApi = currentApi
                val newApi = buildApi(session)
                currentApi = newApi
                currentCatalogRepository = buildCatalogRepository(newApi)
                currentPlaybackPort = OfflineFirstPlaybackPort(
                    offlineSourceResolver = offlineSourceResolver,
                    online = ApiPlaybackPort(newApi),
                )
                // Close the superseded client only after the new one is fully
                // swapped in, so nothing still references it as "current".
                previousApi.close()
            }
        }
    }

    /**
     * Resolves a progressive (single-file) download URL for [mediaId],
     * preferring the item's top-level `download_url`, falling back to the
     * quality variant matching the requested label. HLS playlists
     * (`.m3u8`) are not single files and are rejected here. Returns null
     * on any failure (the coordinator marks the download FAILED).
     */
    @Suppress("TooGenericExceptionCaught") // Network-boundary catch-all, same pattern as playback resolution.
    private suspend fun resolveDownloadTarget(
        mediaId: Media.MediaId,
        qualityLabel: String?,
    ): DownloadTarget? = try {
        when (mediaId) {
            is Media.MediaId.Movie -> api.getMovie(mediaId.value.value).let { dto ->
                pickDownloadTarget(dto.downloadUrl, Mapper.mapQualities(dto.qualities), qualityLabel)
            }

            is Media.MediaId.Episode -> api.getEpisode(mediaId.value.value).let { dto ->
                pickDownloadTarget(dto.downloadUrl, Mapper.mapQualities(dto.qualities), qualityLabel)
            }

            is Media.MediaId.Show -> null
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "Download URL resolution failed for $mediaId" }
        null
    }

    /**
     * Resolves the subtitle tracks offered for [mediaId] at transfer time.
     * Signed subtitle URLs are ephemeral, so they are fetched fresh and
     * never persisted.
     */
    private suspend fun resolveSubtitleTracks(mediaId: Media.MediaId): List<Subtitle> {
        val tracks = when (mediaId) {
            is Media.MediaId.Movie -> api.getMovie(mediaId.value.value).subtitles

            is Media.MediaId.Episode -> api.getEpisode(mediaId.value.value).subtitles

            is Media.MediaId.Show -> null
        }
        return Mapper.mapSubtitleTracks(tracks)
    }

    private fun pickDownloadTarget(
        topDownloadUrl: String?,
        qualities: List<Quality>,
        qualityLabel: String?,
    ): DownloadTarget? {
        val url = topDownloadUrl
            ?: qualityLabel?.let { label ->
                val preferred = parseResolution(label)
                val descriptor = DownloadPolicy.selectFallbackQuality(
                    available = qualities.map { it.info },
                    preferred = preferred,
                )
                qualities.firstOrNull { it.info == descriptor }?.url
            }
            ?.takeIf { candidate -> !candidate.substringBefore('?').endsWith(".m3u8") }
            ?: return null
        return DownloadTarget(url = url, extension = downloadExtension(url))
    }

    private fun downloadExtension(url: String): String {
        val path = url.substringBefore('?').substringAfterLast('/')
        val extension = path.substringAfterLast('.', "")
        return extension.ifBlank { "mp4" }.filter { it.isLetterOrDigit() }.ifBlank { "mp4" }
    }

    /**
     * Resolves the API base URL from preference *presence*
     * ([ApiBaseUrlPolicy]): a deliberately saved non-blank
     * [UserPreferences.storedApiBaseUrl] value — including the default —
     * wins over a non-blank [BuildConfig.SUBSLOTH_API_BASE_URL]; an absent
     * or blank preference falls back to the build-config value and then to
     * the default. Shared by the session validation client, [buildApi], and
     * the login screens ([apiBaseUrlFlow]).
     */
    private suspend fun resolveApiBaseUrl(): String = apiBaseUrlFlow().first()

    /**
     * Flow form of [resolveApiBaseUrl] for `LoginViewModel`'s
     * `readApiBaseUrl`, so the login form and [ClientFactory] always agree
     * on the effective URL.
     */
    fun apiBaseUrlFlow(): Flow<String> = userPreferences.storedApiBaseUrl().map { stored ->
        ApiBaseUrlPolicy.resolve(stored = stored, configured = BuildConfig.SUBSLOTH_API_BASE_URL)
    }

    private suspend fun buildApi(session: Session): Api {
        val baseUrl = resolveApiBaseUrl()
        val client = when (session) {
            is Session.Authenticated -> ClientFactory.create(
                login = session.credentials.login,
                password = session.credentials.password,
                baseUrl = baseUrl,
            )

            Session.Anonymous -> ClientFactory.create(baseUrl = baseUrl)
        }
        return Api(client)
    }

    private fun buildCatalogRepository(api: Api): CatalogRepository = CatalogRepository(
        api = api,
        catalogDao = deferredCachedCatalogDao,
        userPreferences = userPreferences,
        clock = clock,
    )

    /**
     * Adapts [catalogRepository]'s generic [Media] catalog stream into
     * [net.subsloth.library.LibraryViewModel]'s narrower [MovieSummary]
     * list shape. [CatalogRepository.catalogItems] already partitions by
     * `contentType` ("movie"/"show") at the Room-query level, and its
     * cache-to-domain mapper only ever produces a [MovieSummary] for
     * "movie" rows — so [filterIsInstance] here is a type-safety net, not
     * a guess. Reads [catalogRepository] live (not captured), matching
     * [HomeViewModelFactory]'s anti-stale-capture discipline.
     */
    suspend fun listMovies(): Result<List<MovieSummary>> = runCatching {
        catalogRepository.catalogItems("movie").first().filterIsInstance<MovieSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    /** Show-list counterpart of [listMovies]; see its doc for the mapping rationale. */
    suspend fun listShows(): Result<List<ShowSummary>> = runCatching {
        catalogRepository.catalogItems("show").first().filterIsInstance<ShowSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Merged movie+show catalog for the search screen (`SearchViewModel`'s
     * `listCatalog`): reads the same cached-catalog lists as [listMovies]/
     * [listShows], wrapped in the `Outcome` shape the search ViewModel
     * consumes. A failed side degrades to a failure (the search screen
     * then renders no results rather than a partial list).
     */
    suspend fun listAllMedia(): Outcome<List<Media>> {
        val movies = listMovies()
        val shows = listShows()
        return when {
            movies.isSuccess && shows.isSuccess -> Outcome.Success(
                movies.getOrThrow() + shows.getOrThrow(),
            )

            else -> {
                val error = movies.exceptionOrNull() ?: shows.exceptionOrNull()
                    ?: return Outcome.Success(emptyList())
                Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(error))
            }
        }
    }

    /**
     * Fetches subtitle document text for the player's Compose subtitle
     * layer. Subtitle URLs are ephemeral public streams (same trust level
     * as the video stream URL), so a plain unauthenticated GET is used,
     * bounded by timeouts and a response-size cap.
     */
    suspend fun fetchSubtitleText(url: String): Outcome<String> = withContext(Dispatchers.IO) {
        try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = SUBTITLE_TIMEOUT_MS
            connection.readTimeout = SUBTITLE_TIMEOUT_MS
            val bytes = connection.inputStream.use { input ->
                val buffer = ByteArray(SUBTITLE_MAX_BYTES + 1)
                var read = 0
                while (read <= SUBTITLE_MAX_BYTES) {
                    val count = input.read(buffer, read, buffer.size - read)
                    if (count < 0) break
                    read += count
                }
                if (read > SUBTITLE_MAX_BYTES) {
                    throw IOException("Subtitle document from $url exceeds $SUBTITLE_MAX_BYTES bytes")
                }
                buffer.copyOf(read)
            }
            Outcome.Success(bytes.decodeToString())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            log.e(exception) { "fetchSubtitleText failed for $url" }
            Outcome.Failure(NetworkError.UnexpectedResponse)
        } catch (exception: URISyntaxException) {
            log.e(exception) { "fetchSubtitleText failed for $url" }
            Outcome.Failure(NetworkError.UnexpectedResponse)
        }
    }

    /**
     * Maps the active session's account-scoped playback progress into the
     * [PlaybackProgress] domain shape consumed by
     * [net.subsloth.library.LibraryViewModel]'s "Continue Watching" row.
     *
     * Only [SubSlothDatabase.accountPlaybackProgressDao] is mapped here: its
     * [AccountPlaybackProgressEntity.contentType] column makes reconstructing
     * a [Media.MediaId] unambiguous. The shared, cross-account
     * `offline_playback_progress` table has no such column —
     * [net.subsloth.database.entity.OfflinePlaybackProgressEntity.contentId]
     * alone cannot disambiguate a movie from an episode when the two id
     * spaces collide (which they do: [MovieId]/[EpisodeId] are independent
     * counters, and [net.subsloth.database.entity.DownloadedMediaEntity]'s
     * own unique index is on `(contentId, mediaType)` precisely because a
     * bare `contentId` isn't unique across content types). Building a
     * mapping there would mean guessing a media type, so
     * [net.subsloth.library.DownloadsViewModel]'s `listProgress` is
     * intentionally left on its safe empty-list default instead (see the
     * wiring in [SubSlothNavHost]).
     */
    suspend fun listAccountPlaybackProgress(): Result<List<PlaybackProgress>> = runCatching {
        when (val session = sessionPort.current()) {
            Session.Anonymous -> emptyList()
            is Session.Authenticated -> database.accountPlaybackProgressDao()
                .getAllForProfile(session.userId)
                .first()
                .map { it.toPlaybackProgress() }
        }
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Looks up the active session's stored progress for [mediaId] so the
     * player can resume. Returns `null` when there is none or the lookup
     * fails — resume is best-effort and playback then starts from zero.
     */
    suspend fun loadPlaybackProgress(mediaId: Media.MediaId): PlaybackProgress? =
        listAccountPlaybackProgress().getOrDefault(emptyList()).firstOrNull { it.mediaId == mediaId }

    private fun AccountPlaybackProgressEntity.toPlaybackProgress(): PlaybackProgress {
        val fraction = if (durationSeconds > 0) {
            positionSeconds.toDouble() / durationSeconds.toDouble()
        } else {
            0.0
        }
        return PlaybackProgress(
            mediaId = parsePlaybackMediaId(contentId, contentType),
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(updatedAtEpochSeconds),
            isWatched = fraction > CompletionPolicy.WATCHED_THRESHOLD,
        )
    }

    private fun parsePlaybackMediaId(contentId: String, contentType: String): Media.MediaId = when (contentType) {
        "movie" -> Media.MediaId.Movie(MovieId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")))
        "show" -> Media.MediaId.Show(ShowId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")))
        "episode" -> Media.MediaId.Episode(
            EpisodeId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")),
        )
        else -> error("Unknown contentType: $contentType")
    }

    /**
     * Creates, confirms, and starts driving a download queue for one season
     * of [showId]. Transfers themselves run through
     * [downloadTransferCoordinator] for the process lifetime; this only
     * seeds the queue with the season's episodes.
     */
    suspend fun startSeasonDownload(showId: ShowId, seasonNumber: Int, episodes: ImmutableList<Episode>) {
        if (episodes.isEmpty()) return
        val language = loadPreferredLanguage()
        val quality = episodes
            .firstNotNullOfOrNull { QualityPolicy.selectDefault(it.qualities, isTvDevice = false) }
            ?.info
            ?.resolution
            ?: Resolution.HD_720
        val completedIds = downloadController.listDownloads()
            .getOrElse { emptyList() }
            .filterIsInstance<DownloadState.Completed>()
            .map { it.mediaId }
            .toSet()
        val confirmation = DownloadPolicy.prepareSeasonPreflight(
            episodes = episodes,
            qualityPref = quality,
            subtitlePref = language,
            transferPreference = TransferPreference.WifiOnly,
            alreadyDownloaded = completedIds,
        )
        val queueId = QueueId("${showId.value}-$seasonNumber")
        seasonQueueController.createQueue(
            queueId = queueId,
            showId = showId,
            seasonNumber = seasonNumber,
            episodes = episodes,
            qualityPref = quality,
            subtitlePref = language,
            transferPreference = TransferPreference.WifiOnly,
            confirmation = confirmation,
        )
        seasonQueueController.confirmQueue(queueId)
        containerScope.launch { seasonQueueDriver.drive(queueId) }
    }

    /**
     * Adapts [SeasonQueueController.listQueues] (a plain suspend function
     * returning a plain [List]) to [net.subsloth.library.DownloadsViewModel]'s
     * `Result`/[ImmutableList]-wrapped shape.
     */
    suspend fun listSeasonQueues(): Result<ImmutableList<SeasonDownloadQueue>> = runCatching {
        seasonQueueController.listQueues().toImmutableList()
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Retries a previously-failed (or otherwise inactive) download by
     * re-[DownloadController.enqueue]ing it with its last-known media id and
     * quality. [net.subsloth.core.domain.port.DownloadsPort] has no
     * dedicated "retry" operation — enqueueing again is the existing
     * recovery path. If the download can no longer be found (e.g.
     * concurrently removed) or the retry enqueue itself fails, this falls
     * back to [EnqueueOutcome.Queued] and logs the failure:
     * [EnqueueOutcome] has no "failed" case to report through, matching
     * [net.subsloth.library.DownloadsViewModel]'s existing
     * log-and-fall-back-to-default pattern for every other port call.
     */
    suspend fun retryDownload(localId: String): EnqueueOutcome {
        val target = LocalMediaIdentifier(localId)
        val existing = downloadController.listDownloads()
            .onFailure { log.e(it) { "listDownloads failed while retrying $localId" } }
            .getOrNull()
            ?.firstOrNull { it.localId == target }
        if (existing == null) {
            log.e(null) { "retryDownload: no download found for localId=$localId" }
            return EnqueueOutcome.Queued
        }
        return downloadController.enqueue(
            mediaId = existing.mediaId,
            requested = existing.quality.resolution,
        ).onFailure { log.e(it) { "retry enqueue failed for localId=$localId" } }
            .getOrDefault(EnqueueOutcome.Queued)
    }

    // ── Account profile key ─────────────────────────────────────────────

    /**
     * Derives the active [AccountProfileKey] from the current session,
     * matching [LibraryPortAdapter]'s established session-derived
     * profile-key pattern (see its private `profileKey()`, which returns
     * the plain [Session.Authenticated.userId] string): the session's
     * `userId` when logged in, the same `"default"` fallback
     * [net.subsloth.settings.SettingsViewModel]'s constructor and
     * [LibraryPortAdapter] both already use for anonymous sessions.
     */
    fun currentProfileKey(): AccountProfileKey = when (val session = sessionPort.current()) {
        is Session.Authenticated -> AccountProfileKey(session.userId)
        Session.Anonymous -> AccountProfileKey(DEFAULT_PROFILE_KEY)
    }

    // ── Settings wiring (net.subsloth.settings.SettingsViewModel) ───────

    fun writeSubtitleEnabled(enabled: Boolean) {
        containerScope.launch { userPreferences.setSubtitleEnabled(currentProfileKey(), enabled) }
    }

    fun writeSubtitleLanguage(language: String?) {
        containerScope.launch { userPreferences.setSubtitleLanguage(currentProfileKey(), language) }
    }

    fun writeQuality(quality: String?) {
        containerScope.launch { userPreferences.setQuality(currentProfileKey(), quality) }
    }

    fun writePlaybackSpeed(speed: Float) {
        containerScope.launch { userPreferences.setPlaybackSpeed(currentProfileKey(), speed) }
    }

    fun writeDownloadsWifiOnly(wifiOnly: Boolean) {
        containerScope.launch { userPreferences.setDownloadsWifiOnly(currentProfileKey(), wifiOnly) }
    }

    /**
     * Deletes every locally-downloaded media item. [downloadController]'s
     * backing DAOs carry no profile key (downloads are shared across
     * accounts and logged-out state — see [downloadController]'s doc), so
     * this is necessarily a full wipe regardless of which account is
     * active; that matches the "delete downloads" logout-cleanup
     * checkbox's intended scope (there is no per-account download data to
     * narrow it to).
     */
    fun deleteAllDownloads() {
        containerScope.launch {
            downloadController.listDownloads()
                .onFailure { log.e(it) { "listDownloads failed while deleting all downloads" } }
                .getOrNull()
                ?.forEach { state ->
                    downloadController.remove(state.localId)
                        .onFailure { log.e(it) { "remove failed for ${state.localId} while deleting all downloads" } }
                }
        }
    }

    /** Clears every persisted preference for [profileKey]. */
    fun clearPreferences(profileKey: AccountProfileKey) {
        containerScope.launch { userPreferences.clearProfilePreferences(profileKey) }
    }

    /**
     * Clears every active-profile library scope named by the
     * `auth-security` spec's "Logout Cleanup Scopes": favorites, watch
     * later, watched state, subscriptions/server mirrors, local-only
     * library records, and account-scoped playback progress.
     *
     * Deliberately does NOT touch [cachedCatalogDao]: cached
     * catalog/detail metadata has no profile-key column at all — it is a
     * single shared cache (see [net.subsloth.database.dao.CachedCatalogDao]) —
     * so its only clear operation
     * ([net.subsloth.database.dao.CachedCatalogDao.deleteAll]) would wipe
     * the cache for every account, not just the active one, which is
     * out of scope for an "only active-profile" cleanup action. See this
     * task's report for the full reasoning.
     */
    fun clearLibrary() {
        // Captured synchronously, before scheduling the cleanup coroutine —
        // not read from inside `launch { }` — so this can't race
        // `clearCredentials()`'s independent `containerScope.launch`, which
        // may flip `sessionPort` to `Anonymous` (and thus this profile key's
        // derivation to the anonymous default) before an async read here
        // would otherwise land. Mirrors `clearPreferences(profileKey: ...)`,
        // which gets the same guarantee by taking its key as a parameter.
        val key = currentProfileKey().value
        containerScope.launch {
            database.favoriteDao().deleteAllForProfile(key)
            database.watchLaterDao().deleteAllForProfile(key)
            database.watchedStateDao().deleteAllForProfile(key)
            database.subscriptionDao().deleteAllForProfile(key)
            database.localLibraryRecordDao().deleteAllForProfile(key)
            database.accountPlaybackProgressDao().deleteAllForProfile(key)
        }
    }

    /**
     * Clears persisted credentials and ends the current session. Grouped
     * alongside [deleteAllDownloads]/[clearPreferences]/[clearLibrary] as
     * one of the independently-selectable logout-cleanup actions in
     * `SettingsViewModel.performLogoutCleanup` — but unlike those three,
     * this also flips [sessionPort]'s state to `Anonymous`, which drops
     * `SessionGate` out of the authenticated content entirely (including
     * this very Settings screen). See this task's report for why this
     * interpretation was chosen over a narrower one.
     */
    fun clearCredentials() {
        containerScope.launch { sessionPort.close() }
    }

    // ── Player wiring (net.subsloth.player.PlayerViewModel) ─────────────

    /**
     * Flattens a [ShowDetails]'s seasons into a single episode list for
     * [net.subsloth.player.PlayerViewModel]'s "next episode" lookup. Reads
     * [catalogRepository] live (not captured) for the same
     * anti-stale-capture reason documented on [listMovies].
     */
    suspend fun fetchEpisodesForShow(showId: Media.MediaId.Show): Outcome<List<Episode>> =
        when (val result = catalogRepository.getDetails(showId)) {
            is Outcome.Success -> {
                val details = result.value as? ShowDetails
                if (details != null) {
                    Outcome.Success(details.seasons.flatMap { it.episodes })
                } else {
                    Outcome.Failure(MediaError.NotFound)
                }
            }

            is Outcome.Failure -> Outcome.Failure(result.error)
        }

    /**
     * Persists online playback progress for the active profile.
     * [net.subsloth.database.dao.AccountPlaybackProgressDao.upsert] already
     * replaces on conflict, keyed by `(profileKey, contentId)`.
     */
    /**
     * Persists playback progress for the active profile. Online progress
     * goes to the account-scoped table (and marks the item watched once
     * the completion threshold is crossed); offline playback goes to the
     * shared offline table — [PlayerViewModel] passes its mode through.
     */
    suspend fun savePlaybackProgress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        playbackMode: PlaybackMode = PlaybackMode.ONLINE,
    ) {
        when (playbackMode) {
            PlaybackMode.OFFLINE -> database.offlinePlaybackProgressDao().upsert(
                OfflinePlaybackProgressEntity(
                    contentId = mediaId.toContentId(),
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                    updatedAtEpochSeconds = clock.now().epochSeconds,
                ),
            )

            PlaybackMode.ONLINE -> {
                database.accountPlaybackProgressDao().upsert(
                    AccountPlaybackProgressEntity(
                        profileKey = currentProfileKey().value,
                        contentId = mediaId.toContentId(),
                        contentType = mediaId.toContentType(),
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                        updatedAtEpochSeconds = clock.now().epochSeconds,
                    ),
                )
                if (durationSeconds > 0 &&
                    positionSeconds.toDouble() / durationSeconds > CompletionPolicy.WATCHED_THRESHOLD
                ) {
                    markWatched(mediaId)
                }
            }
        }
    }

    /** Marks [mediaId] watched for the active profile (watched_state table). */
    suspend fun markWatched(mediaId: Media.MediaId) {
        database.watchedStateDao().upsert(
            WatchedStateEntity(
                profileKey = currentProfileKey().value,
                contentId = mediaId.toContentId(),
                contentType = mediaId.toContentType(),
                isWatched = true,
                watchedAtEpochSeconds = clock.now().epochSeconds,
            ),
        )
    }

    /** Content ids marked watched for the active profile (series rows, detail badges). */
    suspend fun listWatchedContentIds(): Set<String> = database.watchedStateDao()
        .getAllForProfile(currentProfileKey().value)
        .first()
        .filter { it.isWatched }
        .map { it.contentId }
        .toSet()

    /** Watched state for a single media item, for the active profile. */
    suspend fun isWatched(mediaId: Media.MediaId): Boolean = database.watchedStateDao()
        .getByProfileAndContentId(currentProfileKey().value, mediaId.toContentId())
        ?.isWatched == true

    suspend fun savePlaybackSpeed(speed: Float) {
        userPreferences.setPlaybackSpeed(currentProfileKey(), speed)
    }

    suspend fun loadPlaybackSpeed(): Float = userPreferences.playbackSpeed(currentProfileKey()).first()

    suspend fun loadPreferredLanguage(): LanguageCode =
        LanguageCode(userPreferences.subtitleLanguage(currentProfileKey()).first() ?: DEFAULT_LANGUAGE)

    /**
     * Resolves an episode's parent show id via a single `/episodes/{id}`
     * lookup ([Api.getEpisode]) so
     * [net.subsloth.player.PlayerViewModel] can populate its "next
     * episode" prompt when playback started directly on an episode
     * (rather than navigating in from a show's season list). Returns null
     * on any failure (not found, network error) — this is a minor
     * nice-to-have (next-episode navigation), not core functionality, so
     * it degrades silently like every other best-effort port call in this
     * container.
     */
    @Suppress("TooGenericExceptionCaught") // Network-boundary catch-all, same pattern as AndroidSessionState.validate.
    suspend fun resolveShowIdForEpisode(episodeId: EpisodeId): ShowId? = try {
        api.getEpisode(episodeId.value).showId?.let { ShowId(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "resolveShowIdForEpisode failed for $episodeId" }
        null
    }

    /**
     * Ends the current session in response to a playback-detected auth
     * failure (401). Complementary to, not redundant with, `PlayerScreen`'s
     * existing `onNavigateToAuthRepair` callback: this invalidates
     * [sessionPort] as soon as `PlayerViewModel` classifies a playback
     * error as [net.subsloth.core.model.playback.PlaybackError.AuthFailure],
     * while `onNavigateToAuthRepair` only fires when the user taps "Sign
     * in again" on the resulting error screen — by then the session
     * should already be invalidated, so `SessionGate`/`AuthRepairScreen`
     * start from a clean, already-logged-out state rather than fighting a
     * stale `Authenticated` session.
     */
    fun invalidateSession() {
        containerScope.launch { sessionPort.invalidate() }
    }

    private fun Media.MediaId.toContentId(): String = when (this) {
        is Media.MediaId.Movie -> value.value.toString()
        is Media.MediaId.Show -> value.value.toString()
        is Media.MediaId.Episode -> value.value.toString()
    }

    private fun Media.MediaId.toContentType(): String = when (this) {
        is Media.MediaId.Movie -> "movie"
        is Media.MediaId.Show -> "show"
        is Media.MediaId.Episode -> "episode"
    }

    private companion object {
        private const val DEFAULT_PROFILE_KEY = "default"
        private const val DEFAULT_LANGUAGE = "en"

        /** Subtitle documents are small text files; refuse larger ones. */
        private const val SUBTITLE_MAX_BYTES = 2_000_000

        private const val SUBTITLE_TIMEOUT_MS = 10_000
    }
}

/**
 * [ViewModelProvider.Factory] for [HomeViewModel] that receives its
 * [CatalogRepository] dependency as a supplier rather than a pre-resolved
 * value: [AppContainer.catalogRepository] is rebuilt asynchronously
 * whenever the session's credentials change (login, logout, account
 * switch), so resolving it eagerly at factory-construction time risks
 * permanently capturing a stale (anonymous or previous-account) instance
 * if construction happens before an in-flight rebuild completes.
 * Reading [catalogRepositoryProvider] here in [create] instead ensures
 * each [HomeViewModel] construction sees whichever [CatalogRepository]
 * is current at that moment.
 */
internal class HomeViewModelFactory(
    private val catalogRepositoryProvider: () -> CatalogRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val catalogRepository = catalogRepositoryProvider()
        return requireNotNull(
            modelClass.cast(
                HomeViewModel(
                    catalogItems = { contentType -> catalogRepository.catalogItems(contentType) },
                    syncCatalog = { catalogRepository.sync() },
                    isCatalogStale = { catalogRepository.isStale() },
                ),
            ),
        )
    }
}
