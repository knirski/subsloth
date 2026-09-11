package net.subsloth.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.subsloth.core.domain.policy.QualityPolicy
import net.subsloth.core.domain.policy.ResumePolicy
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.EpisodeDetails
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.ui.error.toUiError
import kotlin.time.Clock

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
        val progressFraction: Double? = null,
        val watchedEpisodeIds: ImmutableList<Int> = persistentListOf(),
        val seasonQueues: ImmutableList<SeasonDownloadQueue> = persistentListOf(),
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
    private val addToLibrary: suspend (LibraryItem) -> Outcome<Unit> = {
        Outcome.Success(Unit)
    },
    private val removeFromLibrary: suspend (Media.MediaId) -> Outcome<Unit> = {
        Outcome.Success(Unit)
    },
    private val enqueueDownload: suspend (Media.MediaId, Resolution) -> Result<EnqueueOutcome> = { _, _ ->
        Result.success(EnqueueOutcome.Queued)
    },
    private val removeDownload: suspend (LocalMediaIdentifier) -> Result<DownloadCommandOutcome> = {
        Result.success(DownloadCommandOutcome.NoOp)
    },
    private val isTvDevice: Boolean = false,
    private val clock: Clock = Clock.System,
) : ViewModel() {
    private val log = Logger.withTag("MovieDetailViewModel")

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
                        val flags = loadFlags()
                        val progress = progressFor(mediaId)
                        _uiState.value =
                            MovieDetailUiState.Content(
                                details = details,
                                isFavorite = flags.isFavorite,
                                isWatchLater = flags.isWatchLater,
                                isDownloaded = flags.isDownloaded,
                                progressFraction = progress?.fraction,
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

    fun toggleFavorite() {
        val content = _uiState.value as? MovieDetailUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isFavorite) {
                removeFromLibrary(mediaId)
            } else {
                addToLibrary(libraryItem(LibraryCollection.FAVORITES))
            }
            if (result is Outcome.Failure) {
                log.w { "Failed to toggle favorite: ${result.error}" }
            }
            refreshFlags()
        }
    }

    fun toggleWatchLater() {
        val content = _uiState.value as? MovieDetailUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isWatchLater) {
                removeFromLibrary(mediaId)
            } else {
                addToLibrary(libraryItem(LibraryCollection.WATCH_LATER))
            }
            if (result is Outcome.Failure) {
                log.w { "Failed to toggle watch later: ${result.error}" }
            }
            refreshFlags()
        }
    }

    fun toggleDownload() {
        val content = _uiState.value as? MovieDetailUiState.Content ?: return
        viewModelScope.launch {
            if (content.isDownloaded) {
                removeCompletedDownload()
            } else {
                val quality = QualityPolicy.selectDefault(content.details.qualities, isTvDevice)
                // Items without per-quality variants only expose a top-level
                // download URL, so enqueue a nominal resolution; the transfer
                // resolver prefers the top-level URL and ignores this label.
                val resolution = quality?.info?.resolution ?: Resolution.HD_720
                enqueueDownload(mediaId, resolution)
                    .onFailure { error -> log.w(error) { "Failed to enqueue download" } }
            }
            refreshFlags()
        }
    }

    private suspend fun loadFlags(): DetailFlags {
        val library = when (val lib = listLibrary()) {
            is Outcome.Success -> lib.value

            is Outcome.Failure -> {
                log.w { "Failed to load library: ${lib.error}" }
                emptyList()
            }
        }
        val downloads = listDownloads().getOrElse { error ->
            log.w { "Failed to load downloads: $error" }
            emptyList()
        }
        return DetailFlags(
            isFavorite = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES },
            isWatchLater = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.WATCH_LATER },
            isDownloaded = downloads.any { it.mediaId == mediaId && it is DownloadState.Completed },
        )
    }

    private suspend fun refreshFlags() {
        val content = _uiState.value as? MovieDetailUiState.Content ?: return
        val flags = loadFlags()
        _uiState.value = content.copy(
            isFavorite = flags.isFavorite,
            isWatchLater = flags.isWatchLater,
            isDownloaded = flags.isDownloaded,
        )
    }

    private suspend fun removeCompletedDownload() {
        val completed = listDownloads().getOrElse { error ->
            log.w { "Failed to load downloads: $error" }
            emptyList()
        }.firstOrNull { it.mediaId == mediaId && it is DownloadState.Completed }
        if (completed == null) {
            log.w { "No completed download to remove for $mediaId" }
        } else {
            removeDownload(completed.localId).onFailure { error ->
                log.w(error) { "Failed to remove download" }
            }
        }
    }

    private fun libraryItem(collection: LibraryCollection): LibraryItem = LibraryItem(
        mediaId = mediaId,
        collection = collection,
        addedAtEpochSeconds = clock.now(),
        sortOrder = 0,
    )

    /**
     * The stored progress playback would actually resume from for
     * [mediaId], or `null` when there is none or [ResumePolicy] considers
     * it non-resumable (below threshold / already finished). Keeps the
     * detail Play label in sync with the player's resume decision.
     */
    private suspend fun progressFor(mediaId: Media.MediaId): PlaybackProgress? = listProgress()
        .getOrElse { error ->
            log.w { "Failed to load progress: $error" }
            emptyList()
        }
        .firstOrNull { it.mediaId == mediaId }
        ?.takeIf { ResumePolicy.resumablePosition(it) != null }
}

