package net.subsloth.catalog

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.SearchPolicy
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary

@Stable
sealed interface SearchUiState {
    data object Idle : SearchUiState

    @Immutable
    data class Results(val query: String, val items: List<Media>, val isLoading: Boolean = false) : SearchUiState
}

@Immutable
data class SearchFilters(
    val type: MediaTypeFilter = MediaTypeFilter.ALL,
    val genre: String? = null,
    val country: String? = null,
    val subtitleLanguage: String? = null,
    val watched: FilterOption = FilterOption.ANY,
    val downloaded: FilterOption = FilterOption.ANY,
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val ratingFrom: Double? = null,
    val ratingTo: Double? = null,
)

enum class MediaTypeFilter { ALL, MOVIES, SHOWS }

enum class FilterOption { ANY, YES, NO }

class SearchViewModel(
    private val listCatalog: suspend () -> Result<List<Media>> = { Result.success(emptyList()) },
    private val getDetails: suspend (Media.MediaId) -> Result<MediaDetails> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
    private val savedState: Map<String, String> = mapOf("searchQuery" to ""),
) : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private var catalog: List<Media> = emptyList()
    private var searchJob: Job? = null

    init {
        val restoredQuery = savedState["searchQuery"].orEmpty()
        if (restoredQuery.isNotBlank()) {
            search(restoredQuery)
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.value = SearchUiState.Results(query = query, items = emptyList(), isLoading = true)

            if (catalog.isEmpty()) {
                catalog = listCatalog().getOrDefault(emptyList())
            }

            val filtered = applyFilters(catalog)
            val matched = SearchPolicy.filter(filtered, query)
            _uiState.value = SearchUiState.Results(query = query, items = matched, isLoading = false)
        }
    }

    fun updateFilters(newFilters: SearchFilters) {
        _filters.value = newFilters
        val currentQuery = (_uiState.value as? SearchUiState.Results)?.query ?: return
        search(currentQuery)
    }

    private fun applyFilters(items: List<Media>): List<Media> {
        val filters = _filters.value
        return items.filter { item ->
            val matchesType = when (filters.type) {
                MediaTypeFilter.ALL -> true
                MediaTypeFilter.MOVIES -> item is MovieSummary
                MediaTypeFilter.SHOWS -> item is ShowSummary
            }
            val matchesGenre = filters.genre == null ||
                item.genres.any { it.contains(filters.genre, ignoreCase = true) }

            matchesType && matchesGenre
        }
    }
}
