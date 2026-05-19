package net.subsloth.core.network.media.client

import net.subsloth.core.model.error.NetworkError
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp interceptor that detects unexpected redirect, HTML, and non-JSON
 * responses before DTO parsing.
 *
 * When an unexpected response type is detected, the interceptor throws
 * [ResponseException] which can be caught by the caller and mapped
 * to a typed [NetworkError.UnexpectedResponse].
 */
class ResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val url = request.url.toRedactedString()

        // 1. Check for unexpected redirects (3xx)
        if (response.isRedirect) {
            val location = response.header("Location")
            InterceptorLogger.w(
                TAG,
                "[$url] Unexpected redirect ${response.code}" +
                    (location?.let { " -> $it" } ?: ""),
            )
            response.close()
            throw ResponseException(
                error = NetworkError.UnexpectedResponse,
                message =
                "Unexpected redirect ${response.code}" +
                    (location?.let { " -> $it" } ?: ""),
            )
        }

        // 2. Check Content-Type for HTML
        val contentType = response.header("Content-Type") ?: ""
        if (contentType.startsWith("text/html", ignoreCase = true)) {
            InterceptorLogger.w(TAG, "[$url] Expected JSON but received HTML (Content-Type: $contentType)")
            response.close()
            throw ResponseException(
                error = NetworkError.UnexpectedResponse,
                message = "Expected JSON response but received HTML (Content-Type: $contentType)",
            )
        }

        // 3. For successful responses (2xx), check that Content-Type indicates JSON.
        //    Error responses (4xx, 5xx) may carry HTML or plain-text error bodies
        //    and should pass through so Retrofit can surface them as HttpException.
        if (response.isSuccessful && contentType.isNotBlank() && !isJsonContentType(contentType)) {
            InterceptorLogger.w(TAG, "[$url] Expected JSON but received: $contentType")
            response.close()
            throw ResponseException(
                error = NetworkError.UnexpectedResponse,
                message = "Expected JSON response but received: $contentType",
            )
        }

        return response
    }

    /**
     * Returns `true` if [contentType] indicates a JSON-like response type.
     */
    private fun isJsonContentType(contentType: String): Boolean = contentType.contains("json", ignoreCase = true) ||
        contentType.contains("javascript", ignoreCase = true) ||
        contentType == "*/*"

    companion object {
        private const val TAG = "MediaRespInterceptor"
    }
}

/**
 * Exception thrown by [ResponseInterceptor] when the server returns an
 * unexpected response type.
 *
 * Callers should catch this exception and map it back to the typed
 * [NetworkError] carried in the [error] property.
 */
class ResponseException(val error: NetworkError, message: String) : Exception(message)
