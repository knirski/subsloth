package net.subsloth.core.network.media.client

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * The download client must not apply JSON response validation: a signed media
 * URL answers with `video/mp4`/`application/octet-stream` (and may redirect),
 * which [ResponseValidationPlugin] would reject before any bytes are read.
 * This regression test pins both sides of that difference.
 */
class ClientFactoryDownloadsTest {
    private val mediaBytes = ByteArray(64) { it.toByte() }

    private fun mediaEngine() = MockEngine {
        respond(
            content = mediaBytes,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "video/mp4"),
        )
    }

    @Test
    fun `download client streams non-json media responses`() = runTest {
        val client = ClientFactory.createForDownloads(engine = mediaEngine())

        val response = client.get("https://media.example.test/movie.mp4")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsBytes().size).isEqualTo(mediaBytes.size)
    }

    @Test
    fun `json client rejects non-json media responses`() = runTest {
        val client = ClientFactory.create(engine = mediaEngine())

        val ex = assertThrows<ResponseValidationException> { client.get("https://media.example.test/movie.mp4") }
        assertThat(ex.message).isNotNull()
    }
}
