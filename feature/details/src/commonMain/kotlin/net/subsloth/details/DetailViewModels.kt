package net.subsloth.details

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.EpisodeDetails
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.ui.error.toUiError

@Stable
sealed interface MovieDetailUiState {
    data object Loading : MovieDetailUiState

    @Immutable
    data class Content(
        val details: MovieDetails,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val isDownloaded: Boolean = false,
        val progressFraction: Double? = null,
    ) : MovieDetailUiState

    @Immutable
    data class Error(val error: UiError) : MovieDetailUiState
}

@Stable
sealed interface ShowDetailUiState {
    data object Loading : ShowDetailUiState

    @Immutable
    data class Content(
        val details: ShowDetails,
        val selectedSeason: Int,
        val isFavorite: Boolean = false,
        val isWatchLater: Boolean = false,
        val isDownloaded: Boolean = false,
        val progressFraction: Double? = null,
        val watchedEpisodeIds: ImmutableList<Int> = persistentListOf(),
    ) : ShowDetailUiState

    @Immutable
    data class Error(val error: UiError) : ShowDetailUiState
}

class MovieDetailViewModel(
    private val mediaId: Media.MediaId.Movie,
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
) : ViewModel() {
    private val _uiState = MutableStateFlow<MovieDetailUiState>(MovieDetailUiState.Loading)
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = MovieDetailUiState.Loading
            when (val detailsResult = getDetails(mediaId)) {
                is Outcome.Success -> {
                    val details = detailsResult.value
                    if (details is MovieDetails) {
                        val library = when (val lib = listLibrary()) {
                            is Outcome.Success -> lib.value
                            is Outcome.Failure -> emptyList()
                        }
                        val downloads = listDownloads().getOrDefault(emptyList())
                        _uiState.value =
                            MovieDetailUiState.Content(
                                details = details,
                                isFavorite = library.any {
                                    it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES
                                },
                                isDownloaded = downloads.any {
                                    it.mediaId == mediaId && it is DownloadState.Completed
                                },
                            )
                    } else {
                        _uiState.value = MovieDetailUiState.Error(UiError.NotFound("Unexpected media type"))
                    }
                }

                is Outcome.Failure -> {
                    _uiState.value = MovieDetailUiState.Error(detailsResult.error.toUiError())
                }
            }
        }
    }
}

class ShowDetailViewModel(
    private val mediaId: Media.MediaId.Show,
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
    private val listWatchedIds: suspend () -> Set<String> = { emptySet() },
    private val savedState: Map<String, String> = mapOf("selectedSeason" to ""),
) : ViewModel() {
    private val _uiState = MutableStateFlow<ShowDetailUiState>(ShowDetailUiState.Loading)
    val uiState: StateFlow<ShowDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = ShowDetailUiState.Loading
            when (val detailsResult = getDetails(mediaId)) {
                is Outcome.Success -> {
                    val details = detailsResult.value
                    if (details is ShowDetails) {
                        val watchedIds = runCatching { listWatchedIds() }.getOrDefault(emptySet())
                        val library = when (val lib = listLibrary()) {
                            is Outcome.Success -> lib.value
                            is Outcome.Failure -> emptyList()
                        }
                        val downloads = listDownloads().getOrDefault(emptyList())
                        val restoredSeason = parseSeason(savedState["selectedSeason"].orEmpty(), details.seasons)
                        _uiState.value =
                            ShowDetailUiState.Content(
                                details = details,
                                selectedSeason = restoredSeason,
                                isFavorite = library.any {
                                    it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES
                                },
                                isDownloaded = downloads.any {
                                    it.mediaId == mediaId && it is DownloadState.Completed
                                },
                                watchedEpisodeIds = details.seasons
                                    .flatMap { season -> season.episodes }
                                    .filter { episode -> watchedIds.contains(episode.id.value.toString()) }
                                    .map { episode -> episode.id.value }
                                    .toImmutableList(),
                            )
                    } else {
                        _uiState.value = ShowDetailUiState.Error(UiError.NotFound("Unexpected media type"))
                    }
                }

                is Outcome.Failure -> {
                    _uiState.value = ShowDetailUiState.Error(detailsResult.error.toUiError())
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        _uiState.update { current ->
            if (current is ShowDetailUiState.Content) current.copy(selectedSeason = seasonNumber) else current
        }
    }

    private fun parseSeason(saved: String, seasons: List<Season>): Int {
        val parsed = saved.toIntOrNull()
        if (parsed != null && seasons.any { it.seasonNumber == parsed }) return parsed
        return seasons.minOfOrNull { it.seasonNumber } ?: 1
    }
}

@Stable
sealed interface EpisodeDetailUiState {
    data object Loading : EpisodeDetailUiState

    @Immutable
    data class Content(val details: EpisodeDetails, val isWatched: Boolean = false) : EpisodeDetailUiState

    @Immutable
    data class Error(val error: UiError) : EpisodeDetailUiState
}

/**
 * Episode detail page (`EpisodeDetailKey`): fetches the episode via the
 * same detail pipeline as movies/shows (`GET /episodes/{id}` mapped to
 * [EpisodeDetails]).
 */
class EpisodeDetailViewModel(
    private val mediaId: Media.MediaId.Episode,
    private val getDetails: suspend (Media.MediaId) -> Outcome<MediaDetails> = {
        Outcome.Failure(DecodeError.SerializationFailed)
    },
    private val isWatched: suspend (Media.MediaId) -> Boolean = { false },
) : ViewModel() {
    private val _uiState = MutableStateFlow<EpisodeDetailUiState>(EpisodeDetailUiState.Loading)
    val uiState: StateFlow<EpisodeDetailUiState> = _uiState.asStateFlow()

    init {
        loadDetails()
    }

    private fun loadDetails() {
        viewModelScope.launch {
            _uiState.value = EpisodeDetailUiState.Loading
            when (val detailsResult = getDetails(mediaId)) {
                is Outcome.Success -> {
                    val details = detailsResult.value
                    if (details is EpisodeDetails) {
                        _uiState.value = EpisodeDetailUiState.Content(
                            details = details,
                            isWatched = runCatching { isWatched(mediaId) }.getOrDefault(false),
                        )
                    } else {
                        _uiState.value = EpisodeDetailUiState.Error(UiError.NotFound("Unexpected media type"))
                    }
                }

                is Outcome.Failure -> {
                    _uiState.value = EpisodeDetailUiState.Error(detailsResult.error.toUiError())
                }
            }
        }
    }
}
