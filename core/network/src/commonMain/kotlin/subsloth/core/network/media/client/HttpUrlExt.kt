package subsloth.core.network.media.client

import io.ktor.http.Url

/**
 * Returns a redacted version of this URL with query parameters stripped,
 * to avoid leaking auth tokens or other sensitive values into logcat.
 */
internal fun Url.toRedactedString(): String {
    val path = if (encodedPath == "/") "" else encodedPath
    val portSuffix = if (port != protocol.defaultPort) ":$port" else ""
    return "${protocol.name}://$host$portSuffix$path"
}
