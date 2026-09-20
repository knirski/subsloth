package net.subsloth.details

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
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
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.EpisodeDetails
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MediaDetails
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.ui.error.toUiError
@Stable
sealed interface EpisodeDetailUiState {
    data object Loading : EpisodeDetailUiState

    @Immutable
    data class Content(
        val details: EpisodeDetails,
        val isWatched: Boolean = false,
        val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
        val downloadProgressPercent: Int? = null,
        val progressFraction: Double? = null,
    ) : EpisodeDetailUiState {
        val isDownloaded: Boolean get() = downloadStatus == DownloadStatus.DOWNLOADED
    }

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
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    private val listDownloads: suspend () -> Result<List<DownloadState>> = {
        Result.success(emptyList())
    },
    private val enqueueDownload: suspend (Media.MediaId, Resolution, String?) -> Result<EnqueueOutcome> = { _, _, _ ->
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
    private var downloadMonitor: Job? = null

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
                        val download = downloadSnapshot()
                        _uiState.value = EpisodeDetailUiState.Content(
                            details = details,
                            isWatched = runCatching { isWatched(mediaId) }.getOrElse { error ->
                                log.w { "Failed to load watched state: $error" }
                                false
                            },
                            downloadStatus = download.status,
                            downloadProgressPercent = download.progressPercent,
                            progressFraction = resumableProgressFraction(),
                        )
                        if (download.status == DownloadStatus.QUEUED ||
                            download.status == DownloadStatus.DOWNLOADING
                        ) {
                            startDownloadMonitor()
                        }
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
                // Episodes without per-quality variants only expose a
                // top-level download URL; enqueue a nominal resolution.
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

    private suspend fun resumableProgressFraction(): Double? = listProgress()
        .getOrElse { error ->
            log.w { "Failed to load progress: $error" }
            emptyList()
        }
        .firstOrNull { it.mediaId == mediaId }
        ?.takeIf { ResumePolicy.resumablePosition(it) != null }
        ?.fraction

    private suspend fun downloadSnapshot(): DownloadSnapshot = downloadSnapshot(
        listDownloads().getOrElse { error ->
            log.w { "Failed to load downloads: $error" }
            emptyList()
        },
        mediaId,
    )

    private suspend fun refreshDownloadState() {
        val download = downloadSnapshot()
        _uiState.update { current ->
            if (current is EpisodeDetailUiState.Content) {
                current.copy(
                    downloadStatus = download.status,
                    downloadProgressPercent = download.progressPercent,
                )
            } else {
                current
            }
        }
    }

    private fun setDownloadStatus(status: DownloadStatus) {
        _uiState.update { current ->
            if (current is EpisodeDetailUiState.Content) {
                current.copy(downloadStatus = status, downloadProgressPercent = null)
            } else {
                current
            }
        }
    }

    private fun startDownloadMonitor() {
        if (downloadMonitor?.isActive == true) return
        downloadMonitor = viewModelScope.launch {
            repeat(DOWNLOAD_MONITOR_ATTEMPTS) {
                delay(DOWNLOAD_MONITOR_INTERVAL_MS)
                val download = downloadSnapshot()
                _uiState.update { current ->
                    if (current is EpisodeDetailUiState.Content) {
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
}
