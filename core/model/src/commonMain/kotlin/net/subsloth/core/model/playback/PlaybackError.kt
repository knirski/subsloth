package net.subsloth.core.model.playback

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.error.DomainError

@Immutable
sealed interface PlaybackError {
    @Immutable
    data object AuthFailure : PlaybackError

    @Immutable
    data object StreamUrlExpired : PlaybackError

    @Immutable
    data class Recoverable(val cause: DomainError? = null) : PlaybackError
}
