package net.subsloth.core.media.subtitle

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.error.NetworkError
import net.subsloth.core.model.error.Outcome
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import kotlin.test.fail

class SubtitleTextLoaderTest {

    private val loader = SubtitleTextLoader()

    @Test
    fun `reads subtitle text from a file URL`() = runTest {
        val file = Files.createTempFile("subsloth-subtitle", ".vtt")
        try {
            Files.writeString(file, "WEBVTT\n\n00:00.000 --> 00:02.000\nhéllo offline")

            val result = loader.load(file.toUri().toString())

            assertThat(result.successValue())
                .isEqualTo("WEBVTT\n\n00:00.000 --> 00:02.000\nhéllo offline")
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `returns a failure instead of throwing for a missing file`() = runTest {
        val file = Files.createTempFile("subsloth-missing", ".vtt")
        Files.delete(file)

        val result = loader.load(file.toUri().toString())

        assertThat(result.failureError()).isEqualTo(NetworkError.UnexpectedResponse)
    }

    @Test
    fun `rejects a document larger than the byte cap`() = runTest {
        val cappedLoader = SubtitleTextLoader(maxBytes = 16)
        val file = Files.createTempFile("subsloth-large", ".vtt")
        try {
            Files.writeString(file, "x".repeat(64))

            val result = cappedLoader.load(file.toUri().toString())

            assertThat(result.failureError()).isEqualTo(NetworkError.UnexpectedResponse)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `returns a failure for a malformed URL`() = runTest {
        val result = loader.load("not a url")

        assertThat(result.failureError()).isEqualTo(NetworkError.UnexpectedResponse)
    }

    @Test
    fun `rejects an unsupported URL scheme`() = runTest {
        val result = loader.load("ftp://example.com/subtitle.vtt")

        assertThat(result.failureError()).isEqualTo(NetworkError.UnexpectedResponse)
    }

    @Test
    fun `returns a failure for a non-2xx response`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/not-modified.vtt") { exchange ->
            exchange.sendResponseHeaders(304, -1)
            exchange.close()
        }
        server.start()
        try {
            val result = loader.load("http://127.0.0.1:${server.address.port}/not-modified.vtt")

            assertThat(result.failureError()).isEqualTo(NetworkError.UnexpectedResponse)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `reads subtitle text from an HTTP URL`() = runTest {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/subtitle.vtt") { exchange ->
            val body = "WEBVTT\n\nremote".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()
        try {
            val result = loader.load("http://127.0.0.1:${server.address.port}/subtitle.vtt")

            assertThat(result.successValue()).contains("remote")
        } finally {
            server.stop(0)
        }
    }

    private fun Outcome<String>.successValue(): String = when (this) {
        is Outcome.Success -> value
        is Outcome.Failure -> fail("Expected success but got $error")
    }

    private fun Outcome<String>.failureError() = when (this) {
        is Outcome.Failure -> error
        is Outcome.Success -> fail("Expected failure but got: $value")
    }
}
