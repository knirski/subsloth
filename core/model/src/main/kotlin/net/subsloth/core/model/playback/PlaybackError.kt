package net.subsloth.core.model.playback

import androidx.compose.runtime.Immutable

/**
 * Categorises a playback error to drive the correct recovery path.
 *
 * - [Recoverable]: transient or quality-related errors that allow retry.
 * - [AuthFailure]: HTTP 401 or equivalent — stop online playback, save
 *   progress, and route to auth repair.
 * - [StreamUrlExpired]: the signed stream URL has expired and needs refresh.
 */
@Immutable
sealed interface PlaybackError {
    val message: String

    /** A transient or quality-related error that allows retry or fallback. */
    @Immutable
    data class Recoverable(
        override val message: String,
    ) : PlaybackError

    /** Authentication failure — stop online playback and route to auth repair. */
    @Immutable
    data class AuthFailure(
        override val message: String,
    ) : PlaybackError

    /** Stream URL expired — may attempt a bounded refresh. */
    @Immutable
    data class StreamUrlExpired(
        override val message: String,
    ) : PlaybackError
}
