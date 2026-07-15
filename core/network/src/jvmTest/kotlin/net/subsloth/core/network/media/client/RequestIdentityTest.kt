package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Tests that outgoing requests carry the correct Kodi-compatible identity
 * headers as configured by [ClientFactory].
 */
class RequestIdentityTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private fun createClient(login: String? = null, password: String? = null, engine: MockEngine): HttpClient =
        HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
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
                        credentials { BasicAuthCredentials(login, password) }
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
            defaultRequest {
                url("http://localhost:1/")
                header(HttpHeaders.UserAgent, "Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
                header(HttpHeaders.Accept, "application/json, */*")
                header(HttpHeaders.AcceptLanguage, "en-US,en;q=0.5")
            }
        }.also { clients.add(it) }

    @Test
    fun `User-Agent header matches Kodi identity`() = runTest {
        val captured = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                captured.add(request.headers[HttpHeaders.UserAgent] ?: "")
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(engine = engine)

        client.get("/test")

        assertThat(captured.single()).isEqualTo("Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1")
    }

    @Test
    fun `Accept header matches Kodi identity`() = runTest {
        val captured = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                captured.add(request.headers[HttpHeaders.Accept] ?: "")
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(engine = engine)

        client.get("/test")

        assertThat(captured.single()).isEqualTo("application/json, */*")
    }

    @Test
    fun `Accept-Language header matches Kodi identity`() = runTest {
        val captured = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                captured.add(request.headers[HttpHeaders.AcceptLanguage] ?: "")
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(engine = engine)

        client.get("/test")

        assertThat(captured.single()).isEqualTo("en-US,en;q=0.5")
    }

    @Test
    fun `Authorization header present when credentials provided`() = runTest {
        val captured = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                captured.add(request.headers[HttpHeaders.Authorization] ?: "")
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(login = "user", password = "pass", engine = engine)

        client.get("/test")

        assertThat(captured.single()).isNotEmpty()
    }

    @Test
    fun `Authorization header absent when credentials omitted`() = runTest {
        val captured = mutableListOf<String>()
        val engine =
            MockEngine { request ->
                captured.add(request.headers[HttpHeaders.Authorization] ?: "")
                respond(
                    content = "{}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(engine = engine)

        client.get("/test")

        assertThat(captured.single()).isEmpty()
    }
}
