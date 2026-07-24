package net.subsloth.core.network.media.client

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import net.subsloth.core.model.error.NetworkError

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

            // 3. Check for 402 Payment Required (subscription limit)
            if (response.status.value == 402) {
                InterceptorLogger.w("ResponseValidationPlugin", "[$url] HTTP 402 — subscription limit reached")
                throw ResponseValidationException(
                    error = NetworkError.HttpError(402, "Subscription limit reached"),
                    message = "Subscription limit reached — verify account status",
                )
            }

            // 3b. Check for 401 Unauthorized (invalid/expired credentials).
            //
            // Ktor's `HttpClient` in this project does not set
            // `expectSuccess = true` (see `ClientFactory`), so a non-2xx
            // status alone never throws a `ResponseException` — the engine
            // just returns the response normally, and `.body<T>()` only
            // fails later (and only incidentally) if the payload doesn't
            // happen to match the requested type. Without this explicit
            // check, a 401 response is never surfaced as
            // `NetworkError.HttpError(401, ...)`, which is what callers
            // like `AndroidSessionState`/`LoginViewModel` rely on to detect
            // `AuthError.InvalidCredentials` / `UiError.AuthRequired`.
            if (response.status.value == 401) {
                InterceptorLogger.w("ResponseValidationPlugin", "[$url] HTTP 401 — unauthorized")
                throw ResponseValidationException(
                    error = NetworkError.HttpError(401, "Unauthorized"),
                    message = "HTTP 401 Unauthorized",
                )
            }

            // 4. For successful responses, check Content-Type indicates JSON
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
