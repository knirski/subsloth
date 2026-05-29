package net.subsloth.core.model.identifier

/**
 * Key identifying an account profile within the local credentials store.
 *
 * Profiles allow multiple account configurations on the same device.
 */
data class AccountProfileKey(
    val value: String,
)
