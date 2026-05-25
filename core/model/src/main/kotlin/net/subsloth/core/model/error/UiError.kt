package net.subsloth.core.model.error

sealed interface UiError {
    data class AuthRequired(
        val detail: String? = null,
    ) : UiError

    data class NotFound(
        val detail: String? = null,
    ) : UiError

    data class ServiceError(
        val detail: String? = null,
    ) : UiError

    data class Offline(
        val detail: String? = null,
    ) : UiError

    data class Unknown(
        val detail: String? = null,
    ) : UiError
}
