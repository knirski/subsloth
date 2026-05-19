package net.subsloth.core.network.media.client

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * OkHttp interceptor that coalesces identical in-flight requests.
 *
 * When the same URL (including query parameters) is requested concurrently,
 * the second and subsequent callers wait for the first request to complete
 * and receive a **buffered copy** of its response body. This reduces
 * redundant network calls without caching responses across time.
 *
 * This interceptor is thread-safe and uses [ConcurrentHashMap] with
 * per-key [CountDownLatch] synchronisation for minimal contention.
 *
 * Responses with a `Content-Length` exceeding [maxBodyBytes] are passed
 * through without coalescing to avoid buffering large payloads in memory.
 *
 * @param awaitTimeoutMs maximum time to wait for an in-flight request to
 *   complete before falling back to a direct call (default 30 seconds).
 * @param maxBodyBytes maximum response body size (in bytes) to buffer;
 *   larger responses bypass coalescing entirely (default 10 MiB).
 */
class RequestCoalescer(
    private val awaitTimeoutMs: Long = DEFAULT_AWAIT_TIMEOUT_MS,
    private val maxBodyBytes: Long = DEFAULT_MAX_BODY_BYTES,
) : Interceptor {
    private val inFlight = ConcurrentHashMap<String, InFlightEntry>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val cacheKey = buildCacheKey(chain.request())
        val request = chain.request()
        val redactedUrl = request.url.toRedactedString()

        // Fast path: no in-flight request for this key
        val existing = inFlight[cacheKey]
        if (existing == null) {
            val entry = InFlightEntry(awaitTimeoutMs)
            val winner = inFlight.putIfAbsent(cacheKey, entry)
            if (winner == null) {
                return handleNewRequest(cacheKey, chain, request, redactedUrl, entry)
            }
        }

        // Slow path: wait for the in-flight request to complete.
        return awaitInFlight(cacheKey, redactedUrl, chain)
    }

    private fun handleNewRequest(
        cacheKey: String,
        chain: Interceptor.Chain,
        request: okhttp3.Request,
        redactedUrl: String,
        entry: InFlightEntry,
    ): Response {
        InterceptorLogger.v(TAG, "[$redactedUrl] Starting in-flight request")
        return try {
            val response = chain.proceed(request)
            // Skip coalescing for large responses to avoid OOM
            if (response.body.contentLength() > maxBodyBytes) {
                InterceptorLogger.v(TAG, "[$redactedUrl] Response too large, skipping coalescing")
                entry.completeExceptionally(RequestCoalescerSkippedException())
                return response
            }
            val bodyBytes = response.body.bytes()
            val contentType = response.body.contentType()
            entry.complete(bodyBytes, contentType, response)
            InterceptorLogger.v(TAG, "[$redactedUrl] In-flight request completed successfully")
            // Return a buffered copy for the first caller too
            response
                .newBuilder()
                .body(bodyBytes.toResponseBody(contentType))
                .build()
        } catch (e: IOException) {
            InterceptorLogger.e(TAG, "[$redactedUrl] In-flight request failed: ${e.message}")
            entry.completeExceptionally(e)
            throw e
        } finally {
            inFlight.remove(cacheKey)
        }
    }

    private fun awaitInFlight(cacheKey: String, redactedUrl: String, chain: Interceptor.Chain): Response {
        // If the entry has already been removed (race between winner and
        // waiter threads), fall back to a direct call instead of crashing.
        InterceptorLogger.v(TAG, "[$redactedUrl] Coalescing duplicate request")
        val waiter = inFlight[cacheKey] ?: return chain.proceed(chain.request())
        return waiter.await()
    }

    private fun buildCacheKey(request: okhttp3.Request): String = request.url.toString()

    /**
     * A one-shot synchronisation entry that allows a single producer to
     * complete with a buffered response and multiple consumers to await and
     * receive their own body copy.
     */
    companion object {
        private const val TAG = "RequestCoalescer"
        private const val DEFAULT_AWAIT_TIMEOUT_MS = 30_000L
        private const val DEFAULT_MAX_BODY_BYTES = 10L * 1024 * 1024 // 10 MiB
    }

    internal class InFlightEntry(private val timeoutMs: Long) {
        private val latch = CountDownLatch(1)
        private var bodyBytes: ByteArray? = null
        private var contentType: okhttp3.MediaType? = null
        private var template: Response? = null
        private var exception: Exception? = null

        fun complete(bytes: ByteArray, type: okhttp3.MediaType?, resp: Response) {
            bodyBytes = bytes
            contentType = type
            template = resp
            latch.countDown()
        }

        fun completeExceptionally(e: Exception) {
            exception = e
            latch.countDown()
        }

        fun await(): Response {
            val completed = latch.await(timeoutMs, TimeUnit.MILLISECONDS)
            if (!completed) {
                val msg =
                    "Request coalescing timed out after ${timeoutMs}ms for: ${template?.request?.url?.toRedactedString() ?: "unknown"}"
                InterceptorLogger.e(TAG, msg)
                throw SocketTimeoutException(msg)
            }
            val ex = exception
            if (ex is RequestCoalescerSkippedException) {
                // The winner decided not to coalesce this response;
                // this waiter should never have waited.
                error("In-flight entry was skipped but a waiter was registered")
            }
            if (ex != null) throw ex
            val bytes = bodyBytes ?: error("Body not set but latch completed")
            val type = contentType
            val resp = template ?: error("Template not set but latch completed")
            return resp
                .newBuilder()
                .body(bytes.toResponseBody(type))
                .build()
        }
    }
}

/**
 * Internal sentinel thrown by [RequestCoalescer] when a response is passed
 * through without buffering (e.g. due to body size limits). Waiters should
 * never see this because the winner removes the map entry before the
 * delayed-path read can happen, but we handle it defensively anyway.
 */
internal class RequestCoalescerSkippedException : Exception()
