package net.subsloth.core.domain.policy

import net.subsloth.core.domain.LoginDefaults

/**
 * Pure policy for resolving the effective API base URL from the raw stored
 * preference and a platform-provided build-time override (Android's
 * `BuildConfig.SUBSLOTH_API_BASE_URL`, desktop/web's
 * `SUBSLOTH_API_BASE_URL` environment value).
 *
 * Precedence is decided by preference *presence*, not by comparing values:
 * a deliberately saved non-blank URL — including
 * [LoginDefaults.DEFAULT_API_BASE_URL] — wins over the build-time override.
 * Only when nothing usable has been saved does the override apply, and only
 * when neither exists does the hard-coded default apply. Blank or
 * whitespace-only values are treated as absent so an empty URL can never
 * reach `LoginViewModel` or `ClientFactory`.
 *
 * All functions are pure and have no platform dependencies.
 */
object ApiBaseUrlPolicy {
    /**
     * Resolves the API base URL from the raw [stored] preference value and
     * the [configured] build-time override, both of which may be `null`,
     * absent, or blank. The resolved value is normalized with [normalize].
     */
    fun resolve(stored: String?, configured: String?): String = normalize(
        when {
            !stored.isNullOrBlank() -> stored
            !configured.isNullOrBlank() -> configured
            else -> LoginDefaults.DEFAULT_API_BASE_URL
        },
    )

    /**
     * Normalizes [url] for use as a base URL by trimming surrounding
     * whitespace and ensuring a single trailing `/`.
     *
     * Ktor resolves relative request paths against a base URL by replacing
     * the base's last path segment when the base does not end in `/` — a
     * base like `https://host/api/v2` therefore turns `movies` into
     * `/api/movies` instead of `/api/v2/movies`. Requiring the trailing
     * slash keeps the whole base path.
     *
     * A blank [url] falls back to [LoginDefaults.DEFAULT_API_BASE_URL].
     */
    fun normalize(url: String?): String {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return LoginDefaults.DEFAULT_API_BASE_URL
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }
}
