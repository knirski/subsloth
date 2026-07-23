package net.subsloth.core.domain

/**
 * Default values used by the login flow before any user- or
 * account-specific configuration has been persisted.
 */
object LoginDefaults {
    /** Default API base URL used when no value has been persisted yet. */
    const val DEFAULT_API_BASE_URL = "http://localhost:8080/api/v2/"
}
