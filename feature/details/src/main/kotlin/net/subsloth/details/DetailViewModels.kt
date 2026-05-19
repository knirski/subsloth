package net.subsloth.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.progress.PlaybackProgress

sealed interface DetailUiState {
    data object Loading : DetailUiState

    data class MovieContent(
        val details: MovieDetails,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val isDownloaded: Boolean = false,
        val progressFraction: Double? = null,
    ) : DetailUiState

    data class ShowContent(
        val details: ShowDetails,
        val selectedSeason: Int,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val isDownloaded: Boolean = false,
        val progressFraction: Double? = null,
    ) : DetailUiState

    data class Error(val message: String) : DetailUiState
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
            val result = getDetails(mediaId)
            result.fold(
                onSuccess = { details ->
                    if (details is MovieDetails) {
                        _uiState.value =
                            DetailUiState.MovieContent(
                                details = details,
                            )
                    } else {
                        _uiState.value = DetailUiState.Error("Unexpected media type")
                    }
                },
                onFailure = {
                    _uiState.value = DetailUiState.Error(it.message ?: "Failed to load details")
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
            val result = getDetails(mediaId)
            result.fold(
                onSuccess = { details ->
                    if (details is ShowDetails) {
                        val restoredSeason = parseSeason(savedState["selectedSeason"].orEmpty(), details.seasons)
                        _uiState.value =
                            DetailUiState.ShowContent(
                                details = details,
                                selectedSeason = restoredSeason,
                            )
                    } else {
                        _uiState.value = DetailUiState.Error("Unexpected media type")
                    }
                },
                onFailure = {
                    _uiState.value = DetailUiState.Error(it.message ?: "Failed to load details")
                },
            )
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val current = _uiState.value
        if (current is DetailUiState.ShowContent) {
            _uiState.value = current.copy(selectedSeason = seasonNumber)
        }
    }

    private fun parseSeason(saved: String, seasons: List<Season>): Int {
        val parsed = saved.toIntOrNull()
        if (parsed != null && seasons.any { it.seasonNumber == parsed }) return parsed
        return seasons.minOfOrNull { it.seasonNumber } ?: 1
    }
}
