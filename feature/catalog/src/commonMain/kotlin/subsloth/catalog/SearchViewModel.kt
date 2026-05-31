package subsloth.catalog

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import subsloth.core.domain.policy.SearchPolicy
import subsloth.core.model.media.Media
import subsloth.core.model.media.MediaDetails
import subsloth.core.model.media.MovieSummary
import subsloth.core.model.media.ShowSummary

@Stable
sealed interface SearchUiState {
    data object Idle : SearchUiState

    @Immutable
    data class Results(val query: String, val items: ImmutableList<Media>, val isLoading: Boolean = false) :
        SearchUiState
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val listCatalog: suspend () -> Result<List<Media>> = { Result.success(emptyList()) },
    private val getDetails: suspend (Media.MediaId) -> Result<MediaDetails> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
    private val savedState: Map<String, String> = mapOf("searchQuery" to ""),
) : ViewModel() {
    private val _uiState: MutableStateFlow<SearchUiState>
    val uiState: StateFlow<SearchUiState> get() = _uiState.asStateFlow()

    private val _filters = MutableStateFlow(SearchFilters())
    val filters: StateFlow<SearchFilters> = _filters.asStateFlow()

    private var catalogDeferred: Deferred<List<Media>>? = null
    private val searchChannel = Channel<String>(Channel.CONFLATED)

    init {
        val restoredQuery = savedState["searchQuery"].orEmpty()
        val initialState = if (restoredQuery.isNotBlank()) {
            SearchUiState.Results(query = restoredQuery, items = persistentListOf(), isLoading = true)
        } else {
            SearchUiState.Idle
        }
        _uiState = MutableStateFlow(initialState)

        viewModelScope.launch {
            searchChannel.receiveAsFlow()
                .flatMapLatest { query -> searchInternal(query) }
                .collect { state -> _uiState.value = state }
        }

        if (restoredQuery.isNotBlank()) {
            searchChannel.trySend(restoredQuery)
        }
    }

    override fun onCleared() {
        searchChannel.close()
        super.onCleared()
    }

    fun search(query: String) {
        searchChannel.trySend(query)
    }

    private suspend fun ensureCatalogLoaded(): List<Media> {
        if (catalogDeferred == null) {
            catalogDeferred = viewModelScope.async(start = CoroutineStart.LAZY) {
                listCatalog().getOrDefault(emptyList())
            }
        }
        val catalog = catalogDeferred!!.await()
        if (catalog.isEmpty()) {
            catalogDeferred = null
        }
        return catalog
    }

    private fun searchInternal(query: String): Flow<SearchUiState> = flow {
        emit(SearchUiState.Results(query = query, items = persistentListOf(), isLoading = true))

        val catalog = ensureCatalogLoaded()
        val filtered = applyFilters(catalog)
        val matched = SearchPolicy.filter(filtered, query)
        emit(SearchUiState.Results(query = query, items = matched.toImmutableList(), isLoading = false))
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
