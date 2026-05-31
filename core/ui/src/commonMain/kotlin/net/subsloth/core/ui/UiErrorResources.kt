@file:Suppress("ktlint:standard:no-wildcard-imports")

package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import net.subsloth.core.model.error.UiError
import org.jetbrains.compose.resources.stringResource
import subsloth.core.ui.generated.resources.Res
import subsloth.core.ui.generated.resources.error_auth_required
import subsloth.core.ui.generated.resources.error_not_found
import subsloth.core.ui.generated.resources.error_offline
import subsloth.core.ui.generated.resources.error_service
import subsloth.core.ui.generated.resources.error_unknown

/**
 * Stable identifier for an error message that can be resolved to a localized
 * display string via [UiErrorMessage.toDisplayString].
 */
sealed interface UiErrorMessage {
    data object AuthRequired : UiErrorMessage
    data object NotFound : UiErrorMessage
    data object ServiceError : UiErrorMessage
    data object Offline : UiErrorMessage
    data object Unknown : UiErrorMessage
}

/** Maps a domain [UiError] to a stable [UiErrorMessage] key. */
fun UiError.toUiErrorMessage(): UiErrorMessage = when (this) {
    is UiError.AuthRequired -> UiErrorMessage.AuthRequired
    is UiError.NotFound -> UiErrorMessage.NotFound
    is UiError.ServiceError -> UiErrorMessage.ServiceError
    is UiError.Offline -> UiErrorMessage.Offline
    is UiError.Unknown -> UiErrorMessage.Unknown
}

/**
 * Resolves a [UiErrorMessage] to a localized display string using Compose
 * Multiplatform resources from `:core:ui`'s [Res].
 */
@Composable
fun UiErrorMessage.toDisplayString(): String = when (this) {
    UiErrorMessage.AuthRequired -> stringResource(Res.string.error_auth_required)
    UiErrorMessage.NotFound -> stringResource(Res.string.error_not_found)
    UiErrorMessage.ServiceError -> stringResource(Res.string.error_service)
    UiErrorMessage.Offline -> stringResource(Res.string.error_offline)
    UiErrorMessage.Unknown -> stringResource(Res.string.error_unknown)
}
