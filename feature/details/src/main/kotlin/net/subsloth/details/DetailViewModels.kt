package net.subsloth.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.error.toUiError

@Stable
sealed interface DetailUiState {
    data object Loading : DetailUiState

    @Immutable
    data class MovieContent(
        val details: MovieDetails,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val isDownloaded: Boolean = false,
        val progressFraction: Double? = null,
    ) : DetailUiState

    @Immutable
    data class ShowContent(
        val details: ShowDetails,
        val selectedSeason: Int,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val isDownloaded: Boolean = false,
        val progressFraction: Double? = null,
    ) : DetailUiState

    @Immutable
    data class Error(val error: UiError) : DetailUiState
}

class MovieDetailViewModel(
    private val mediaId: Media.MediaId.Movie,
    private val getDetails: suspend (Media.MediaId) -> Result<MediaDetails> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
    private val listLibrary: suspend () -> Result<List<LibraryItem>> = {
        Result.success(emptyList())
    },
    private val listDownloads: suspend () -> Result<List<DownloadState>> = {
        Result.success(emptyList())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
) : ViewModel() {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            val detailsResult = getDetails(mediaId)
            val libraryResult = listLibrary()
            val downloadsResult = listDownloads()
            detailsResult.fold(
                onSuccess = { details ->
                    if (details is MovieDetails) {
                        val library = libraryResult.getOrDefault(emptyList())
                        val downloads = downloadsResult.getOrDefault(emptyList())
                        _uiState.value =
                            DetailUiState.MovieContent(
                                details = details,
                                isFavorite = library.any {
                                    it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES
                                },
                                isDownloaded = downloads.any {
                                    it.mediaId == mediaId && it.status == DownloadStatus.COMPLETED
                                },
                            )
                    } else {
                        _uiState.value = DetailUiState.Error(UiError.NotFound("Unexpected media type"))
                    }
                },
                onFailure = { error ->
                    _uiState.value = DetailUiState.Error(error.toUiError())
                },
            )
        }
    }
}

class ShowDetailViewModel(
    private val mediaId: Media.MediaId.Show,
    private val getDetails: suspend (Media.MediaId) -> Result<MediaDetails> = {
        Result.failure(UnsupportedOperationException("Not implemented"))
    },
    private val listLibrary: suspend () -> Result<List<LibraryItem>> = {
        Result.success(emptyList())
    },
    private val listDownloads: suspend () -> Result<List<DownloadState>> = {
        Result.success(emptyList())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    private val savedState: Map<String, String> = mapOf("selectedSeason" to ""),
) : ViewModel() {
    private val _uiState = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading
            val detailsResult = getDetails(mediaId)
            val libraryResult = listLibrary()
            val downloadsResult = listDownloads()
            detailsResult.fold(
                onSuccess = { details ->
                    if (details is ShowDetails) {
                        val library = libraryResult.getOrDefault(emptyList())
                        val downloads = downloadsResult.getOrDefault(emptyList())
                        val restoredSeason = parseSeason(savedState["selectedSeason"].orEmpty(), details.seasons)
                        _uiState.value =
                            DetailUiState.ShowContent(
                                details = details,
                                selectedSeason = restoredSeason,
                                isFavorite = library.any {
                                    it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES
                                },
                                isDownloaded = downloads.any {
                                    it.mediaId == mediaId && it.status == DownloadStatus.COMPLETED
                                },
                            )
                    } else {
                        _uiState.value = DetailUiState.Error(UiError.NotFound("Unexpected media type"))
                    }
                },
                onFailure = { error ->
                    _uiState.value = DetailUiState.Error(error.toUiError())
                },
            )
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.update { current ->
            if (current is DetailUiState.ShowContent) current.copy(selectedSeason = seasonNumber) else current
        }
    }

    private fun parseSeason(saved: String, seasons: List<Season>): Int {
        val parsed = saved.toIntOrNull()
        if (parsed != null && seasons.any { it.seasonNumber == parsed }) return parsed
        return seasons.minOfOrNull { it.seasonNumber } ?: 1
    }
}
