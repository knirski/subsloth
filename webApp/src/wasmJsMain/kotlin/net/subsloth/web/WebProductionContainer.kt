package net.subsloth.web

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.subsloth.catalog.HomeViewModel
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.data.session.ValidatingSessionState
import net.subsloth.core.domain.policy.ApiBaseUrlPolicy
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.domain.port.LibraryPort
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.domain.port.StoragePort
import net.subsloth.core.domain.port.SubtitleEnqueueOutcome
import net.subsloth.core.media.download.DownloadController
import net.subsloth.core.media.download.DownloadFileStore
import net.subsloth.core.media.download.SeasonQueueController
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.getOrElse
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
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
import kotlin.time.Clock
import kotlin.time.Instant

/** Fallback subtitle language when the user has no preference saved. */
private const val DEFAULT_LANGUAGE = "en"

/** Fallback profile key for anonymous sessions, mirroring the other containers. */
private const val DEFAULT_PROFILE_KEY = "default"

/** Base URL used before preferences/session resolve; equals ClientFactory's default. */
private const val DEFAULT_BASE_URL = "http://localhost:8080/api/v2/"

/**
 * Production web composition root — the wasmJs counterpart of
 * `DesktopContainer`. Wires the real, persistent adapters: localStorage
 * DataStore preferences, the browser credential store, the OPFS Room
 * database (sqlite-wasm worker), and a `ValidatingSessionState` session
 * port validated against the API. The session-scoped
 * [Api]/[CatalogRepository]/[ApiPlaybackPort] trio rebuilds on session
 * change exactly like the other platforms.
 *
 * Platform notes:
 * - [ApiPlaybackPort] runs with `preferProgressiveDownload = true`:
 *   browsers cannot play HLS natively, so the progressive `download_url`
 *   mp4 variant is preferred when present.
 * - Download byte transfer is jvm-only (no browser equivalent of the
 *   staged-file worker), so the downloads port lists persisted state
 *   (empty until transfer lands) and the controls manage that state.
 *
 * Never construct directly — `createWebApp()` picks this when the build
 * injected a `SUBSLOTH_API_BASE_URL` (production tier), otherwise the
 * demo runtime is used.
 */
@Suppress("TooManyFunctions") // Composition root: one member per wired port, mirroring DesktopContainer.
class WebProductionContainer : WebRuntime {

    private val log = Logger.withTag("WebProductionContainer")

    val clock: Clock = Clock.System

    /** Process-lifetime scope (page lifetime); never cancelled, same pattern as the other containers. */
    private val containerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val dataStore by lazy {
        createDataStorePreferences(name = "subsloth", scope = containerScope)
    }

    val userPreferences: UserPreferences by lazy { UserPreferences(dataStore) }

    private val accountProfileStore: AccountProfileStore by lazy { AccountProfileStore(dataStore) }

    val database: SubSlothDatabase by lazy { createSubSlothDatabase(name = "subsloth") }

    private val cachedCatalogDao: CachedCatalogDao by lazy { database.cachedCatalogDao() }

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

    private val sessionState = ValidatingSessionState(
        credentialsPort = CredentialsStoreAdapter(CredentialStore()),
        baseUrlProvider = { resolveApiBaseUrl() },
        accountProfileStore = accountProfileStore,
        clock = clock,
    )

    override val sessionPort: SessionPort = sessionState

    private var currentApi: Api = Api(ClientFactory.create(baseUrl = initialBaseUrl()))

    private var currentCatalogRepository: CatalogRepository = buildCatalogRepository(currentApi)

    private var currentPlaybackPort: PlaybackPort = ApiPlaybackPort(currentApi, preferProgressiveDownload = true)

    override val playbackPort: PlaybackPort get() = currentPlaybackPort

    override val libraryPort: LibraryPort by lazy {
        LibraryPortAdapter(
            favoriteDao = database.favoriteDao(),
            localLibraryDao = database.localLibraryRecordDao(),
            sessionPort = sessionPort,
        )
    }

