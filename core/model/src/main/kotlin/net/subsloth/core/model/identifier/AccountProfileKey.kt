package net.subsloth.core.model.identifier

import androidx.compose.runtime.Immutable

/**
 * Key identifying an account profile within the local credentials store.
 *
 * Profiles allow multiple account configurations on the same device.
 */
@Immutable
@JvmInline
value class AccountProfileKey(
    val value: String,
)
