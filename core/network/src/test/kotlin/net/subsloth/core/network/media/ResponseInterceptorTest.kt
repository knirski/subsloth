package net.subsloth.core.network.media

import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.network.media.client.ResponseException
import net.subsloth.core.network.media.client.ResponseInterceptor
import net.subsloth.testing.assertions.assertThat
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/**
 * Tests for [ResponseInterceptor] — redirect, HTML, and non-JSON response
 * detection before DTO parsing.
 */
class ResponseInterceptorTest {
    private val interceptor = ResponseInterceptor()

    @Test
    fun `redirect response throws ResponseException with UnexpectedResponse`() {
        val response = buildResponse(code = 302, contentType = "application/json")
        val exception = interceptAndCapture(response)

        assertThat(exception).isNotNull()
        val ex = exception!!
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
        assertThat(ex.message).contains("302")
    }

    @Test
    fun `permanent redirect throws ResponseException`() {
        val response = buildResponse(code = 301, contentType = "application/json")
        val exception = interceptAndCapture(response)

        assertThat(exception).isNotNull()
        val ex = exception!!
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
        assertThat(ex.message).contains("301")
    }

    @Test
    fun `temporary redirect throws ResponseException`() {
        val response = buildResponse(code = 307, contentType = "application/json")
        val exception = interceptAndCapture(response)

        assertThat(exception).isNotNull()
        val ex = exception!!
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
        assertThat(ex.message).contains("307")
    }

    @Test
    fun `HTML response throws ResponseException`() {
        val response = buildResponse(code = 200, contentType = "text/html; charset=utf-8")
        val exception = interceptAndCapture(response)

        assertThat(exception).isNotNull()
        val ex = exception!!
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
        assertThat(ex.message).contains("HTML")
    }

    @Test
    fun `non-JSON response throws ResponseException`() {
        val response = buildResponse(code = 200, contentType = "text/plain")
        val exception = interceptAndCapture(response)

        assertThat(exception).isNotNull()
        val ex = exception!!
        assertThat(ex.error).isEqualTo(NetworkError.UnexpectedResponse)
        assertThat(ex.message).contains("text/plain")
    }

    @Test
    fun `application JSON response passes through`() {
        val response = buildResponse(code = 200, contentType = "application/json")
        val passed = interceptAndReturn(response)

        assertThat(passed).isNotNull()
        assertThat(passed!!.code).isEqualTo(200)
    }

    @Test
    fun `application javascript response passes through`() {
        val response = buildResponse(code = 200, contentType = "application/javascript")
        val passed = interceptAndReturn(response)

        assertThat(passed).isNotNull()
        assertThat(passed!!.code).isEqualTo(200)
    }

    @Test
    fun `star slash star content type passes through`() {
        val response = buildResponse(code = 200, contentType = "*/*")
        val passed = interceptAndReturn(response)

        assertThat(passed).isNotNull()
        assertThat(passed!!.code).isEqualTo(200)
    }

    @Test
    fun `vnd dot api plus json content type passes through`() {
        val response = buildResponse(code = 200, contentType = "application/vnd.api+json")
        val passed = interceptAndReturn(response)

        assertThat(passed).isNotNull()
        assertThat(passed!!.code).isEqualTo(200)
    }

    @Test
    fun `HTTP 200 success passes through`() {
        val response = buildResponse(code = 200, contentType = "application/json")
        val passed = interceptAndReturn(response)

        assertThat(passed).isNotNull()
        assertThat(passed!!.code).isEqualTo(200)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Runs the interceptor on a mock chain that returns [response] and
     * captures any [ResponseException] thrown, or returns `null`
     * if the interceptor passes the response through.
     */
    private fun interceptAndCapture(response: Response): ResponseException? {
        val chain = FakeChain(response)
        val result = runCatching { interceptor.intercept(chain) }
        return result.exceptionOrNull() as? ResponseException
    }

    /**
     * Runs the interceptor on a mock chain that returns [response] and
     * returns the response if it passes through, or `null` if an exception
     * was thrown.
     */
    private fun interceptAndReturn(response: Response): Response? {
        val chain = FakeChain(response)
        val result = runCatching { interceptor.intercept(chain) }
        return result.getOrNull()
    }

    private fun buildResponse(code: Int, contentType: String): Response {
        val request =
            Request
                .Builder()
                .url("https://api.example.com/movies")
                .build()
        val body = """{"data": "test"}""".toResponseBody(contentType.toMediaType())
        return Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 302) "Found" else "OK")
            .body(body)
            .header("Content-Type", contentType)
            .build()
    }

    /**
     * A fake [Interceptor.Chain] that returns a pre-built [Response].
     */
    private class FakeChain(private val response: Response) : Interceptor.Chain {
        override fun request(): Request = response.request

        override fun proceed(request: Request): Response = response

        override fun connection() = null

        override fun call() = error("Not supported in test")

        override fun connectTimeoutMillis() = 10_000

        override fun withConnectTimeout(timeout: Int, unit: TimeUnit) = this

        override fun readTimeoutMillis() = 10_000

        override fun withReadTimeout(timeout: Int, unit: TimeUnit) = this

        override fun writeTimeoutMillis() = 10_000

        override fun withWriteTimeout(timeout: Int, unit: TimeUnit) = this
    }
}
