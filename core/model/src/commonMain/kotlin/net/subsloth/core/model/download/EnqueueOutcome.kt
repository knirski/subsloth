package net.subsloth.core.model.download

/** Outcome of requesting a media download to be enqueued. */
enum class EnqueueOutcome {
    /** Download was queued and will be processed. */
    Queued,

    /** Already have a higher-quality version; enqueue skipped. */
    AlreadyAvailableHigherQuality,
}
