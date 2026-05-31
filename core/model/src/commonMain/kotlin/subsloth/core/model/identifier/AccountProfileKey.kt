package subsloth.core.model.identifier

import kotlin.jvm.JvmInline

/**
 * Key identifying an account profile within the local credentials store.
 *
 * Profiles allow multiple account configurations on the same device.
 */
@JvmInline
value class AccountProfileKey(val value: String)