    override val downloadController: DownloadsPort by lazy {
        DownloadController(
            storageManager = NoOpDownloadStore,
            storageProvider = BrowserStorageProvider,
            connectivityChecker = BrowserConnectivityChecker,
            downloadedMediaDao = database.downloadedMediaDao(),
            downloadedSubtitleDao = database.downloadedSubtitleDao(),
            offlineDisplayMetadataDao = database.offlineDisplayMetadataDao(),
        )
    }

    private val seasonQueueController: SeasonQueueController by lazy {
        SeasonQueueController(
            downloadsPort = downloadController,
            seasonQueueDao = database.seasonQueueDao(),
            clock = clock,
        )
    }

    init {
        containerScope.launch { sessionState.recover() }
        containerScope.launch {
            sessionPort.state.collect { session ->
                val previousApi = currentApi
                val newApi = Api(buildClient(session))
                currentApi = newApi
                currentCatalogRepository = buildCatalogRepository(newApi)
                currentPlaybackPort = ApiPlaybackPort(newApi, preferProgressiveDownload = true)
                previousApi.close()
            }
        }
    }

    val api: Api get() = currentApi

    val catalogRepository: CatalogRepository get() = currentCatalogRepository

    private fun initialBaseUrl(): String {
        val fromEnv = subslothApiBaseUrlEnv()
        return if (fromEnv.isNotEmpty()) fromEnv else DEFAULT_BASE_URL
    }

    private suspend fun buildClient(session: Session = sessionPort.current()): io.ktor.client.HttpClient =
        ClientFactory.create(
            login = (session as? Session.Authenticated)?.credentials?.login,
            password = (session as? Session.Authenticated)?.credentials?.password,
            baseUrl = resolveApiBaseUrl(),
        )

    private fun buildCatalogRepository(api: Api): CatalogRepository = CatalogRepository(
        api = api,
        catalogDao = deferredCachedCatalogDao,
        userPreferences = userPreferences,
        clock = clock,
    )

    private suspend fun resolveApiBaseUrl(): String = apiBaseUrlFlow().first()

    override fun apiBaseUrlFlow(): Flow<String> = userPreferences.storedApiBaseUrl().map { stored ->
        ApiBaseUrlPolicy.resolve(stored = stored, configured = subslothApiBaseUrlEnv())
    }

    override suspend fun saveApiBaseUrl(url: String) {
        userPreferences.setApiBaseUrl(url)
    }

    // ── Catalog / search ────────────────────────────────────────────────

