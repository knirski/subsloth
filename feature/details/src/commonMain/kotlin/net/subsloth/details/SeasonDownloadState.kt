package net.subsloth.details

import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonQueueExecution

/** UI-level state of the download queue covering one season. */
enum class SeasonDownloadState {
    /** No queue exists for the season. */
    Idle,

    /** A queue exists but no transfer is running yet. */
    Queued,

    /** The queue is actively transferring episodes. */
    Downloading,

    /** Every episode in the queue downloaded successfully. */
    Completed,

    /** The queue paused or a transfer failed. */
    Failed,
}

/** Derives the [SeasonDownloadState] for [seasonNumber] from [queues]. */
fun seasonDownloadState(queues: List<SeasonDownloadQueue>, seasonNumber: Int): SeasonDownloadState =
    queues.firstOrNull { it.seasonNumber == seasonNumber }?.let { queue ->
        when (queue.execution) {
            SeasonQueueExecution.PendingConfirmation,
            SeasonQueueExecution.Queued,
            -> SeasonDownloadState.Queued

            is SeasonQueueExecution.Running -> SeasonDownloadState.Downloading

            SeasonQueueExecution.Completed -> SeasonDownloadState.Completed

            is SeasonQueueExecution.Paused,
            is SeasonQueueExecution.Failed,
            -> SeasonDownloadState.Failed
        }
    } ?: SeasonDownloadState.Idle

/** True while [this] queue can still make progress without user action. */
internal fun SeasonDownloadQueue.isActive(): Boolean = when (execution) {
    SeasonQueueExecution.PendingConfirmation,
    SeasonQueueExecution.Queued,
    is SeasonQueueExecution.Running,
    -> true

    SeasonQueueExecution.Completed,
    is SeasonQueueExecution.Paused,
    is SeasonQueueExecution.Failed,
    -> false
}
