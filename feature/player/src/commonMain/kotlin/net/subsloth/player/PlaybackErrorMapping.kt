package net.subsloth.player

import net.subsloth.core.domain.policy.PlaybackErrorClassifier
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.playback.PlaybackError

/**
 * Classifies the player bridge's opaque error message.
 *
 * The bridge is not coupled to the network shell, so an HTTP status code is
 * recovered from the message when one is present — 401 maps to
 * [PlaybackError.AuthFailure] and 403 to [PlaybackError.StreamUrlExpired] via
 * [PlaybackErrorClassifier]. Messages without a status code are treated as
 * recoverable playback errors.
 */
internal fun classifyPlaybackErrorMessage(message: String): PlaybackError {
    val code = HTTP_STATUS_REGEX.find(message)?.value?.toIntOrNull()
    val domainError = if (code != null && code in 400..599) {
        NetworkError.HttpError(code, message)
    } else {
        DecodeError.SerializationFailed
    }
    return PlaybackErrorClassifier.classify(domainError)
}

// Matches an HTTP 4xx/5xx status inside an arbitrary player error message.
private val HTTP_STATUS_REGEX = Regex("""\b([45]\d{2})\b""")
