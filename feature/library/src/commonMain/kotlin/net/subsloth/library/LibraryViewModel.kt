package net.subsloth.library

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.progress.PlaybackProgress

@Stable
sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    @Immutable
    data class Content(
        val isLoggedIn: Boolean,
        val continueWatching: ImmutableList<Media>,
        val favorites: ImmutableList<Media>,
        val watchLater: ImmutableList<Media>,
        val availableOffline: ImmutableList<Media>,
        val custom: ImmutableList<Media>,
        val showCatalogLink: Boolean,
    ) : LibraryUiState
}

class LibraryViewModel(
    private val libraryPort: suspend () -> Result<List<LibraryItem>> = {
        Result.success(emptyList())
    },
    private val downloadsPort: suspend () -> Result<ImmutableList<DownloadState>> = {
        Result.success(persistentListOf())
    },
    private val listMovies: suspend () -> Result<List<MovieSummary>> = {
        Result.success(emptyList())
    },
    private val listShows: suspend () -> Result<List<ShowSummary>> = {
        Result.success(emptyList())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    private val isLoggedIn: () -> Boolean = { true },
    private val removeDownload: suspend (String) -> Result<DownloadCommandOutcome> = {
        Result.success(DownloadCommandOutcome.NoOp)
    },
) : ViewModel() {
    private val _uiState = MutableStateFlow<LibraryUiState>(LibraryUiState.Loading)
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadLibrary()
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            _uiState.value = LibraryUiState.Loading
            val loggedIn = isLoggedIn()

            val libraryResult = libraryPort()
            val library = libraryResult.getOrDefault(emptyList())

            val downloadsResult = downloadsPort()
            val downloads = downloadsResult.getOrDefault(persistentListOf())

            val moviesResult = listMovies()
            val movies = moviesResult.getOrDefault(emptyList())
            val showsResult = listShows()
            val shows = showsResult.getOrDefault(emptyList())
            val catalog = (movies + shows).associateBy { it.id }

            val progressResult = listProgress()
            val progress = progressResult.getOrDefault(emptyList())

            val continueWatching = buildContinueWatching(progress, catalog)

            val offlineIds = downloads
                .filterIsInstance<DownloadState.Completed>()
                .map { it.mediaId }
                .toSet()
            val availableOffline = offlineIds.mapNotNull { catalog[it] }

            if (loggedIn) {
                val favorites = library
                    .filter { it.collection == LibraryCollection.FAVORITES }
                    .mapNotNull { catalog[it.mediaId] }
                val watchLater = library
                    .filter { it.collection == LibraryCollection.HISTORY }
                    .mapNotNull { catalog[it.mediaId] }
                val custom = library
                    .filter { it.collection == LibraryCollection.CUSTOM }
                    .mapNotNull { catalog[it.mediaId] }

                _uiState.value = LibraryUiState.Content(
                    isLoggedIn = true,
                    continueWatching = continueWatching.toImmutableList(),
                    favorites = favorites.toImmutableList(),
                    watchLater = watchLater.toImmutableList(),
                    availableOffline = availableOffline.toImmutableList(),
                    custom = custom.toImmutableList(),
                    showCatalogLink = true,
                )
            } else {
                _uiState.value = LibraryUiState.Content(
                    isLoggedIn = false,
                    continueWatching = persistentListOf(),
                    favorites = persistentListOf(),
                    watchLater = persistentListOf(),
                    availableOffline = availableOffline.toImmutableList(),
                    custom = persistentListOf(),
                    showCatalogLink = false,
                )
            }
        }
    }

    private fun buildContinueWatching(
        progress: List<PlaybackProgress>,
        catalog: Map<Media.MediaId, Media>,
    ): List<Media> = progress
        .filter { it.positionSeconds > 0 && it.durationSeconds > 0 }
        .mapNotNull { catalog[it.mediaId] }

    fun deleteDownload(localId: String) {
        viewModelScope.launch {
            removeDownload(localId)
            loadLibrary()
        }
    }
}
