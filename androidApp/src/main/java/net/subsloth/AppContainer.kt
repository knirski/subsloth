package net.subsloth

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import net.subsloth.core.data.media.CatalogRepository
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.domain.port.StoragePort
import net.subsloth.core.media.download.ConnectivityChecker
import net.subsloth.core.media.download.DownloadController
import net.subsloth.core.media.download.DownloadStorageManager
import net.subsloth.core.media.download.SeasonQueueController
import net.subsloth.core.media.download.StorageProvider
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.database.LibraryPortAdapter
import net.subsloth.database.SubSlothDatabase
import net.subsloth.database.createSubSlothDatabase
import net.subsloth.database.entity.AccountPlaybackProgressEntity
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

    /** Room database for cached catalog, library, and playback state. */
    val database: SubSlothDatabase by lazy {
        createSubSlothDatabase("subsloth_db")
    }

    /** Cached catalog DAO. */
    val cachedCatalogDao by lazy { database.cachedCatalogDao() }

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

    /** Catalog repository combining API sync with Room caching. */
    @Volatile
    private var currentCatalogRepository: CatalogRepository = buildCatalogRepository(currentApi)

    val api: Api get() = currentApi

    val catalogRepository: CatalogRepository get() = currentCatalogRepository

    /**
     * Production [net.subsloth.core.domain.port.LibraryPort] implementation:
     * favorites and custom-list membership, persisted to Room and scoped by
     * the active session's user profile key (read fresh from [sessionPort]
     * on every call — see [LibraryPortAdapter.profileKey]), so a single
     * instance stays correct across login/logout/account switches.
     */
    val libraryPortAdapter: LibraryPortAdapter by lazy {
        LibraryPortAdapter(
            favoriteDao = database.favoriteDao(),
            localLibraryDao = database.localLibraryRecordDao(),
            sessionPort = sessionPort,
        )
    }

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

    /** Season-level download queue orchestration, wrapping [downloadController]. */
    private val seasonQueueController: SeasonQueueController by lazy {
        SeasonQueueController(
            downloadsPort = downloadController,
            seasonQueueDao = database.seasonQueueDao(),
            clock = clock,
        )
    }

    init {
        // Cold-start session recovery — invoked exactly once, unconditionally,
        // as part of container construction.
        containerScope.launch { androidSessionState.recover() }

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
                // Close the superseded client only after the new one is fully
                // swapped in, so nothing still references it as "current".
                previousApi.close()
            }
        }
    }

    /**
     * Resolves the API base URL with the same precedence
     * `MainActivity.kt`'s `readApiBaseUrl` uses for [net.subsloth.auth.LoginViewModel]:
     * the persisted [UserPreferences.apiBaseUrl] value, unless it is still
     * the default and a non-empty [BuildConfig.SUBSLOTH_API_BASE_URL] override
     * is configured, in which case the build-config value wins.
     */
    private suspend fun resolveApiBaseUrl(): String {
        val stored = userPreferences.apiBaseUrl().first()
        return if (stored == UserPreferences.DEFAULT_API_BASE_URL && BuildConfig.SUBSLOTH_API_BASE_URL.isNotEmpty()) {
            BuildConfig.SUBSLOTH_API_BASE_URL
        } else {
            stored
        }
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
        catalogDao = cachedCatalogDao,
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
