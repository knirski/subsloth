package net.subsloth.core.model.error

sealed interface UiError {
    val detail: String?

    data class AuthRequired(
        override val detail: String? = null,
    ) : UiError

    data class NotFound(
        override val detail: String? = null,
    ) : UiError

    data class ServiceError(
        override val detail: String? = null,
    ) : UiError

    data class Offline(
        override val detail: String? = null,
    ) : UiError

    data class Unknown(
        override val detail: String? = null,
    ) : UiError

    companion object {
        fun fromThrowable(error: Throwable): UiError {
            val message = error.message
            return when {
                message?.contains("401", ignoreCase = true) == true ||
                    message?.contains("auth", ignoreCase = true) == true ->
                    AuthRequired(message)
                else -> Unknown(message)
            }
        }
    }
}
