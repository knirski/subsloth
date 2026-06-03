package net.subsloth.library

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.progress.PlaybackProgress

@Stable
sealed interface DownloadsUiState {
    data object Loading : DownloadsUiState

    @Immutable
    data class Content(
        val active: ImmutableList<DownloadGroupItem>,
        val queuedOrPaused: ImmutableList<DownloadGroupItem>,
        val failedOrUnavailable: ImmutableList<DownloadGroupItem>,
        val completed: ImmutableList<DownloadGroupItem>,
        val seasonQueues: ImmutableList<SeasonDownloadQueue>,
    ) : DownloadsUiState
}

@Immutable
data class DownloadGroupItem(
    val state: DownloadState,
    val progressFraction: Double? = null,
)

class DownloadsViewModel(
    private val listDownloads: suspend () -> Result<ImmutableList<DownloadState>> = {
        Result.success(persistentListOf())
    },
    private val listSeasonQueues: suspend () -> Result<ImmutableList<SeasonDownloadQueue>> = {
        Result.success(persistentListOf())
    },
    private val listProgress: suspend () -> Result<List<PlaybackProgress>> = {
        Result.success(emptyList())
    },
    private val pauseDownload: suspend (String) -> DownloadCommandOutcome = { DownloadCommandOutcome.NoOp },
    private val resumeDownload: suspend (String) -> DownloadCommandOutcome = { DownloadCommandOutcome.NoOp },
    private val cancelDownload: suspend (String) -> DownloadCommandOutcome = { DownloadCommandOutcome.NoOp },
    private val retryDownload: suspend (String) -> EnqueueOutcome = { EnqueueOutcome.Queued },
    private val removeDownload: suspend (String) -> DownloadCommandOutcome = { DownloadCommandOutcome.NoOp },
    private val isOnline: () -> Boolean = { true },
) : ViewModel() {
    private val _uiState = MutableStateFlow<DownloadsUiState>(DownloadsUiState.Loading)
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            _uiState.value = DownloadsUiState.Loading
            val downloadsDeferred = async { listDownloads().getOrDefault(persistentListOf()) }
            val seasonQueuesDeferred = async { listSeasonQueues().getOrDefault(persistentListOf()) }
            val progressDeferred = async { listProgress().getOrDefault(emptyList()) }

            val downloads = downloadsDeferred.await()
            val seasonQueues = seasonQueuesDeferred.await()
            val progress = progressDeferred.await()

            val watchedIds = progress
                .filter { it.fraction > 0.9 }
                .map { it.mediaId }
                .toSet()

            val active = downloads
                .filterIsInstance<DownloadState.Active>()
                .map { DownloadGroupItem(state = it, progressFraction = it.progressPercent / 100.0) }

            val queuedOrPaused = downloads
                .filter { it is DownloadState.Queued || it is DownloadState.Paused }

            val failedOrUnavailable = downloads
                .filter { it is DownloadState.Failed || it is DownloadState.Unavailable }

            val completed = downloads
                .filterIsInstance<DownloadState.Completed>()
                .map { DownloadGroupItem(state = it, progressFraction = if (it.mediaId in watchedIds) 1.0 else null) }

            _uiState.value = DownloadsUiState.Content(
                active = active.toImmutableList(),
                queuedOrPaused = queuedOrPaused.map { DownloadGroupItem(state = it) }.toImmutableList(),
                failedOrUnavailable = failedOrUnavailable.map { DownloadGroupItem(state = it) }.toImmutableList(),
                completed = completed.toImmutableList(),
                seasonQueues = seasonQueues,
            )
        }
    }

    fun pause(localId: String) {
        viewModelScope.launch {
            pauseDownload(localId)
            loadDownloads()
        }
    }

    fun resume(localId: String) {
        viewModelScope.launch {
            resumeDownload(localId)
            loadDownloads()
        }
    }

    fun cancel(localId: String) {
        viewModelScope.launch {
            cancelDownload(localId)
            loadDownloads()
        }
    }

    fun retry(localId: String) {
        viewModelScope.launch {
            retryDownload(localId)
            loadDownloads()
        }
    }

    fun remove(localId: String) {
        viewModelScope.launch {
            removeDownload(localId)
            loadDownloads()
        }
    }

    fun deleteAllCompleted() {
        viewModelScope.launch {
            val current = _uiState.value
            if (current is DownloadsUiState.Content) {
                val jobs = current.completed.map { item ->
                    launch { removeDownload(item.state.localId.value) }
                }
                jobs.forEach { it.join() }
                loadDownloads()
            }
        }
    }

    fun deleteWatchedCompleted() {
        viewModelScope.launch {
            val progress = listProgress().getOrDefault(emptyList())
            val watchedIds = progress
                .filter { it.fraction > 0.9 }
                .map { it.mediaId }
                .toSet()

            val current = _uiState.value
            if (current is DownloadsUiState.Content) {
                val jobs = current.completed
                    .filter { it.state.mediaId in watchedIds }
                    .map { item ->
                        launch { removeDownload(item.state.localId.value) }
                    }
                jobs.forEach { it.join() }
                loadDownloads()
            }
        }
    }
}
