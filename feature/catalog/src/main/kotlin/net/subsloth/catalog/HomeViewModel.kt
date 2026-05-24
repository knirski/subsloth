package net.subsloth.catalog

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.subsloth.core.model.download.DownloadState
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
    data class Content(val rows: ImmutableList<HomeRow>, val selectedTab: HomeTab) : HomeUiState
}

@Stable
sealed interface HomeRow {
    val label: String?
    val items: ImmutableList<Media>

    @Immutable
    data class ContinueWatching(
        override val items: ImmutableList<Media>,
        override val label: String? = "Continue Watching",
    ) : HomeRow

    @Immutable
    data class AvailableOffline(
        override val items: ImmutableList<Media>,
        override val label: String? = "Available Offline",
    ) : HomeRow

    @Immutable
    data class Movies(override val items: ImmutableList<Media>, override val label: String? = "Movies") : HomeRow

    @Immutable
    data class Shows(override val items: ImmutableList<Media>, override val label: String? = "Shows") : HomeRow

    @Immutable
    data class Recency(override val items: ImmutableList<Media>, override val label: String) : HomeRow
}

enum class HomeTab { MOVIES, SHOWS, SEARCH }

class HomeViewModel(
    private val listCatalog: suspend () -> Result<List<Media>> = { Result.success(emptyList()) },
    private val getDetails: suspend (Media.MediaId) -> Result<MediaDetails> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
    private val listLibrary: suspend () -> Result<List<LibraryItem>> = {
        Result.success(emptyList())
    },
    private val listDownloads: suspend () -> Result<List<DownloadState>> = {
        Result.success(emptyList())
    },
    private val isOnline: () -> Boolean = { true },
    private val isMetered: () -> Boolean = { false },
    private val now: () -> Instant = { Instant.fromEpochSeconds(0L) },
    private val savedState: Map<String, String> = mapOf(
        "selectedTab" to "",
        "searchQuery" to "",
    ),
) : ViewModel() {
    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadCatalog()
    }

    private fun loadCatalog() {
        viewModelScope.launch {
            _uiState.value = HomeUiState.Loading

            val catalogResult = listCatalog()
            val catalog = catalogResult.getOrDefault(emptyList())

            val movies = catalog.filterIsInstance<MovieSummary>()
            val shows = catalog.filterIsInstance<ShowSummary>()

            val continueWatchingRow = buildContinueWatchingItems(catalog)
                .takeIf { it.isNotEmpty() }
                ?.let { HomeRow.ContinueWatching(it.toImmutableList()) }
            val offlineRow = buildOfflineItems(catalog)
                .takeIf { it.isNotEmpty() }
                ?.let { HomeRow.AvailableOffline(it.toImmutableList()) }
            val recencyRows = buildRecencyRows(movies, shows)
            val moviesRow = movies.takeIf { it.isNotEmpty() }
                ?.let { HomeRow.Movies(it.toImmutableList()) }
            val showsRow = shows.takeIf { it.isNotEmpty() }
                ?.let { HomeRow.Shows(it.toImmutableList()) }

            val rows = listOfNotNull(continueWatchingRow, offlineRow)
                .plus(recencyRows)
                .plus(listOfNotNull(moviesRow, showsRow))
                .toImmutableList()

            val restoredTab = parseSavedTab(savedState["selectedTab"].orEmpty())
            _uiState.value = HomeUiState.Content(
                rows = rows,
                selectedTab = restoredTab,
            )
        }
    }

    @Suppress("UnusedParameter")
    private fun buildContinueWatchingItems(catalog: List<Media>): List<Media> = emptyList()

    @Suppress("UnusedParameter")
    private fun buildOfflineItems(catalog: List<Media>): List<Media> = emptyList()

    private fun buildRecencyRows(movies: List<MovieSummary>, shows: List<ShowSummary>): ImmutableList<HomeRow.Recency> {
        val recentlyAdded = movies.filter { it.updatedAtEpochSeconds != null }
            .takeIf { it.isNotEmpty() }
            ?.let { HomeRow.Recency(items = it.toImmutableList(), label = "Recently Added") }
        val showsWithRecent = shows.filter { it.newestVideoEpochSeconds != null }
            .takeIf { it.isNotEmpty() }
            ?.let { HomeRow.Recency(items = it.toImmutableList(), label = "Shows with recent episodes") }
        val releaseDate = movies.filter { it.year != null && it.updatedAtEpochSeconds == null }
            .takeIf { it.isNotEmpty() }
            ?.let { HomeRow.Recency(items = it.toImmutableList(), label = "Recent by release date") }

        return listOfNotNull(recentlyAdded, showsWithRecent, releaseDate).toImmutableList()
    }

    private fun parseSavedTab(tab: String): HomeTab = when (tab.uppercase()) {
        "MOVIES" -> HomeTab.MOVIES
        "SHOWS" -> HomeTab.SHOWS
        "SEARCH" -> HomeTab.SEARCH
        else -> HomeTab.MOVIES
    }
}
