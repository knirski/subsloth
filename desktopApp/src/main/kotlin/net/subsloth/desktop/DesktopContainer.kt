package net.subsloth.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.data.runtime.AccountMediaRuntime
import net.subsloth.core.data.session.ValidatingSessionState
import net.subsloth.core.domain.policy.ApiBaseUrlPolicy
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.domain.policy.QualityPolicy
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.domain.port.StoragePort
import net.subsloth.core.media.download.DesktopConnectivityChecker
import net.subsloth.core.media.download.DesktopDownloadStore
import net.subsloth.core.media.download.DesktopStorageProvider
import net.subsloth.core.media.download.DownloadController
import net.subsloth.core.media.download.DownloadTarget
import net.subsloth.core.media.download.DownloadTransferCoordinator
import net.subsloth.core.media.download.DownloadTransferer
import net.subsloth.core.media.download.SeasonQueueController
import net.subsloth.core.media.download.SeasonQueueDriver
import net.subsloth.core.media.download.TransferEvent
import net.subsloth.core.media.download.parseResolution
import net.subsloth.core.media.playback.OfflineFirstPlaybackPort
import net.subsloth.core.media.playback.OfflineSourceResolver
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.NetworkError
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
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.error.NetworkErrorClassifier
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
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity
import net.subsloth.database.entity.CachedCatalogItemWithMetadata
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.WatchedStateEntity
import net.subsloth.preferences.AccountProfileStore
import net.subsloth.preferences.CredentialStore
import net.subsloth.preferences.CredentialsStoreAdapter
import net.subsloth.preferences.UserPreferences
import net.subsloth.preferences.createDataStorePreferences
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import kotlin.time.Clock
import kotlin.time.Instant

/** Fallback subtitle language when the user has no preference saved. */
private const val DEFAULT_LANGUAGE = "en"

/** Fallback profile key for anonymous sessions, mirroring [SettingsViewModel]'s default. */
private const val DEFAULT_PROFILE_KEY = "default"

/**
 * Desktop composition root — the JVM counterpart of androidApp's
 * `AppContainer`. Constructs the real, persistent adapters (DataStore
 * preferences, Keystore-backed credential store, Room database,
 * API-validated session) and rebuilds the session-scoped
 * [Api]/[CatalogRepository]/[PlaybackPort] trio whenever the session
 * changes, exactly like Android does.
 *
 * Base URL: desktop has no build-config field; a deliberately saved
 * [UserPreferences.storedApiBaseUrl] value takes precedence, with the
 * `SUBSLOTH_API_BASE_URL` environment variable as the fallback for an
 * absent or blank preference (see [resolveApiBaseUrl] and
 * [ApiBaseUrlPolicy]).
 *
 * Downloads are wired with the JVM storage shell (PR follow-up to #235):
 * [DownloadController] runs with `DesktopDownloadStore`,
 * `DesktopStorageProvider`, and `DesktopConnectivityChecker` from
 * `:core:media`'s `jvmMain`, backed by Room in [dataDir]. See the class
 * docs of the desktop collaborators for their platform semantics
 * (notably the flat unmetered network model).
 *
 * The platform-neutral helper subset of AppContainer (catalog list/detail
 * lambdas, settings writers, playback-progress persistence) is mirrored
 * here rather than extracted into a shared runtime; consolidating the two
 * containers is a follow-up refactor, deliberately not bundled into this
 * wiring change.
 */
@Suppress("TooManyFunctions") // Composition root: one member per wired port, mirroring AppContainer.
class DesktopContainer(dataDirOverride: File? = null) {

    private val log = Logger.withTag("DesktopContainer")

    /** System clock implementation. */
    val clock: Clock = Clock.System

    /**
     * Process-lifetime coroutine scope. Never cancelled — the container
     * lives as long as the desktop process, mirroring `AppContainer`'s
     * documented pattern.
     */
    private val containerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Read-only view of [containerScope] for work that must outlive a
     * ViewModel — e.g. the player's final progress flush, which runs after
     * AndroidX has already cancelled `viewModelScope`.
     */
    val externalScope: CoroutineScope get() = containerScope

    /**
     * App data directory (`~/.local/share/subsloth` on Linux/macOS, `%APPDATA%\subsloth` on
     * Windows). Created eagerly: SQLite does not create missing parent directories, so the
     * first database operation would fail on a fresh install otherwise.
     */
    private val dataDir: File = (dataDirOverride ?: resolveAppDataDir()).apply { mkdirs() }