class ShowDetailViewModel(
    private val mediaId: Media.MediaId.Show,
    private val getDetails: suspend (Media.MediaId) -> Outcome<MediaDetails> = {
        Outcome.Failure(DecodeError.SerializationFailed)
    },
    private val listLibrary: suspend () -> Outcome<List<LibraryItem>> = {
        Outcome.Success(emptyList())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    private val listWatchedIds: suspend () -> Set<String> = { emptySet() },
    private val addToLibrary: suspend (LibraryItem) -> Outcome<Unit> = {
        Outcome.Success(Unit)
    },
    private val removeFromLibrary: suspend (Media.MediaId) -> Outcome<Unit> = {
        Outcome.Success(Unit)
    },
    private val listSeasonQueues: suspend () -> Result<List<SeasonDownloadQueue>> = {
        Result.success(emptyList())
    },
    private val startSeasonDownload: suspend (Int, ImmutableList<Episode>) -> Unit = { _, _ -> },
    private val clock: Clock = Clock.System,
    private val savedState: Map<String, String> = mapOf("selectedSeason" to ""),
) : ViewModel() {
    private val log = Logger.withTag("ShowDetailViewModel")

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
                        val watchedIds = runCatching { listWatchedIds() }.getOrElse { error ->
                            log.w { "Failed to load watched ids: $error" }
                            emptySet()
                        }
                        val flags = loadFlags()
                        val progress = resumableShowProgress(details)
                        val seasonQueues = loadSeasonQueues()
                        val restoredSeason = parseSeason(savedState["selectedSeason"].orEmpty(), details.seasons)
                        _uiState.value =
                            ShowDetailUiState.Content(
                                details = details,
                                selectedSeason = restoredSeason,
                                isFavorite = flags.isFavorite,
                                isWatchLater = flags.isWatchLater,
                                progressFraction = progress?.fraction,
                                watchedEpisodeIds = details.seasons
                                    .flatMap { season -> season.episodes }
                                    .filter { episode -> watchedIds.contains(episode.id.value.toString()) }
                                    .map { episode -> episode.id.value }
                                    .toImmutableList(),
                                seasonQueues = seasonQueues.toImmutableList(),
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

    /**
     * Queues every episode of the selected season for download. No-op while
     * a queue for the season is already active or when the season has no
     * episodes.
     */
    fun downloadSeason() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        if (seasonDownloadState(content.seasonQueues, content.selectedSeason) != SeasonDownloadState.Idle) return
        val season = content.details.seasons.firstOrNull { it.seasonNumber == content.selectedSeason } ?: return
        if (season.episodes.isEmpty()) return
        viewModelScope.launch {
            startSeasonDownload(content.selectedSeason, season.episodes.toImmutableList())
            refreshSeasonQueues()
        }
    }

    private suspend fun loadSeasonQueues(): List<SeasonDownloadQueue> = listSeasonQueues()
        .getOrElse { error ->
            log.w { "Failed to load season queues: $error" }
            emptyList()
        }

    private suspend fun refreshSeasonQueues() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        _uiState.value = content.copy(seasonQueues = loadSeasonQueues().toImmutableList())
    }

    fun toggleFavorite() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isFavorite) {
                removeFromLibrary(mediaId)
            } else {
                addToLibrary(libraryItem(LibraryCollection.FAVORITES))
            }
            if (result is Outcome.Failure) {
                log.w { "Failed to toggle favorite: ${result.error}" }
            }
            refreshFlags()
        }
    }

    fun toggleWatchLater() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        viewModelScope.launch {
            val result = if (content.isWatchLater) {
                removeFromLibrary(mediaId)
            } else {
                addToLibrary(libraryItem(LibraryCollection.WATCH_LATER))
            }
            if (result is Outcome.Failure) {
                log.w { "Failed to toggle watch later: ${result.error}" }
            }
            refreshFlags()
        }
    }

    private suspend fun loadFlags(): DetailFlags {
        val library = when (val lib = listLibrary()) {
            is Outcome.Success -> lib.value

            is Outcome.Failure -> {
                log.w { "Failed to load library: ${lib.error}" }
                emptyList()
            }
        }
        return DetailFlags(
            isFavorite = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES },
            isWatchLater = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.WATCH_LATER },
        )
    }

    private suspend fun refreshFlags() {
        val content = _uiState.value as? ShowDetailUiState.Content ?: return
        val flags = loadFlags()
        _uiState.value = content.copy(
            isFavorite = flags.isFavorite,
            isWatchLater = flags.isWatchLater,
        )
    }

    private fun libraryItem(collection: LibraryCollection): LibraryItem = LibraryItem(
        mediaId = mediaId,
        collection = collection,
        addedAtEpochSeconds = clock.now(),
        sortOrder = 0,
    )

    /**
     * The stored progress playback would resume from for this show: the
     * most recently updated row among the show itself and its episodes,
     * filtered through [ResumePolicy] so the detail Play label matches the
     * player's resume decision.
     */
    private suspend fun resumableShowProgress(details: ShowDetails): PlaybackProgress? {
        val episodeIds = details.seasons.flatMap { season -> season.episodes }.map { episode -> episode.id }.toSet()
        return listProgress()
            .getOrElse { error ->
                log.w { "Failed to load progress: $error" }
                emptyList()
            }
            .filter { progress ->
                progress.mediaId == mediaId ||
                    (progress.mediaId as? Media.MediaId.Episode)?.value in episodeIds
            }
            .maxByOrNull { it.lastUpdatedEpochSeconds }
            ?.takeIf { ResumePolicy.resumablePosition(it) != null }
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
    data class Content(
        val details: EpisodeDetails,
        val isWatched: Boolean = false,
        val isDownloaded: Boolean = false,
    ) : EpisodeDetailUiState

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
    private val listDownloads: suspend () -> Result<List<DownloadState>> = {
        Result.success(emptyList())
    },
    private val enqueueDownload: suspend (Media.MediaId, Resolution) -> Result<EnqueueOutcome> = { _, _ ->
        Result.success(EnqueueOutcome.Queued)
    },
    private val removeDownload: suspend (LocalMediaIdentifier) -> Result<DownloadCommandOutcome> = {
        Result.success(DownloadCommandOutcome.NoOp)
    },
    private val isTvDevice: Boolean = false,
) : ViewModel() {
    private val log = Logger.withTag("EpisodeDetailViewModel")

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
                            isWatched = runCatching { isWatched(mediaId) }.getOrElse { error ->
                                log.w { "Failed to load watched state: $error" }
                                false
                            },
                            isDownloaded = isDownloaded(),
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

    fun toggleDownload() {
        val content = _uiState.value as? EpisodeDetailUiState.Content ?: return
        viewModelScope.launch {
            if (content.isDownloaded) {
                removeCompletedDownload()
            } else {
                val quality = QualityPolicy.selectDefault(content.details.qualities, isTvDevice)
                // Episodes without per-quality variants only expose a
                // top-level download URL; enqueue a nominal resolution.
                val resolution = quality?.info?.resolution ?: Resolution.HD_720
                enqueueDownload(mediaId, resolution)
                    .onFailure { error -> log.w(error) { "Failed to enqueue download" } }
            }
            refreshDownloadFlag()
        }
    }

    private suspend fun isDownloaded(): Boolean = listDownloads().getOrElse { error ->
        log.w { "Failed to load downloads: $error" }
        emptyList()
    }.any { it.mediaId == mediaId && it is DownloadState.Completed }

    private suspend fun refreshDownloadFlag() {
        val content = _uiState.value as? EpisodeDetailUiState.Content ?: return
        _uiState.value = content.copy(isDownloaded = isDownloaded())
    }

    private suspend fun removeCompletedDownload() {
        val completed = listDownloads().getOrElse { error ->
            log.w { "Failed to load downloads: $error" }
            emptyList()
        }.firstOrNull { it.mediaId == mediaId && it is DownloadState.Completed }
        if (completed == null) {
            log.w { "No completed download to remove for $mediaId" }
        } else {
            removeDownload(completed.localId).onFailure { error ->
                log.w(error) { "Failed to remove download" }
            }
        }
    }
}

private data class DetailFlags(
    val isFavorite: Boolean = false,
    val isWatchLater: Boolean = false,
    val isDownloaded: Boolean = false,
)
