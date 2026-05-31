package subsloth.core.network.error

import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import subsloth.core.model.error.UiError
import subsloth.core.network.media.client.ResponseValidationException

fun Throwable.toUiError(): UiError {
    val message = this.message.orEmpty()
    return when {
        this is HttpRequestTimeoutException -> UiError.Offline(message)

        this is ResponseException ->
            when (response.status.value) {
                401 -> UiError.AuthRequired(message)
                404 -> UiError.NotFound(message)
                in 500..599 -> UiError.ServiceError(message)
                else -> UiError.Unknown(message)
            }

        this is ResponseValidationException -> UiError.ServiceError(message)

        isIoError(this) -> UiError.Offline(message)

        else -> UiError.Unknown(message)
    }
}

/**
 * Platform-agnostic check for IO/network exceptions.
 */
private fun isIoError(error: Throwable): Boolean {
    val msg = error.message?.lowercase() ?: ""
    return msg.contains("timeout") ||
        msg.contains("unreachable") ||
        msg.contains("reset") ||
        msg.contains("connection refused") ||
        error.toString().contains("IOException", ignoreCase = true)
}