    private val dataStore: DataStore<Preferences> by lazy {
        createDataStorePreferences(name = "subsloth", appDataDir = dataDir, scope = containerScope)
    }

    /** User preferences backed by DataStore. */
    val userPreferences: UserPreferences by lazy { UserPreferences(dataStore) }

    /**
     * Derives non-reversible [AccountProfileKey]s from logins via
     * HMAC-SHA256 and an app-local, per-install salt. Shares [dataStore]
     * rather than opening a second `DataStore` against the same backing
     * file (see androidApp's `AppContainer` doc for why that throws).
     */
    private val accountProfileStore: AccountProfileStore by lazy { AccountProfileStore(dataStore) }

    /** Room database for cached catalog, library, and playback state. */
    val database: SubSlothDatabase by lazy { createSubSlothDatabase(File(dataDir, "subsloth.db").path) }

    private val cachedCatalogDao: CachedCatalogDao by lazy { database.cachedCatalogDao() }

    /**
     * Delegates every [CachedCatalogDao] call through to [cachedCatalogDao],
     * resolved lazily at call time — mirrors `AppContainer`'s
     * `deferredCachedCatalogDao` so building [CatalogRepository]'s eager
     * initial value never forces Room initialization onto the caller's
     * thread; Room only opens once a DAO method actually runs.
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

        override suspend fun replaceAll(items: List<CachedCatalogItemWithMetadata>) = cachedCatalogDao.replaceAll(items)
    }

    /**
     * Production [SessionPort]: persists credentials via the desktop
     * credential store (`:core:preferences` JVM actual — AES-GCM-encrypted
     * file under `~/.subsloth`, keyed to the machine id) and validates
     * them against the API exactly like Android's session state.
     * [recover] is invoked exactly once, from [init]'s launch.
     */
    private val sessionState = ValidatingSessionState(
        credentialsPort = CredentialsStoreAdapter(CredentialStore()),
        baseUrlProvider = { resolveApiBaseUrl() },
        accountProfileStore = accountProfileStore,
        clock = clock,
    )

    /** Production [SessionPort] exposed to the UI and feature ViewModels. */
    val sessionPort: SessionPort = sessionState

    /**
     * Media API client reflecting the current session: authenticated
     * (BasicAuth) when [Session.Authenticated], anonymous otherwise.
     * Replaced — together with [currentCatalogRepository] and
     * [currentPlaybackPort] — on every session state change (see `init`).
     */
    @Volatile
    private var currentApi: Api = Api(ClientFactory.create())

    @Volatile
    private var currentCatalogRepository: CatalogRepository = buildCatalogRepository(currentApi)

    private val downloadStore: DesktopDownloadStore by lazy { DesktopDownloadStore(File(dataDir, "downloads")) }
    private val storageProvider: StoragePort by lazy { DesktopStorageProvider(File(dataDir, "downloads")) }
    private val connectivityChecker: ConnectivityPort by lazy { DesktopConnectivityChecker() }

    /**
     * Production [net.subsloth.core.domain.port.DownloadsPort] implementation
     * (the desktop counterpart of `AppContainer`'s `downloadController`).
     * Downloaded media is shared across accounts and logged-out state (its
     * backing DAOs carry no profile key), so unlike [catalogRepository] this
     * never needs to be rebuilt when the session changes.
     */
    val downloadController: DownloadController by lazy {
        DownloadController(
            storageManager = downloadStore,
            storageProvider = storageProvider,
            connectivityChecker = connectivityChecker,
            downloadedMediaDao = database.downloadedMediaDao(),
            downloadedSubtitleDao = database.downloadedSubtitleDao(),
            offlineDisplayMetadataDao = database.offlineDisplayMetadataDao(),
        )
    }

    /**
     * Offline playback source resolution over [downloadController]'s stored
     * assets, verified through [downloadStore]. Session-independent (like
     * [downloadController]); consumed by [currentPlaybackPort]'s
     * [OfflineFirstPlaybackPort] wrapper.
     *
     * Declared after its [lazy] dependencies on purpose: [currentPlaybackPort]
     * below is an eager initializer that forces this lazy during construction,
     * and a `lazy` delegate reading a property declared later in the class
     * would hit a null delegate (Kotlin assigns delegate fields in
     * declaration order — this ordering bug crashed the desktop app at
     * startup).
     */
    private val offlineSourceResolver: OfflineSourceResolver by lazy {
        OfflineSourceResolver(
            offlineAssets = { downloadController.listOfflineAssets() },
            files = downloadStore,
        )
    }