    override suspend fun listCatalog(): Outcome<List<Media>> = try {
        val movies = Mapper.mapMovies(api.listMovies().movies).items
        val shows = Mapper.mapShows(api.listShows().shows).items
        Outcome.Success(movies + shows)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "listCatalog failed" }
        Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(e))
    }

    override suspend fun getDetails(mediaId: Media.MediaId): Outcome<MediaDetails> = try {
        when (mediaId) {
            is Media.MediaId.Movie -> Mapper.mapMovieDetails(api.getMovie(mediaId.value.value))
            is Media.MediaId.Show -> Mapper.mapShowDetails(api.getShow(mediaId.value.value))
            is Media.MediaId.Episode -> Mapper.mapEpisodeDetails(api.getEpisode(mediaId.value.value))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "getDetails failed for $mediaId" }
        Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(e))
    }

    override fun catalogItems(type: String): Flow<List<Media>> = flow {
        emit(
            listCatalog().getOrElse { emptyList<Media>() }
                .filter { media -> media.mediaTypeLabel() == type },
        )
    }

    override fun createHomeViewModel(): HomeViewModel = HomeViewModel(
        listCatalog = ::listCatalog,
        getDetails = ::getDetails,
        catalogItems = ::catalogItems,
        syncCatalog = {
            when (val result = listCatalog()) {
                is Outcome.Success -> Outcome.Success(Unit)
                is Outcome.Failure -> result
            }
        },
        isCatalogStale = { false },
    )

    override suspend fun listAllMedia(): Outcome<List<Media>> = listCatalog()

    override suspend fun listMovies(): Result<List<MovieSummary>> = runCatching {
        catalogRepository.catalogItems("movie").first().filterIsInstance<MovieSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun listShows(): Result<List<ShowSummary>> = runCatching {
        catalogRepository.catalogItems("show").first().filterIsInstance<ShowSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    // ── Player ──────────────────────────────────────────────────────────

    override suspend fun fetchVideoSource(mediaId: Media.MediaId): Outcome<VideoSource> =
        playbackPort.prepareSource(mediaId)

    override suspend fun fetchSubtitleText(url: String): Outcome<String> = fetchSubtitleTextViaBrowser(url)

    override suspend fun fetchEpisodesForShow(showId: ShowId): Outcome<List<Episode>> =
        when (val result = catalogRepository.getDetails(Media.MediaId.Show(showId))) {
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

    @Suppress("TooGenericExceptionCaught") // Network-boundary catch-all, same pattern as the other containers.
    override suspend fun resolveShowIdForEpisode(episodeId: EpisodeId): ShowId? = try {
        api.getEpisode(episodeId.value).showId?.let { ShowId(it) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "resolveShowIdForEpisode failed for $episodeId" }
        null
    }

    override suspend fun savePlaybackProgress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        playbackMode: PlaybackMode,
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

    override suspend fun listAccountPlaybackProgress(): Result<List<PlaybackProgress>> = runCatching {
        when (val session = sessionPort.current()) {
            Session.Anonymous -> emptyList()

            is Session.Authenticated -> database.accountPlaybackProgressDao()
                .getAllForProfile(session.userId)
                .first()
                .map { it.toPlaybackProgress() }
        }
    }.onFailure { if (it is CancellationException) throw it }

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
        "episode" -> Media.MediaId.Episode(EpisodeId(contentId.toIntOrNull() ?: error("Invalid contentId: $contentId")))
        else -> error("Unknown contentType: $contentType")
    }

    override suspend fun savePlaybackSpeed(speed: Float) {
        userPreferences.setPlaybackSpeed(currentProfileKey(), speed)
    }

    override suspend fun loadPlaybackSpeed(): Float = userPreferences.playbackSpeed(currentProfileKey()).first()

    override suspend fun loadPreferredLanguage(): LanguageCode =
        LanguageCode(userPreferences.subtitleLanguage(currentProfileKey()).first() ?: DEFAULT_LANGUAGE)

    override fun invalidateSession() {
        containerScope.launch { sessionPort.invalidate() }
    }

    // ── Library / downloads ─────────────────────────────────────────────

    override suspend fun listLibrary(): Outcome<List<LibraryItem>> = libraryPort.listLibrary()

    override suspend fun listDownloads(): Result<ImmutableList<DownloadState>> = downloadController.listDownloads()

    override suspend fun removeDownload(localId: String): Result<DownloadCommandOutcome> =
        downloadController.remove(LocalMediaIdentifier(localId))

    override suspend fun listSeasonQueues(): Result<ImmutableList<SeasonDownloadQueue>> = runCatching {
        seasonQueueController.listQueues().toImmutableList()
    }.onFailure { if (it is CancellationException) throw it }

    override suspend fun retryDownload(localId: String): EnqueueOutcome = EnqueueOutcome.Queued

    override suspend fun pauseDownload(localId: String): DownloadCommandOutcome =
        downloadController.pause(LocalMediaIdentifier(localId)).getOrDefault(DownloadCommandOutcome.NoOp)

    override suspend fun resumeDownload(localId: String): DownloadCommandOutcome =
        downloadController.resume(LocalMediaIdentifier(localId)).getOrDefault(DownloadCommandOutcome.NoOp)

    override suspend fun cancelDownload(localId: String): DownloadCommandOutcome =
        downloadController.cancel(LocalMediaIdentifier(localId)).getOrDefault(DownloadCommandOutcome.NoOp)

    override suspend fun listWatchedContentIds(): Set<String> = database.watchedStateDao()
        .getAllForProfile(currentProfileKey().value)
        .first()
        .filter { it.isWatched }
        .map { it.contentId }
        .toSet()

    override suspend fun isWatched(mediaId: Media.MediaId): Boolean = database.watchedStateDao()
        .getByProfileAndContentId(currentProfileKey().value, mediaId.toContentId())
        ?.isWatched == true

    // ── Session / settings ──────────────────────────────────────────────

    override fun readSubtitleEnabled(profileKey: AccountProfileKey): Flow<Boolean> =
        userPreferences.subtitleEnabled(profileKey)

    override fun readSubtitleLanguage(profileKey: AccountProfileKey): Flow<String?> =
        userPreferences.subtitleLanguage(profileKey)

    override fun readQuality(profileKey: AccountProfileKey): Flow<String?> = userPreferences.quality(profileKey)

    override fun readPlaybackSpeed(profileKey: AccountProfileKey): Flow<Float> =
        userPreferences.playbackSpeed(profileKey)

    override fun readDownloadsWifiOnly(profileKey: AccountProfileKey): Flow<Boolean> =
        userPreferences.downloadsWifiOnly(profileKey)

    override fun currentProfileKey(): AccountProfileKey = when (val session = sessionPort.current()) {
        is Session.Authenticated -> AccountProfileKey(session.userId)
        Session.Anonymous -> AccountProfileKey(DEFAULT_PROFILE_KEY)
    }

    override fun writeSubtitleEnabled(enabled: Boolean) {
        containerScope.launch { userPreferences.setSubtitleEnabled(currentProfileKey(), enabled) }
    }

    override fun writeSubtitleLanguage(language: String?) {
        containerScope.launch { userPreferences.setSubtitleLanguage(currentProfileKey(), language) }
    }

    override fun writeQuality(quality: String?) {
        containerScope.launch { userPreferences.setQuality(currentProfileKey(), quality) }
    }

    override fun writePlaybackSpeed(speed: Float) {
        containerScope.launch { userPreferences.setPlaybackSpeed(currentProfileKey(), speed) }
    }

    override fun writeDownloadsWifiOnly(wifiOnly: Boolean) {
        containerScope.launch { userPreferences.setDownloadsWifiOnly(currentProfileKey(), wifiOnly) }
    }

    override fun deleteAllDownloads() {
        containerScope.launch {
            downloadController.listDownloads()
                .onFailure { log.e(it) { "listDownloads failed while deleting all downloads" } }
                .getOrNull()
                ?.forEach { state ->
                    downloadController.remove(state.localId)
                        .onFailure { log.e(it) { "remove failed for ${state.localId}" } }
                }
        }
    }

    override fun clearPreferences() {
        containerScope.launch { userPreferences.clearProfilePreferences(currentProfileKey()) }
    }

    override fun clearLibrary() {
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

    override fun clearCredentials() {
        containerScope.launch { sessionPort.close() }
    }

    override fun close() = Unit

    private suspend fun markWatched(mediaId: Media.MediaId) {
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

    private fun Media.mediaTypeLabel(): String = when (this) {
        is MovieSummary -> "movie"
        is ShowSummary -> "show"
    }
}

/**
 * Browser storage shell for the downloads port: the file store has no
 * browser filesystem to back it (byte transfer is jvm-only), so deletion
 * is a no-op that reports success; the storage ports report a nominal
 * budget (browser quota APIs are async and not reachable through the
 * sync [StoragePort]), which effectively leaves the storage gate open —
 * harmless while no transfer runs on web.
 */
private object NoOpDownloadStore : DownloadFileStore {
    override fun deleteMedia(localPath: OfflineRelativePath): Boolean = true
}

private object BrowserStorageProvider : StoragePort {
    private const val NOMINAL_BYTES = 1L shl 40

    override fun availableBytes(): Long = NOMINAL_BYTES

    override fun totalBytes(): Long = NOMINAL_BYTES

    override fun reserveBytes(): Long = DownloadPolicy.requiredReserveBytes(totalBytes())
}

private object BrowserConnectivityChecker : ConnectivityPort {
    override fun isOnline(): Boolean = true

    override fun isMetered(): Boolean = false
}
