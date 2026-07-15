package net.subsloth.core.network.media.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.error.NetworkError
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Direct behaviour tests for [ResponseValidationPlugin].
 *
 * The plugin intercepts responses and throws [ResponseValidationException]
 * when the server returns HTML, a redirect, or a non-JSON 2xx response.
 */
class ResponseValidationPluginTest {
    private fun createClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ResponseValidationPlugin)
    }

    @Test
    fun `html response throws ResponseValidationException`() = runTest {
        val engine =
            MockEngine {
                respond(
                    content = "<html><body>Not JSON</body></html>",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
                )
            }
        val client = createClient(engine)

        val ex = org.junit.jupiter.api.assertThrows<ResponseValidationException> { client.get("/test") }
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
    }

    @Test
    fun `redirect response throws ResponseValidationException`() = runTest {
        val engine =
            MockEngine {
                respond(
                    content = "",
                    status = HttpStatusCode.MovedPermanently,
                    headers =
                    headersOf(
                        HttpHeaders.ContentType to listOf(ContentType.Text.Plain.toString()),
                        HttpHeaders.Location to listOf("/new-location"),
                    ),
                )
            }
        val client = createClient(engine)

        val ex = org.junit.jupiter.api.assertThrows<ResponseValidationException> { client.get("/old") }
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
    }

    @Test
    fun `non-json 2xx response throws ResponseValidationException`() = runTest {
        val engine =
            MockEngine {
                respond(
                    content = "plain text",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                )
            }
        val client = createClient(engine)

        val ex = org.junit.jupiter.api.assertThrows<ResponseValidationException> { client.get("/test") }
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
    }

    @Test
    fun `json response passes through without exception`() = runTest {
        val engine =
            MockEngine {
                respond(
                    content = """{"key": "value"}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                )
            }
        val client = createClient(engine)

        val response = client.get("/test")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).isEqualTo("""{"key": "value"}""")
    }

    @Test
    fun `null content-type 2xx passes through without exception`() = runTest {
        val engine =
            MockEngine {
                respond(
                    content = """{}""",
                    status = HttpStatusCode.OK,
                    // no Content-Type header
                )
            }
        val client = createClient(engine)

        val response = client.get("/test")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }
}
