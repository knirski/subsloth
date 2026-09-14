package net.subsloth.catalog

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.CompletionPolicy
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.progress.PlaybackProgress
import kotlin.time.Instant

@Stable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(
        val rows: ImmutableList<HomeRow<*>>,
        val selectedTab: HomeTab,
        val isSyncing: Boolean = false,
        val moviesUnavailable: Boolean = false,
        val continueWatching: ImmutableList<Media> = persistentListOf(),
        val availableOffline: ImmutableList<Media> = persistentListOf(),
        val favorites: ImmutableList<Media> = persistentListOf(),
        val watchLater: ImmutableList<Media> = persistentListOf(),
    ) : HomeUiState
}

@Stable
sealed interface HomeRow<out T : Media> {
    val label: String?
    val items: ImmutableList<T>

    @Immutable
    data class Movies(override val items: ImmutableList<MovieSummary>, override val label: String? = "Movies") :
        HomeRow<MovieSummary>

    @Immutable
    data class Shows(override val items: ImmutableList<ShowSummary>, override val label: String? = "Shows") :
        HomeRow<ShowSummary>

    @Immutable
    data class Recency(override val items: ImmutableList<Media>, override val label: String? = null) : HomeRow<Media>
}

enum class HomeTab { HOME, MOVIES, SHOWS, FAVORITES, WATCH_LATER }

private data class SyncRequest(val silent: Boolean)

/** One-shot library/download/progress data backing the personal Home tabs. */
private data class HomeAuxData(
    val library: List<LibraryItem> = emptyList(),
    val downloads: List<DownloadState> = emptyList(),
    val progress: List<PlaybackProgress> = emptyList(),
)

