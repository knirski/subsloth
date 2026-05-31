package subsloth.core.network.media.client

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import subsloth.core.model.error.NetworkError

/**
 * Ktor client plugin that detects unexpected redirect, HTML, and non-JSON
 * responses before DTO parsing.
 *
 * When an unexpected response type is detected, the plugin throws
 * [ResponseValidationException] which can be caught by the caller and mapped
 * to a typed [NetworkError.UnexpectedResponse].
 *
 * Usage: install(ResponseValidationPlugin)
 */
val ResponseValidationPlugin =
    createClientPlugin("ResponseValidationPlugin") {
        onRequest { _, _ ->
            // No pre-send validation needed
        }

        onResponse { response ->
            val url =
                response.call.request.url
                    .toRedactedString()

            // 1. Check for unexpected redirects (3xx)
            if (response.status.value in 300..399) {
                val location = response.headers[HttpHeaders.Location]
                InterceptorLogger.w(
                    "ResponseValidationPlugin",
                    "[$url] Unexpected redirect ${response.status.value}" +
                        (location?.let { " -> $it" } ?: ""),
                )
                throw ResponseValidationException(
                    error = NetworkError.UnexpectedResponse,
                    message =
                    "Unexpected redirect ${response.status.value}" +
                        (location?.let { " -> $it" } ?: ""),
                )
            }

            // 2. Check Content-Type for HTML
            val contentType = response.contentType()
            if (contentType?.toString()?.startsWith("text/html", ignoreCase = true) == true) {
                InterceptorLogger.w("ResponseValidationPlugin", "[$url] Expected JSON but received HTML")
                throw ResponseValidationException(
                    error = NetworkError.UnexpectedResponse,
                    message = "Expected JSON response but received HTML",
                )
            }

            // 3. For successful responses, check Content-Type indicates JSON
            if (response.status.value in 200..299 && contentType != null) {
                val ct = contentType.toString()
                if (!ct.contains("json", ignoreCase = true) &&
                    !ct.contains("javascript", ignoreCase = true) &&
                    ct != "*/*"
                ) {
                    InterceptorLogger.w("ResponseValidationPlugin", "[$url] Expected JSON but received: $ct")
                    throw ResponseValidationException(
                        error = NetworkError.UnexpectedResponse,
                        message = "Expected JSON response but received: $ct",
                    )
                }
            }
        }
    }

/**
 * Exception thrown by [ResponseValidationPlugin] when the server returns an
 * unexpected response type.
 *
 * Callers should catch this exception and map it back to the typed
 * [NetworkError] carried in the [error] property.
 */
class ResponseValidationException(val error: NetworkError, message: String) : Exception(message)
