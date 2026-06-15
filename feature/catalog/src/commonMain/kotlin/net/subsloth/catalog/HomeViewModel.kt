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
import kotlinx.coroutines.launch
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.error.DomainResultException
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

private data class SyncRequest(val silent: Boolean)

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
    private val catalogItems: (String) -> Flow<List<Media>> = { flowOf(emptyList()) },
    private val syncCatalog: suspend () -> Result<Unit> = { Result.success(Unit) },
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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncErrors = MutableSharedFlow<SyncError>(replay = 1)
    val syncErrors: Flow<SyncError> = _syncErrors

    private val syncChannel = Channel<SyncRequest>(Channel.CONFLATED)

    private val restoredTab = parseSavedTab(savedState["selectedTab"].orEmpty())

    init {
        viewModelScope.launch {
            catalogItems("movie").combine(catalogItems("show")) { movies, shows ->
                buildHomeContent(movies, shows, selectedTab = restoredTab)
            }.collect { content ->
                _uiState.value = content
            }
        }
        viewModelScope.launch {
            syncChannel.receiveAsFlow().collectLatest { request ->
                _isSyncing.value = true
                try {
                    syncCatalog()
                        .onFailure { error ->
                            log.e(error) { "Sync failed" }
                            if (!request.silent) {
                                val syncError = (error as? DomainResultException)?.domainError as? SyncError
                                    ?: SyncError.Unknown
                                _syncErrors.emit(syncError)
                            }
                        }
                } finally {
                    _isSyncing.value = false
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

@Suppress("UnusedParameter")
internal fun buildContinueWatchingItems(catalog: List<Media>): List<Media> = emptyList()

@Suppress("UnusedParameter")
internal fun buildOfflineItems(catalog: List<Media>): List<Media> = emptyList()

internal fun buildHomeContent(
    movies: List<Media>,
    shows: List<Media>,
    selectedTab: HomeTab = HomeTab.MOVIES,
): HomeUiState.Content {
    val movieItems = movies.filterIsInstance<MovieSummary>()
    val showItems = shows.filterIsInstance<ShowSummary>()

    val recencyRows = buildRecencyRows(movieItems, showItems)

    val rows = buildList {
        buildContinueWatchingItems(movies + shows).takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.ContinueWatching(it.toImmutableList())) }
        buildOfflineItems(movies + shows).takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.AvailableOffline(it.toImmutableList())) }
        addAll(recencyRows)
        movieItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Movies(it.toImmutableList())) }
        showItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Shows(it.toImmutableList())) }
    }.toImmutableList()

    return HomeUiState.Content(rows = rows, selectedTab = selectedTab)
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
