package net.subsloth.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
        val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
        val downloadProgressPercent: Int? = null,
        val progressFraction: Double? = null,
    ) : MovieDetailUiState {
        val isDownloaded: Boolean get() = downloadStatus == DownloadStatus.DOWNLOADED
    }

    @Immutable
    data class Error(val error: UiError) : MovieDetailUiState
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
    private val enqueueDownload: suspend (Media.MediaId, Resolution, String?) -> Result<EnqueueOutcome> = { _, _, _ ->
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
    private var downloadMonitor: Job? = null

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
                                downloadStatus = flags.downloadStatus,
                                downloadProgressPercent = flags.downloadProgressPercent,
                                progressFraction = progress?.fraction,
                            )
                        if (flags.downloadStatus == DownloadStatus.QUEUED ||
                            flags.downloadStatus == DownloadStatus.DOWNLOADING
                        ) {
                            startDownloadMonitor()
                        }
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
        if (content.downloadStatus == DownloadStatus.QUEUED ||
            content.downloadStatus == DownloadStatus.DOWNLOADING
        ) {
            return
        }
        viewModelScope.launch {
            if (content.isDownloaded) {
                removeCompletedDownload()
                refreshDownloadState()
            } else {
                val quality = QualityPolicy.selectDefault(content.details.qualities, isTvDevice)
                // Items without per-quality variants only expose a top-level
                // download URL, so enqueue a nominal resolution; the transfer
                // resolver prefers the top-level URL and ignores this label.
                val resolution = quality?.info?.resolution ?: Resolution.HD_720
                enqueueDownload(mediaId, resolution, content.details.title)
                    .onSuccess {
                        refreshDownloadState()
                        startDownloadMonitor()
                    }
                    .onFailure { error ->
                        log.w(error) { "Failed to enqueue download" }
                        setDownloadStatus(DownloadStatus.FAILED)
                    }
            }
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
        val download = downloadSnapshot(downloads, mediaId)
        return DetailFlags(
            isFavorite = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.FAVORITES },
            isWatchLater = library.any { it.mediaId == mediaId && it.collection == LibraryCollection.WATCH_LATER },
            downloadStatus = download.status,
            downloadProgressPercent = download.progressPercent,
        )
    }

    private suspend fun refreshFlags() {
        val content = _uiState.value as? MovieDetailUiState.Content ?: return
        val flags = loadFlags()
        _uiState.value = content.copy(
            isFavorite = flags.isFavorite,
            isWatchLater = flags.isWatchLater,
            downloadStatus = flags.downloadStatus,
            downloadProgressPercent = flags.downloadProgressPercent,
        )
    }

    private suspend fun refreshDownloadState() {
        val download = downloadSnapshot()
        _uiState.update { current ->
            if (current is MovieDetailUiState.Content) {
                current.copy(
                    downloadStatus = download.status,
                    downloadProgressPercent = download.progressPercent,
                )
            } else {
                current
            }
        }
    }

    private suspend fun downloadSnapshot(): DownloadSnapshot = downloadSnapshot(
        listDownloads().getOrElse { error ->
            log.w { "Failed to load downloads: $error" }
            emptyList()
        },
        mediaId,
    )

    private fun setDownloadStatus(status: DownloadStatus) {
        _uiState.update { current ->
            if (current is MovieDetailUiState.Content) {
                current.copy(downloadStatus = status, downloadProgressPercent = null)
            } else {
                current
            }
        }
    }

    /**
     * Polls the download row while the item is queued or transferring so the
     * detail button reaches "Downloaded"/"Retry" without leaving the screen.
     * Bounded: the transfer may legitimately take longer than the window.
     */
    private fun startDownloadMonitor() {
        if (downloadMonitor?.isActive == true) return
        downloadMonitor = viewModelScope.launch {
            repeat(DOWNLOAD_MONITOR_ATTEMPTS) {
                delay(DOWNLOAD_MONITOR_INTERVAL_MS)
                val download = downloadSnapshot()
                _uiState.update { current ->
                    if (current is MovieDetailUiState.Content) {
                        current.copy(
                            downloadStatus = download.status,
                            downloadProgressPercent = download.progressPercent,
                        )
                    } else {
                        current
                    }
                }
                if (download.status != DownloadStatus.QUEUED &&
                    download.status != DownloadStatus.DOWNLOADING
                ) {
                    return@launch
                }
            }
        }
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
