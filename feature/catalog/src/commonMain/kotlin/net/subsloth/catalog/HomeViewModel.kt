package net.subsloth.catalog

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
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
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import kotlin.time.Instant

@Stable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(val rows: ImmutableList<HomeRow>, val selectedTab: HomeTab, val isSyncing: Boolean = false) :
        HomeUiState
}

@Stable
sealed interface HomeRow {
    val label: String?
    val items: ImmutableList<Media>

    @Immutable
    data class Movies(override val items: ImmutableList<Media>, override val label: String? = "Movies") : HomeRow

    @Immutable
    data class Shows(override val items: ImmutableList<Media>, override val label: String? = "Shows") : HomeRow

    @Immutable
    data class Recency(override val items: ImmutableList<Media>, override val label: String) : HomeRow
}

enum class HomeTab { MOVIES, SHOWS, SEARCH }

private data class SyncRequest(val silent: Boolean)

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

    private val restoredTab = parseSavedTab(savedState["selectedTab"].orEmpty())

    init {
        viewModelScope.launch {
            combine(
                catalogItems("movie"),
                catalogItems("show"),
                isSyncing,
            ) { movies, shows, syncing ->
                buildHomeContent(
                    movies = movies,
                    shows = shows,
                    selectedTab = restoredTab,
                    isSyncing = syncing,
                )
            }.collect { content ->
                _uiState.value = content
            }
        }
        viewModelScope.launch {
            syncChannel.receiveAsFlow().collectLatest { request ->
                isSyncing.value = true
                try {
                    when (val outcome = syncCatalog()) {
                        is Outcome.Failure -> {
                            log.e(null) { "Sync failed: ${outcome.error}" }
                            if (!request.silent) {
                                _syncErrors.tryEmit(SyncError.Unknown)
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
}

internal fun buildHomeContent(
    movies: List<Media>,
    shows: List<Media>,
    selectedTab: HomeTab = HomeTab.MOVIES,
    isSyncing: Boolean = false,
): HomeUiState.Content {
    val movieItems = movies.filterIsInstance<MovieSummary>()
    val showItems = shows.filterIsInstance<ShowSummary>()

    val recencyRows = buildRecencyRows(movieItems, showItems)

    val rows = buildList {
        addAll(recencyRows)
        movieItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Movies(it.toImmutableList())) }
        showItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Shows(it.toImmutableList())) }
    }.toImmutableList()

    return HomeUiState.Content(rows = rows, selectedTab = selectedTab, isSyncing = isSyncing)
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
    "MOVIES" -> HomeTab.MOVIES
    "SHOWS" -> HomeTab.SHOWS
    "SEARCH" -> HomeTab.SEARCH
    else -> HomeTab.MOVIES
}
