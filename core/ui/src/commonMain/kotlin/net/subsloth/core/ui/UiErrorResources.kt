package net.subsloth.core.ui

import net.subsloth.core.model.error.UiError

/**
 * Converts [UiError] to a human-readable display string.
 *
 * Returns the string directly rather than a resource ID so this
 * function is multiplatform-friendly.  Localization can be added
 * later via Compose Multiplatform resources if needed.
 */
fun UiError.toDisplayStringRes(): String = when (this) {
    is UiError.AuthRequired -> "Authentication required"
    is UiError.NotFound -> "Not found"
    is UiError.ServiceError -> "Service error"
    is UiError.Offline -> "You are offline"
    is UiError.Unknown -> "An unexpected error occurred"
}
