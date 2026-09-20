package net.subsloth.core.media.subtitle

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException

/**
 * Loads subtitle document text for the player's Compose subtitle layer.
 *
 * Handles both remote and local sources:
 * - `http`/`https` URLs are fetched with an unauthenticated GET bounded by
 *   [timeoutMs] and [maxBytes] — subtitle URLs are ephemeral public streams,
 *   the same trust level as the video stream URL.
 * - `file` URLs are read directly from disk. Downloaded subtitle tracks are
 *   addressed with `file://` URIs by `OfflineSourceResolver`, so this path is
 *   load-bearing for offline playback.
 *
 * Every failure — a missing file, a malformed or unsupported URL, a non-2xx
 * response, a timeout, or a document larger than [maxBytes] — maps to
 * [Outcome.Failure], never a thrown exception, so a bad subtitle track cannot
 * take down playback.
 */
class SubtitleTextLoader(
    private val timeoutMs: Int = DEFAULT_TIMEOUT_MS,
    private val maxBytes: Int = DEFAULT_MAX_BYTES,
) {
    private val log = Logger.withTag("SubtitleTextLoader")

    /** Reads the subtitle document at [url], or returns a typed failure. */
    suspend fun load(url: String): Outcome<String> = withContext(Dispatchers.IO) {
        try {
            val bytes = openStream(url).use { readBounded(it) }
            Outcome.Success(bytes.decodeToString())
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: IOException) {
            failure(url, exception)
        } catch (exception: URISyntaxException) {
            failure(url, exception)
        } catch (exception: IllegalArgumentException) {
            // File(URI) rejects opaque or non-absolute file URIs; treat those
            // like any other unreadable source instead of crashing playback.
            failure(url, exception)
        }
    }

    private fun failure(url: String, exception: Exception): Outcome.Failure {
        log.e(exception) { "Subtitle load failed for $url" }
        return Outcome.Failure(NetworkError.UnexpectedResponse)
    }

    private fun openStream(url: String): InputStream {
        val uri = URI(url)
        return when (uri.scheme?.lowercase()) {
            FILE_SCHEME -> File(uri).inputStream()
            HTTP_SCHEME, HTTPS_SCHEME -> openHttpStream(uri)
            else -> throw IOException("Unsupported subtitle URL scheme: ${uri.scheme}")
        }
    }

    /**
     * Opens a remote subtitle stream with the configured timeouts. Responses
     * outside 2xx are rejected so an empty or error body cannot masquerade as
     * subtitle text (e.g. a `304 Not Modified`).
     */
    private fun openHttpStream(uri: URI): InputStream {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            throw IOException("Subtitle request returned HTTP $responseCode for $uri")
        }
        return connection.inputStream
    }

    private fun readBounded(input: InputStream): ByteArray {
        val buffer = ByteArray(maxBytes + 1)
        var read = 0
        while (read < buffer.size) {
            val count = input.read(buffer, read, buffer.size - read)
            if (count < 0) break
            read += count
        }
        if (read > maxBytes) {
            throw IOException("Subtitle document exceeds $maxBytes bytes")
        }
        return buffer.copyOf(read)
    }

    private companion object {
        const val FILE_SCHEME = "file"
        const val HTTP_SCHEME = "http"
        const val HTTPS_SCHEME = "https"

        /** Remote subtitle requests are small; keep the fetch budget bounded. */
        const val DEFAULT_TIMEOUT_MS = 10_000

        /** Subtitle documents are small text files; refuse larger ones. */
        const val DEFAULT_MAX_BYTES = 2_000_000
    }
}
