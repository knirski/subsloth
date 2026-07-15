package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
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
     * - Basic authentication via login/password (only when both are non-null)
     * - Response validation for unexpected redirect/HTML detection
     * - Bounded retry on 429/5xx responses
     * - Optional HTTP logging (headers only, with redacted auth headers)
     *
     * When [ClientConfig.useMock] is `true`, returns a mock client (wasmJs only).
     *
     * Pass a custom [engine] (e.g. Ktor's `MockEngine`) to intercept and verify
     * outgoing requests during tests without hitting the network.
     */
    fun create(
        login: String? = null,
        password: String? = null,
        baseUrl: String = DEFAULT_BASE_URL,
        enableHttpLogging: Boolean = false,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        if (ClientConfig.useMock && engine == null) {
            return createMockClient(login, password, baseUrl, enableHttpLogging)
        }
        return createRealClient(login, password, baseUrl, enableHttpLogging, engine)
    }

    private fun createRealClient(
        login: String?,
        password: String?,
        baseUrl: String,
        enableHttpLogging: Boolean,
        engine: HttpClientEngine? = null,
    ): HttpClient {
        val builder: HttpClientConfig<*>.() -> Unit = {
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

            if (login != null && password != null) {
                install(Auth) {
                    basic {
                        credentials {
                            BasicAuthCredentials(login, password)
                        }
                        sendWithoutRequest { true }
                    }
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
        return if (engine != null) {
            HttpClient(engine, builder)
        } else {
            HttpClient(builder)
        }
    }
}
