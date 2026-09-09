package net.subsloth.desktop

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.data.session.ValidatingSessionState
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.AccountProfileKey
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
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
import net.subsloth.preferences.AccountProfileStore
import net.subsloth.preferences.CredentialStore
import net.subsloth.preferences.CredentialsStoreAdapter
import net.subsloth.preferences.UserPreferences
import net.subsloth.preferences.createDataStorePreferences
import java.io.File
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
 * Deliberate omissions (see `docs/architecture/composition-roots.md`):
 * - **Downloads** — `DownloadController` and its `Context`-backed storage
 *   collaborators are androidMain-only, so `DownloadsPort` stays on its
 *   safe empty-list defaults on desktop until a JVM storage shell exists.
 * - **Build-config base URL** — desktop has no `SUBSLOTH_API_BASE_URL`
 *   build-config field; the persisted [UserPreferences.apiBaseUrl] value
 *   is used as-is.
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
     * App data directory (`~/.local/share/subsloth` on Linux/macOS, `%APPDATA%\subsloth` on
     * Windows). Created eagerly: SQLite does not create missing parent directories, so the
     * first database operation would fail on a fresh install otherwise.
     */
    private val dataDir: File = (dataDirOverride ?: resolveAppDataDir()).apply { mkdirs() }

    private val dataStore: DataStore<Preferences> by lazy {
        createDataStorePreferences(name = "subsloth", scope = containerScope)
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
        baseUrlProvider = { userPreferences.apiBaseUrl().first() },
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

    @Volatile
    private var currentPlaybackPort: PlaybackPort = ApiPlaybackPort(currentApi)

    val api: Api get() = currentApi

    val catalogRepository: CatalogRepository get() = currentCatalogRepository

    val playbackPort: PlaybackPort get() = currentPlaybackPort

    /** Production [net.subsloth.core.domain.port.LibraryPort]: favorites and
     * custom-list membership, persisted to Room and scoped by the active
     * session's profile key (read fresh per call). Stays correct across
     * login/logout without rebuilding.
     */
    val libraryPortAdapter by lazy {
        LibraryPortAdapter(
            favoriteDao = database.favoriteDao(),
            localLibraryDao = database.localLibraryRecordDao(),
            sessionPort = sessionPort,
        )
    }

    init {
        // Cold-start session recovery — invoked exactly once.
        containerScope.launch { sessionState.recover() }

        // Rebuild the session-scoped adapter trio whenever the session's
        // credentials actually change (login/logout/account switch).
        containerScope.launch {
            sessionPort.state.collect { session ->
                val previousApi = currentApi
                val newApi = buildApi(session)
                currentApi = newApi
                currentCatalogRepository = buildCatalogRepository(newApi)
                currentPlaybackPort = ApiPlaybackPort(newApi)
                previousApi.close()
            }
        }
    }

    private suspend fun buildApi(session: Session): Api = when (session) {
        is Session.Authenticated -> Api(
            ClientFactory.create(
                login = session.credentials.login,
                password = session.credentials.password,
                baseUrl = userPreferences.apiBaseUrl().first(),
            ),
        )

        Session.Anonymous -> Api(ClientFactory.create(baseUrl = userPreferences.apiBaseUrl().first()))
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
    suspend fun listMovies(): Result<List<MovieSummary>> = runCatching {
        catalogRepository.catalogItems("movie").first().filterIsInstance<MovieSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    /** Show-list counterpart of [listMovies]. */
    suspend fun listShows(): Result<List<ShowSummary>> = runCatching {
        catalogRepository.catalogItems("show").first().filterIsInstance<ShowSummary>()
    }.onFailure { if (it is CancellationException) throw it }

    // ── Player wiring (net.subsloth.player.PlayerViewModel) ─────────────

    /**
     * Maps the active session's account-scoped playback progress into the
     * [PlaybackProgress] domain shape consumed by `LibraryViewModel`'s
     * "Continue Watching" row — same mapping as `AppContainer`'s
     * `listAccountPlaybackProgress`.
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

    /** Flattens [ShowDetails]'s seasons into a single episode list for the "next episode" lookup. */
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

    /** Persists online playback progress for the active profile. */
    suspend fun savePlaybackProgress(mediaId: Media.MediaId, positionSeconds: Long, durationSeconds: Long) {
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
    }

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
