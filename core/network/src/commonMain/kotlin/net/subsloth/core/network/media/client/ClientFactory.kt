package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ClientFactory {
    /** Default API base URL. Override via [create]'s [baseUrl] parameter. */
    private const val DEFAULT_BASE_URL = "http://localhost:8080/api/v2/"

    /**
     * Creates an [HttpClient] configured with:
     * - Kodi-compatible request identity (User-Agent, Accept headers)
     * - Basic authentication via login/password
     * - Response validation for unexpected redirect/HTML detection
     * - Bounded retry on 429/5xx responses
     * - Optional HTTP logging (headers only, with redacted auth headers)
     */
    fun create(
        login: String,
        password: String,
        baseUrl: String = DEFAULT_BASE_URL,
        enableHttpLogging: Boolean = false,
    ): HttpClient =
        HttpClient {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }

            install(ResponseValidationPlugin)

            install(Auth) {
                basic {
                    credentials {
                        BasicAuthCredentials(login, password)
                    }
                    sendWithoutRequest { true }
                }
            }

            install(HttpRequestRetry) {
                maxRetries = 2
                retryOnException(retryOnTimeout = true)
                retryIf { _, response -> response.status.value == 429 || response.status.value in 500..599 }
                delayMillis { attempt -> (attempt + 1) * 500L }
            }

            install(Logging) {
                level = if (enableHttpLogging) LogLevel.HEADERS else LogLevel.NONE
            }

            defaultRequest {
                url(baseUrl)
                header(HttpHeaders.UserAgent, "Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
                header(HttpHeaders.Accept, "application/json, */*")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.5")
            }
        }
}