class HomeViewModel(
    private val listCatalog: suspend () -> Outcome<List<Media>> = { Outcome.Success(emptyList()) },
    private val getDetails: suspend (Media.MediaId) -> Outcome<MediaDetails> = {
        Outcome.Failure(DecodeError.SerializationFailed)
    },
    private val listLibrary: suspend () -> Outcome<List<LibraryItem>> = {
        Outcome.Success(emptyList())
    },
    private val listDownloads: suspend () -> Result<List<DownloadState>> = {
        Result.success(emptyList())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    /**
     * Resolves an episode to the show that owns it, so episode playback can
     * feed show-level rows (Continue Watching) whose catalog only contains
     * movies and shows.
     */
    private val resolveShowForEpisode: suspend (EpisodeId) -> ShowId? = { null },
    private val catalogItems: (String) -> Flow<List<Media>> = { flowOf(emptyList()) },
    private val syncCatalog: suspend () -> Outcome<Unit> = { Outcome.Success(Unit) },
    private val isCatalogStale: suspend () -> Boolean = { true },
    private val isOnline: () -> Boolean = { true },
    private val isMetered: () -> Boolean = { false },
    private val now: () -> Instant = { Instant.fromEpochSeconds(0L) },
    private val savedState: Map<String, String> = mapOf(
        "selectedTab" to "",
        "searchQuery" to "",
    ),
) : ViewModel() {
    private val log = Logger.withTag("HomeViewModel")
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /**
     * The in-progress sync flag. Held as a [MutableStateFlow] so
     * changes propagate to the `combine` below — flipping it
     * re-emits a fresh [HomeUiState.Content] with the new flag,
     * with no need for a private mutable or a separate `_uiState.update`
     * codepath that re-copies the Content from a possibly-stale
     * snapshot.
     */
    private val isSyncing = MutableStateFlow(false)

    private val _syncErrors = MutableSharedFlow<SyncError>(extraBufferCapacity = 1)
    val syncErrors: Flow<SyncError> = _syncErrors

    private val syncChannel = Channel<SyncRequest>(Channel.CONFLATED)

    private val selectedTab = MutableStateFlow(parseSavedTab(savedState["selectedTab"].orEmpty()))

    private val auxData = MutableStateFlow(HomeAuxData())

    init {
        viewModelScope.launch {
            combine(
                catalogItems("movie"),
                catalogItems("show"),
                isSyncing,
                selectedTab,
                auxData,
            ) { movies, shows, syncing, tab, aux ->
                buildHomeContent(
                    movies = movies,
                    shows = shows,
                    selectedTab = tab,
                    isSyncing = syncing,
                    library = aux.library,
                    downloads = aux.downloads,
                    progress = aux.progress,
                )
            }.collect { content ->
                _uiState.value = content
            }
        }
        refreshAuxData()
        viewModelScope.launch {
            syncChannel.receiveAsFlow().collectLatest { request ->
                isSyncing.value = true
                try {
                    when (val outcome = syncCatalog()) {
                        is Outcome.Failure -> {
                            log.e(null) { "Sync failed: ${outcome.error}" }
                            if (!request.silent) {
                                val syncError = outcome.error as? SyncError ?: SyncError.Unknown
                                _syncErrors.tryEmit(syncError)
                            }
                        }

                        is Outcome.Success -> {}
                    }
                } finally {
                    isSyncing.value = false
                }
            }
        }
        viewModelScope.launch {
            if (isCatalogStale()) {
                syncChannel.trySend(SyncRequest(silent = true))
            }
        }
    }

    fun sync() {
        syncChannel.trySend(SyncRequest(silent = false))
    }

    fun retrySync() {
        viewModelScope.launch { sync() }
    }

    /** Switches tabs and refreshes the personal data the new tab renders. */
    fun selectTab(tab: HomeTab) {
        if (selectedTab.value == tab) return
        selectedTab.value = tab
        if (tab != HomeTab.MOVIES && tab != HomeTab.SHOWS) {
            refreshAuxData()
        }
    }

    /**
     * Reloads the one-shot personal data (library collections, completed
     * downloads, playback progress). Catalog data stays on its own flow; this
     * runs on init and whenever a personal tab is selected.
     */
    private fun refreshAuxData() {
        viewModelScope.launch {
            val library = when (val result = listLibrary()) {
                is Outcome.Success -> result.value

                is Outcome.Failure -> {
                    log.e(null) { "listLibrary failed: ${result.error}" }
                    auxData.value.library
                }
            }
            val downloads = listDownloads()
                .onFailure { log.e(it) { "listDownloads failed" } }
                .getOrDefault(auxData.value.downloads)
            val progress = listProgress()
                .onFailure { log.e(it) { "listProgress failed" } }
                .getOrDefault(auxData.value.progress)
                .toShowLevelProgress()
            auxData.value = HomeAuxData(
                library = library,
                downloads = downloads,
                progress = progress,
            )
        }
    }

    /**
     * Rewrites episode progress to the owning show, so Continue Watching
     * resolves it against the movie/show catalog. Multiple episodes of one
     * show collapse to the most recently watched.
     */
    private suspend fun List<PlaybackProgress>.toShowLevelProgress(): List<PlaybackProgress> {
        val resolved = mapNotNull { progress ->
            val episode = progress.mediaId as? Media.MediaId.Episode ?: return@mapNotNull progress
            val showId = resolveShowForEpisode(episode.value) ?: return@mapNotNull null
            progress.copy(mediaId = Media.MediaId.Show(showId))
        }
        return resolved
            .groupBy { it.mediaId }
            .mapNotNull { (_, entries) -> entries.maxByOrNull { it.lastUpdatedEpochSeconds } }
    }
}

internal fun buildHomeContent(
    movies: List<Media>,
    shows: List<Media>,
    selectedTab: HomeTab = HomeTab.HOME,
    isSyncing: Boolean = false,
    library: List<LibraryItem> = emptyList(),
    downloads: List<DownloadState> = emptyList(),
    progress: List<PlaybackProgress> = emptyList(),
): HomeUiState.Content {
    val movieItems = movies.filterIsInstance<MovieSummary>()
    val showItems = shows.filterIsInstance<ShowSummary>()

    val recencyRows = buildRecencyRows(movieItems, showItems)

    val rows = buildList<HomeRow<*>> {
        addAll(recencyRows)
        movieItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Movies(it.toImmutableList())) }
        showItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Shows(it.toImmutableList())) }
    }.toImmutableList()

    val catalog = (movieItems + showItems).associateBy { it.id }
    val continueWatching = progress
        .filter { CompletionPolicy.isInProgress(it.fraction) }
        .mapNotNull { catalog[it.mediaId] }
    val offlineIds = downloads
        .filterIsInstance<DownloadState.Completed>()
        .map { it.mediaId }
        .toSet()
    val availableOffline = offlineIds.mapNotNull { catalog[it] }
    val favorites = library
        .filter { it.collection == LibraryCollection.FAVORITES }
        .mapNotNull { catalog[it.mediaId] }
    val watchLater = library
        .filter { it.collection == LibraryCollection.WATCH_LATER }
        .mapNotNull { catalog[it.mediaId] }

    return HomeUiState.Content(
        rows = rows,
        selectedTab = selectedTab,
        isSyncing = isSyncing,
        // The backend hides its movie catalog from accounts without movie
        // entitlement: the movies list comes back empty while shows are
        // present, so surface that instead of an unexplained empty section.
        moviesUnavailable = movieItems.isEmpty() && showItems.isNotEmpty(),
        continueWatching = continueWatching.toImmutableList(),
        availableOffline = availableOffline.toImmutableList(),
        favorites = favorites.toImmutableList(),
        watchLater = watchLater.toImmutableList(),
    )
}

private fun buildRecencyRows(movies: List<MovieSummary>, shows: List<ShowSummary>): ImmutableList<HomeRow.Recency> {
    fun List<Media>.toRecencyRow(label: String): HomeRow.Recency? =
        takeIf { it.isNotEmpty() }?.let { HomeRow.Recency(items = it.toImmutableList(), label = label) }

    return listOfNotNull(
        movies.filter { it.updatedAtEpochSeconds != null }.toRecencyRow("Recently Added"),
        shows.filter { it.newestVideoEpochSeconds != null }.toRecencyRow("Shows with recent episodes"),
        movies.filter { it.year != null && it.updatedAtEpochSeconds == null }
            .toRecencyRow("Recent by release date"),
    ).toImmutableList()
}

private fun parseSavedTab(tab: String): HomeTab = when (tab.uppercase()) {
    "HOME", "SEARCH" -> HomeTab.HOME
    "MOVIES" -> HomeTab.MOVIES
    "SHOWS" -> HomeTab.SHOWS
    "FAVORITES" -> HomeTab.FAVORITES
    "WATCH_LATER" -> HomeTab.WATCH_LATER
    else -> HomeTab.HOME
}
