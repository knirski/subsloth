package net.subsloth.core.model.download

/** Outcome of requesting a media download to be enqueued. */
sealed interface EnqueueOutcome {
    /** Download was queued and will be processed. */
    data object Queued : EnqueueOutcome

    /** Already have a higher-quality version; enqueue skipped. */
    data object AlreadyAvailableHigherQuality : EnqueueOutcome
}
