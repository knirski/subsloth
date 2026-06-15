package net.subsloth.core.domain.policy

import net.subsloth.core.model.error.DomainError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.playback.PlaybackError

/**
 * Pure-domain classifier that maps a [DomainError] (typically produced
 * by the network shell) into a [PlaybackError] suitable for the player
 * ViewModel.
 *
 * Centralises the rules in a pure function so the ViewModel can call
 * a typed classifier instead of string-matching on
 * `Throwable.message` (which is fragile and not exhaustive).
 *
 * The mapping:
 * - `NetworkError.HttpError(401)` → [PlaybackError.AuthFailure]
 * - `NetworkError.HttpError(403)` → [PlaybackError.StreamUrlExpired]
 * - everything else → [PlaybackError.Recoverable] carrying the typed
 *   [DomainError] as `cause` so the UI can display a specific message.
 */
object PlaybackErrorClassifier {
    fun classify(error: DomainError): PlaybackError = when (error) {
        is NetworkError.HttpError -> when (error.code) {
            401 -> PlaybackError.AuthFailure
            403 -> PlaybackError.StreamUrlExpired
            else -> PlaybackError.Recoverable(error)
        }

        else -> PlaybackError.Recoverable(error)
    }
}