    @Volatile
    private var currentPlaybackPort: PlaybackPort = OfflineFirstPlaybackPort(
        offlineSourceResolver = offlineSourceResolver,
        online = ApiPlaybackPort(currentApi),
    )

    val api: Api get() = currentApi

    val catalogRepository: CatalogRepository get() = currentCatalogRepository

    /**
     * Shared account-scoped runtime: catalog projections, playback
     * progress, watched state, and subtitle-track resolution. Platform
     * specifics (DataStore, credential storage, byte transfer) stay in this
     * container.
     */
    private val accountMediaRuntime: AccountMediaRuntime by lazy {
        AccountMediaRuntime(
            sessionPort = sessionPort,
            catalogRepository = { catalogRepository },
            api = { api },
            accountPlaybackProgressDao = { database.accountPlaybackProgressDao() },
            offlinePlaybackProgressDao = { database.offlinePlaybackProgressDao() },
            watchedStateDao = { database.watchedStateDao() },
            clock = clock,
            anonymousProfileKey = DEFAULT_PROFILE_KEY,
        )
    }

    val playbackPort: PlaybackPort get() = currentPlaybackPort

    /** Production [net.subsloth.core.domain.port.LibraryPort]: favorites,
     * watch later, and custom-list membership, persisted to Room and scoped
     * by the active session's profile key (read fresh per call). Stays
     * correct across login/logout without rebuilding.
     */
    val libraryPortAdapter by lazy {
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
     * client: download URLs are server-signed (`wmsAuthSign`), so no auth
     * headers are needed, and a session-scoped client would go stale when
     * the session rebuilds (the resolver below reads [api] live instead).
     */
    private val downloadTransferer: DownloadTransferer by lazy {
        DownloadTransferer(
            client = ClientFactory.create(),
            store = downloadStore,
        )
    }

    /**
     * Drives real byte transfers for queued downloads (the desktop
     * counterpart of `AppContainer`'s coordinator). The download-URL
     * resolver reads the session-scoped [api] live on every call, so
     * transfers always use current credentials despite the coordinator
     * itself being session-independent.
     */
    val downloadTransferCoordinator: DownloadTransferCoordinator by lazy {
        DownloadTransferCoordinator(
            downloadedMediaDao = database.downloadedMediaDao(),
            downloadedSubtitleDao = database.downloadedSubtitleDao(),
            store = downloadStore,
            transferer = downloadTransferer,
            connectivityChecker = connectivityChecker,
            clock = clock,
            resolveDownloadUrl = ::resolveDownloadTarget,
            resolveSubtitles = ::resolveSubtitleTracks,
        )
    }

    init {
        // Cold-start session recovery — invoked exactly once.
        containerScope.launch { sessionState.recover() }

        // Drive real download byte transfers for the process lifetime.
        // Desktop has no foreground service — transfer events are logged.
        containerScope.launch { downloadTransferCoordinator.runWatcher() }
        containerScope.launch {
            downloadTransferCoordinator.events.collect { event ->
                when (event) {
                    is TransferEvent.Progress -> log.d {
                        "Download ${event.localId.value}: ${event.bytesWritten} bytes" +
                            (event.totalBytes?.let { " / $it" } ?: "")
                    }

                    is TransferEvent.Completed -> log.d {
                        "Download ${event.localId.value} completed (${event.sizeBytes} bytes)"
                    }

                    is TransferEvent.Failed -> log.e(null) {
                        "Download ${event.localId.value} failed: ${event.reason}"
                    }
                }
            }
        }

        // Rebuild the session-scoped adapter trio whenever the session's
        // credentials actually change (login/logout/account switch).
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
                previousApi.close()
            }
        }
    }

    /**
     * Resolves a progressive (single-file) download URL for [mediaId] —
     * mirrors `AppContainer`'s `resolveDownloadTarget`: prefers the item's
     * top-level `download_url`, falls back to the quality variant matching
     * the requested label; HLS playlists (`.m3u8`) are not single files and
     * are rejected. Returns null on any failure (the coordinator marks the
     * download FAILED).
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
    private suspend fun resolveSubtitleTracks(mediaId: Media.MediaId): List<Subtitle> =
        accountMediaRuntime.resolveSubtitleTracks(mediaId)

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
     * Resolves the API base URL with the same presence-aware precedence as
     * Android's `AppContainer.resolveApiBaseUrl` ([ApiBaseUrlPolicy]): a
     * deliberately saved non-blank [UserPreferences.storedApiBaseUrl] value
     * — including the default — wins over the non-blank
     * `SUBSLOTH_API_BASE_URL` environment variable (desktop's counterpart
     * of Android's build-config field; CI provides it from the repo
     * secret). An absent or blank preference falls back to the environment
     * variable, then to the default. Public because the login flows
     * (Main.kt's `LoginViewModel` and DesktopNavHost's auth-repair entry)
     * display it.
     */
    suspend fun resolveApiBaseUrl(): String = apiBaseUrlFlow().first()

    /**
     * Flow form of [resolveApiBaseUrl] for the login screens, which
     * expect a flow shape.
     */
    fun apiBaseUrlFlow(): Flow<String> = userPreferences.storedApiBaseUrl().map { stored ->
        ApiBaseUrlPolicy.resolve(stored = stored, configured = System.getenv("SUBSLOTH_API_BASE_URL"))
    }

    private suspend fun buildApi(session: Session): Api {
        val baseUrl = resolveApiBaseUrl()
        return when (session) {
            is Session.Authenticated -> Api(
                ClientFactory.create(
                    login = session.credentials.login,
                    password = session.credentials.password,
                    baseUrl = baseUrl,
                ),
            )

            Session.Anonymous -> Api(ClientFactory.create(baseUrl = baseUrl))
        }
    }

    private fun buildCatalogRepository(api: Api): CatalogRepository = CatalogRepository(
        api = api,
        catalogDao = deferredCachedCatalogDao,
        userPreferences = userPreferences,
        clock = clock,
    )

    // ── Catalog wiring (net.subsloth.catalog.HomeViewModel) ─────────────

    /**
     * Reads [catalogRepository] live (not captured) on every call so
     * catalog lambdas never go stale across session rebuilds — the same
     * anti-stale-capture discipline `AppContainer` documents.
     */
    suspend fun listMovies(): Result<List<MovieSummary>> = accountMediaRuntime.listMovies()

    /** Show-list counterpart of [listMovies]. */
    suspend fun listShows(): Result<List<ShowSummary>> = accountMediaRuntime.listShows()

    /**
     * Merged movie+show catalog for the search screen (`SearchViewModel`'s
     * `listCatalog`) — mirrors `AppContainer`'s `listAllMedia`: reads the
     * same cached-catalog lists wrapped in the `Outcome` shape the search
     * ViewModel consumes; a failed side degrades to a failure.
     */
    suspend fun listAllMedia(): Outcome<List<Media>> = accountMediaRuntime.listAllMedia()

    // ── Player wiring (net.subsloth.player.PlayerViewModel) ─────────────

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
     * [PlaybackProgress] domain shape consumed by `LibraryViewModel`'s
     * "Continue Watching" row — same mapping as `AppContainer`'s
     * `listAccountPlaybackProgress`.
     */
    suspend fun listAccountPlaybackProgress(): Result<List<PlaybackProgress>> =
        accountMediaRuntime.listAccountPlaybackProgress()

    /**
     * Looks up the active session's stored progress for [mediaId] so the
     * player can resume. Returns `null` when there is none or the lookup
     * fails — resume is best-effort and playback then starts from zero.
     */
    suspend fun loadPlaybackProgress(mediaId: Media.MediaId): PlaybackProgress? =
        accountMediaRuntime.loadPlaybackProgress(mediaId)

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
     * returning a plain [List]) to `DownloadsViewModel`'s
     * `Result`/[ImmutableList]-wrapped shape. Mirrors `AppContainer`'s
     * `listSeasonQueues`.
     */
    suspend fun listSeasonQueues(): Result<ImmutableList<SeasonDownloadQueue>> = runCatching {
        seasonQueueController.listQueues().toImmutableList()
    }.onFailure { if (it is CancellationException) throw it }

    /**
     * Retries a previously-failed (or otherwise inactive) download by
     * re-[DownloadController.enqueue]ing it with its last-known media id
     * and quality. Mirrors `AppContainer`'s `retryDownload`; falls back to
     * [EnqueueOutcome.Queued] on any failure, matching the downloads
     * screen's log-and-fall-back-to-default pattern.
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

    /** Flattens [ShowDetails]'s seasons into a single episode list for the "next episode" lookup. */
    suspend fun fetchEpisodesForShow(showId: Media.MediaId.Show): Outcome<List<Episode>> =
        accountMediaRuntime.fetchEpisodesForShow(showId)
    suspend fun savePlaybackProgress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        playbackMode: PlaybackMode = PlaybackMode.ONLINE,
    ) = accountMediaRuntime.savePlaybackProgress(mediaId, positionSeconds, durationSeconds, playbackMode)

    /** Marks [mediaId] watched for the active profile (watched_state table). */
    suspend fun markWatched(mediaId: Media.MediaId) = accountMediaRuntime.markWatched(mediaId)

    /** Content ids marked watched for the active profile (series rows, detail badges). */
    suspend fun listWatchedContentIds(): Set<String> = accountMediaRuntime.listWatchedContentIds()
    suspend fun isWatched(mediaId: Media.MediaId): Boolean = accountMediaRuntime.isWatched(mediaId)
    suspend fun savePlaybackSpeed(speed: Float) {
        userPreferences.setPlaybackSpeed(currentProfileKey(), speed)
    }

    suspend fun loadPlaybackSpeed(): Float = userPreferences.playbackSpeed(currentProfileKey()).first()

    suspend fun loadPreferredLanguage(): LanguageCode =
        LanguageCode(userPreferences.subtitleLanguage(currentProfileKey()).first() ?: DEFAULT_LANGUAGE)

    /**
     * Resolves an episode's parent show id via a single `/episodes/{id}`
     * lookup; returns null on any failure (best-effort "next episode"
     * nicety, degrading silently like every other best-effort port call).
     */
    @Suppress("TooGenericExceptionCaught") // Network-boundary catch-all, same pattern as session-state validation.
    suspend fun resolveShowIdForEpisode(episodeId: EpisodeId): ShowId? = try {
        api.getEpisode(episodeId.value).showId?.let { ShowId(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "resolveShowIdForEpisode failed for $episodeId" }
        null
    }

    /** Ends the current session in response to a playback-detected auth failure (401). */
    fun invalidateSession() {
        containerScope.launch { sessionPort.invalidate() }
    }

    // ── Account profile key ─────────────────────────────────────────────

    /**
     * Derives the active [AccountProfileKey] from the current session:
     * the authenticated user's profile key, or the `"default"` fallback
     * for anonymous sessions (same pattern as `AppContainer` and
     * `SettingsViewModel`).
     */
    fun currentProfileKey(): AccountProfileKey = accountMediaRuntime.currentProfileKey()

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

    /** Clears every persisted preference for [profileKey]. */
    fun clearPreferences(profileKey: AccountProfileKey) {
        containerScope.launch { userPreferences.clearProfilePreferences(profileKey) }
    }

    /**
     * Clears the active profile's library scopes (favorites, watch later,
     * watched state, subscriptions, local-only records, account-scoped
     * playback progress) — same scope list as `AppContainer.clearLibrary`.
     * The shared cached-catalog tables are deliberately untouched.
     */
    fun clearLibrary() {
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

    /** Clears persisted credentials and ends the current session. */
    fun clearCredentials() {
        containerScope.launch { sessionPort.close() }
    }

    /**
     * Deletes every locally-downloaded media item. Mirrors `AppContainer`'s
     * `deleteAllDownloads`: [downloadController]'s backing DAOs carry no
     * profile key (downloads are shared across accounts and logged-out
     * state — see [downloadController]'s doc), so this is necessarily a
     * full wipe regardless of which account is active.
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

    private fun resolveAppDataDir(): File {
        val osName = System.getProperty("os.name").lowercase()
        val userHome = System.getProperty("user.home")
        return when {
            osName.contains("linux") || osName.contains("mac") ->
                File("$userHome/.local/share/subsloth")

            osName.contains("windows") ->
                File(System.getenv("APPDATA") ?: "$userHome\\AppData\\Roaming", "subsloth")

            else -> File("$userHome/.subsloth")
        }
    }
}

/** Subtitle documents are small text files; refuse larger ones. */
private const val SUBTITLE_MAX_BYTES = 2_000_000

private const val SUBTITLE_TIMEOUT_MS = 10_000
