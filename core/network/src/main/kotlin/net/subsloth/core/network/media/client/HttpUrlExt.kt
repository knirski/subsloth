package net.subsloth.core.network.media.client

import okhttp3.HttpUrl

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

/**
 * Returns a redacted version of this URL with query parameters stripped,
 * to avoid leaking auth tokens or other sensitive values into logcat.
 */
internal fun HttpUrl.toRedactedString(): String {
    val path = if (encodedPath == "/") "" else encodedPath
    return "$scheme://$host${if (port != DEFAULT_HTTP_PORT && port != DEFAULT_HTTPS_PORT) ":$port" else ""}$path"
}
