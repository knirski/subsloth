package net.subsloth.core.model.download

sealed interface EnqueueOutcome {
    data object Queued : EnqueueOutcome

    data object AlreadyAvailableHigherQuality : EnqueueOutcome
}
