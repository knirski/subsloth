package net.subsloth.core.model.playback

import net.subsloth.core.model.error.DomainError

sealed interface PlaybackError {
    data object AuthFailure : PlaybackError

    data object StreamUrlExpired : PlaybackError

    data class Recoverable(val cause: DomainError? = null) : PlaybackError
}
