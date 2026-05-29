package net.subsloth.core.network.media.client

import io.ktor.http.Url

private const val DEFAULT_HTTP_PORT = 80
private const val DEFAULT_HTTPS_PORT = 443

/**
 * Returns a redacted version of this URL with query parameters stripped,
 * to avoid leaking auth tokens or other sensitive values into logcat.
 */
internal fun Url.toRedactedString(): String {
    val path = if (encodedPath == "/") "" else encodedPath
    val portSuffix =
        if (port != DEFAULT_HTTP_PORT && port != DEFAULT_HTTPS_PORT) ":$port" else ""
    return "${protocol.name}://$host$portSuffix$path"
}
