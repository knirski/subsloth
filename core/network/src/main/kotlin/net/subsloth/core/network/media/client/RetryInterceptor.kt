package net.subsloth.core.network.media.client

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException

/**
 * OkHttp interceptor that applies bounded retry logic for retryable
 * HTTP responses.
 *
 * Retryable responses:
 * - HTTP 429 (Too Many Requests) — respects `Retry-After` header up to
 *   [maxRetryAfterSeconds].
 * - HTTP 5xx server errors.
 *
 * Non-retryable responses (pass through immediately):
 * - Success (2xx), redirect (3xx), client errors (4xx) except 429.
 *
 * The interceptor applies a maximum of [maxRetries] retries with a
 * progressive back-off: base delay × (retryCount + 1).
 *
 * If the [Retry-After] header on a 429 response exceeds [maxRetryAfterSeconds],
 * the request is NOT retried and the 429 is passed through to the caller
 * immediately so the typed error can be surfaced without further delay.
 */
class RetryInterceptor(
    private val maxRetries: Int = MAX_RETRIES,
    private val baseDelayMs: Long = BASE_DELAY_MS,
    private val maxRetryAfterSeconds: Int = MAX_RETRY_AFTER_SECONDS,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        val request = chain.request()
        val url = request.url.toRedactedString()

        while (true) {
            val response: Response
            try {
                response = chain.proceed(request)
            } catch (e: IOException) {
                if (attempt < maxRetries) {
                    InterceptorLogger.w(TAG, "[$url] IOException on attempt ${attempt + 1}/$maxRetries: ${e.message}")
                    attempt++
                    delay(attempt)
                    continue
                }
                InterceptorLogger.e(TAG, "[$url] Max retries ($maxRetries) exhausted on IOException: ${e.message}")
                throw e
            }

            if (!isRetryable(response, attempt)) {
                return response
            }

            val retryAfter = retryAfterSeconds(response)
            if (retryAfter != null && retryAfter > maxRetryAfterSeconds) {
                InterceptorLogger.w(
                    TAG,
                    "[$url] 429 with Retry-After ${retryAfter}s exceeds max $maxRetryAfterSeconds s, passing through",
                )
                return response
            }

            InterceptorLogger.w(
                TAG,
                "[$url] Retrying on ${response.code} attempt ${attempt + 1}/$maxRetries" +
                    (retryAfter?.let { " (Retry-After: ${it}s)" } ?: ""),
            )

            response.closeQuietly()
            attempt++
            delay(attempt, retryAfter)
        }
    }

    private fun isRetryable(response: Response, attempt: Int): Boolean {
        if (attempt >= maxRetries) return false

        return when (response.code) {
            HTTP_TOO_MANY_REQUESTS -> true
            in HTTP_SERVER_ERROR_MIN..HTTP_SERVER_ERROR_MAX -> true
            else -> false
        }
    }

    private fun retryAfterSeconds(response: Response): Int? = response
        .header("Retry-After")
        ?.takeIf { it.isNotBlank() }
        ?.toIntOrNull()
        ?.coerceAtLeast(1)

    private fun delay(attempt: Int, retryAfterSeconds: Int? = null) {
        val delayMs =
            retryAfterSeconds
                ?.let { it * MILLIS_PER_SECOND + BASE_DELAY_MS }
                ?: baseDelayMs * attempt
        try {
            Thread.sleep(delayMs)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    companion object {
        private const val TAG = "RetryInterceptor"
        private const val MAX_RETRIES = 2
        private const val BASE_DELAY_MS = 500L
        private const val MAX_RETRY_AFTER_SECONDS = 60
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_SERVER_ERROR_MIN = 500
        private const val HTTP_SERVER_ERROR_MAX = 599
        private const val MILLIS_PER_SECOND = 1000L
    }
}
