package net.subsloth.core.media.download

/**
 * Redacts absolute local file paths from logs and error messages.
 *
 * Download paths contain app-private directory structures that should not
 * appear in crash reports, analytics, or UI error messages.
 */
object PathRedactor {
    /**
     * Replaces a local file path with a redacted placeholder.
     *
     * @param path The absolute file path to redact, or null.
     * @return "[redacted-local-path]" if the path is non-blank, empty string otherwise.
     */
    fun redact(path: String?): String = path
        ?.takeIf { it.isNotBlank() }
        ?.let { "[redacted-local-path]" }
        .orEmpty()
}
