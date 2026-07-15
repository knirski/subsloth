package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.Base64

/**
 * Tests that outgoing requests carry the correct Kodi-compatible identity
 * headers as configured by [ClientFactory].
 *
 * Uses the production [ClientFactory.create] with an injected [MockEngine]
 * so the assertions verify the real client configuration.
 */
class RequestIdentityTest {
    private val clients = mutableListOf<HttpClient>()

    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
    }

    private fun createClient(login: String? = null, password: String? = null, engine: MockEngine): HttpClient =
        ClientFactory
            .create(
                login = login,
                password = password,
                baseUrl = "http://localhost:1/",
                engine = engine,
            ).also { clients.add(it) }

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
    fun `Authorization header present with exact Basic value when credentials provided`() = runTest {
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

        val expected = "Basic " + Base64.getEncoder().encodeToString("user:pass".toByteArray())
        assertThat(captured.single()).isEqualTo(expected)
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

    @Test
    fun `Authorization header absent when only login provided`() = runTest {
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
        val client = createClient(login = "user", engine = engine)

        client.get("/test")

        assertThat(captured.single()).isEmpty()
    }

    @Test
    fun `Authorization header absent when only password provided`() = runTest {
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
        val client = createClient(password = "pass", engine = engine)

        client.get("/test")

        assertThat(captured.single()).isEmpty()
    }
}
