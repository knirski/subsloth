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
import org.junit.jupiter.api.Test

/**
 * Behaviour tests for the [HttpRequestRetry] policy matching
 * [ClientFactory]'s configuration.
 *
 * Uses the production [ClientFactory.create] with an injected [MockEngine]
 * so the assertions verify the real client configuration.
 *
 * Verifies:
 * - maxRetries = 2
 * - Retries on 429 (rate limit) and 5xx (server error)
 * - No retry on 4xx errors outside the retryable set
 * - Exhaustion after 2 retries
 */
class RetryPolicyTest {
    private fun createClient(engine: MockEngine): HttpClient = ClientFactory.create(
        baseUrl = "http://localhost:1/",
        engine = engine,
    )

    @Test
    fun `retries on server error 500 then succeeds`() = runTest {
        var callCount = 0
        val engine =
            MockEngine {
                callCount++
                when {
                    callCount < 2 ->
                        respond(
                            content = """{"error":"server error"}""",
                            status = HttpStatusCode.InternalServerError,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                    else ->
                        respond(
                            content = """{"data":"ok"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                }
            }
        val client = createClient(engine)

        val response = client.get("/test")

        assertThat(callCount).isEqualTo(2)
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `retries on rate limit 429 then succeeds`() = runTest {
        var callCount = 0
        val engine =
            MockEngine {
                callCount++
                when {
                    callCount < 2 ->
                        respond(
                            content = """{"error":"rate limited"}""",
                            status = HttpStatusCode.TooManyRequests,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )

                    else ->
                        respond(
                            content = """{"data":"ok"}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                }
            }
        val client = createClient(engine)

        val response = client.get("/test")

        assertThat(callCount).isEqualTo(2)
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
    }

    @Test
    fun `does not retry on non-retryable 4xx error`() = runTest {
        var callCount = 0
        val engine =
            MockEngine {
                callCount++
                respond(
                    content = """{"error":"bad request"}""",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(engine)

        val response = client.get("/test")

        assertThat(callCount).isEqualTo(1)
        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
    }

    @Test
    fun `exhausts retries on persistent server errors`() = runTest {
        var callCount = 0
        val engine =
            MockEngine {
                callCount++
                respond(
                    content = """{"error":"server error"}""",
                    status = HttpStatusCode.InternalServerError,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        val client = createClient(engine)

        val response = client.get("/test")

        // maxRetries=2 means 3 total attempts (initial + 2 retries)
        assertThat(callCount).isEqualTo(3)
        assertThat(response.status).isEqualTo(HttpStatusCode.InternalServerError)
    }
}
