package net.subsloth.core.network.error

import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.UiError
import net.subsloth.core.network.media.client.ResponseValidationException

fun Throwable.toUiError(): UiError {
    val message = this.message.orEmpty()
    if (this is ResponseValidationException) return UiError.ServiceError(message)
    return when (val networkError = NetworkErrorClassifier.classifyToNetwork(this)) {
        is NetworkError.Timeout -> UiError.Offline(message)

        is NetworkError.NoConnectivity -> UiError.Offline(message)

        is NetworkError.HttpError -> when (networkError.code) {
            401 -> UiError.AuthRequired(message)
            404 -> UiError.NotFound(message)
            in 500..599 -> UiError.ServiceError(message)
            else -> UiError.Unknown(message)
        }

        is NetworkError.RateLimited -> UiError.ServiceError(message)

        is NetworkError.UnexpectedResponse -> UiError.Unknown(message)
    }
}
