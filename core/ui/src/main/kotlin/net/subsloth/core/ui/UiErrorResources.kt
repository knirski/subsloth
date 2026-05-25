package net.subsloth.core.ui

import androidx.annotation.StringRes
import net.subsloth.core.model.error.UiError

@StringRes
fun UiError.toDisplayStringRes(): Int = when (this) {
    is UiError.AuthRequired -> R.string.error_auth_required
    is UiError.NotFound -> R.string.error_not_found
    is UiError.ServiceError -> R.string.error_service
    is UiError.Offline -> R.string.error_offline
    is UiError.Unknown -> R.string.error_unknown
}
