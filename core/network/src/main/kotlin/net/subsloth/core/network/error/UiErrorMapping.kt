package net.subsloth.core.network.error

import net.subsloth.core.model.error.UiError
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

fun Throwable.toUiError(): UiError {
    val message = this.message.orEmpty()
    return when {
        isConnectivityError(this) -> UiError.Offline(message)
        message.contains("401") -> UiError.AuthRequired(message)
        message.contains("auth", ignoreCase = true) &&
            message.contains("author", ignoreCase = true) -> UiError.Unknown(message)
        message.contains("auth", ignoreCase = true) -> UiError.AuthRequired(message)
        message.contains("404") -> UiError.NotFound(message)
        isHttpServerError(message) -> UiError.ServiceError(message)
        else -> UiError.Unknown(message)
    }
}

private fun isConnectivityError(error: Throwable): Boolean = when (error) {
    is UnknownHostException,
    is SocketTimeoutException,
    is ConnectException,
    -> true
    is IOException -> error.message?.contains("timeout", ignoreCase = true) == true ||
        error.message?.contains("unreachable", ignoreCase = true) == true ||
        error.message?.contains("reset", ignoreCase = true) == true
    else -> false
}

private fun isHttpServerError(message: String): Boolean = message.contains("500") ||
    message.contains("502") ||
    message.contains("503") ||
    message.matches(".*\\b5[0-9]{2}\\b.*".toRegex()) ||
    message.contains("5") &&
    message.contains("error", ignoreCase = true)
