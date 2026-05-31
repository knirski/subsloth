package net.subsloth.core.ui

import net.subsloth.core.model.error.UiError

/**
 * Converts [UiError] to a human-readable display string.
 *
 * Returns the string directly rather than a resource reference because:
 * 1. The generated `Res` class is `internal` per module — callers in
 *    `:feature:auth` / `:feature:details` cannot access `:core:ui`'s `Res`.
 * 2. This function is used in both `@Composable` and non-composable contexts.
 * 3. Error messages are technical English strings, not user-facing copy
 *    that requires localization.
 *
 * If localization is needed, replace this with a composable wrapper
 * or expose the resource key as a sealed type resolved at the call site.
 */
fun UiError.toDisplayStringRes(): String = when (this) {
    is UiError.AuthRequired -> "Authentication required"
    is UiError.NotFound -> "Not found"
    is UiError.ServiceError -> "Service error"
    is UiError.Offline -> "You are offline"
    is UiError.Unknown -> "An unexpected error occurred"
}
