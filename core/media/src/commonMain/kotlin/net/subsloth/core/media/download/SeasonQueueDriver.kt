package net.subsloth.core.media.download

import kotlinx.coroutines.delay
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.media.Media

/**
 * Drives a confirmed [SeasonQueueController] queue to completion.
 *
 * [SeasonQueueController.executeNext] only enqueues the next pending
 * episode; this driver waits for that episode's download to reach a
 * terminal state before marking the queue item and advancing, keeping the
 * queue statuses in sync with the real transfer.
 *
 * Transfers run on the JVM platforms through `DownloadTransferCoordinator`.
 * The web tier has no byte transfer, so its containers do not launch this
 * driver — the queue stays confirmed with pending items there.
 */
class SeasonQueueDriver(
    private val controller: SeasonQueueController,
    private val downloadsPort: DownloadsPort,
    private val pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
) {
    /** Runs [queueId] until it completes, fails, or pauses. */
    suspend fun drive(queueId: QueueId) {
        while (true) {
            when (val execution = controller.executeNext(queueId)) {
                is SeasonQueueExecution.Running -> {
                    when (awaitEpisodeOutcome(execution.activeItem)) {
                        EpisodeOutcome.Completed -> controller.markItemCompleted(queueId, execution.activeItem)

                        EpisodeOutcome.Paused -> {
                            controller.pauseQueue(queueId, DownloadFailureReason.NeedsWifi)
                            return
                        }

                        EpisodeOutcome.Failed -> {
                            controller.markItemFailed(
                                queueId,
                                execution.activeItem,
                                DownloadFailureReason.DownloadFailed,
                            )
                            return
                        }
                    }
                }

                SeasonQueueExecution.PendingConfirmation,
                SeasonQueueExecution.Queued,
                SeasonQueueExecution.Completed,
                is SeasonQueueExecution.Paused,
                is SeasonQueueExecution.Failed,
                -> return
            }
        }
    }

    private suspend fun awaitEpisodeOutcome(mediaId: Media.MediaId): EpisodeOutcome {
        while (true) {
            val state = downloadsPort.listDownloads().getOrNull()
                ?.firstOrNull { it.mediaId == mediaId }
            when (state) {
                is DownloadState.Completed -> return EpisodeOutcome.Completed
                is DownloadState.Paused -> return EpisodeOutcome.Paused
                is DownloadState.Failed, is DownloadState.Unavailable -> return EpisodeOutcome.Failed
                else -> delay(pollIntervalMs)
            }
        }
    }

    private enum class EpisodeOutcome { Completed, Paused, Failed }

    private companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 1_000L
    }
}
