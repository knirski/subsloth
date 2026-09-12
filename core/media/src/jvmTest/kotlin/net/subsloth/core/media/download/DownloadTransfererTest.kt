package net.subsloth.core.media.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.readBytes
import kotlin.io.path.writeText

class DownloadTransfererTest {

    private val tempDir = Files.createTempDirectory("transferer-test")

    private fun transfererWith(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        DownloadTransferer(
            client = HttpClient(MockEngine { handler(it) }),
            store = DesktopDownloadStore(tempDir.toFile()),
        )

    @Test
    fun `transfers the full body to the staged file and reports progress`() = runTest {
        val body = "hello download world"
        val transferer = transfererWith { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, body.length.toString()),
            )
        }
        val relativePath = OfflineRelativePath.safe("1/asset.mp4")
        val progress = mutableListOf<TransferProgress>()

        val result = transferer.transfer("https://cdn.example.com/file.mp4", relativePath) {
            progress.add(it)
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow()).isEqualTo(body.length.toLong())
        val staged = tempDir.resolve("1/asset.mp4.part")
        assertThat(String(staged.readBytes())).isEqualTo(body)
        assertThat(progress.last().bytesWritten).isEqualTo(body.length.toLong())
        assertThat(progress.last().totalBytes).isEqualTo(body.length.toLong())
    }

    @Test
    fun `a non-2xx response fails the transfer without creating the staged file`() = runTest {
        val transferer = transfererWith { _ ->
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Forbidden)
        }
        val relativePath = OfflineRelativePath.safe("1/asset.mp4")

        val result = transferer.transfer("https://cdn.example.com/file.mp4", relativePath)

        assertThat(result.isFailure).isTrue()
        assertThat(tempDir.resolve("1/asset.mp4.part").toFile().exists()).isFalse()
    }

    @Test
    fun `resumes from the staged bytes with a range request and appends`() = runTest {
        val prefix = "hello "
        val suffix = "download world"
        val total = prefix.length + suffix.length
        val staged = tempDir.resolve("1/asset.mp4.part").toFile()
        staged.parentFile?.mkdirs()
        staged.writeText(prefix)
        var requestedRange: String? = null
        val transferer = transfererWith { request ->
            requestedRange = request.headers[HttpHeaders.Range]
            respond(
                content = ByteReadChannel(suffix),
                status = HttpStatusCode.PartialContent,
                headers = headersOf(
                    HttpHeaders.ContentLength to listOf(suffix.length.toString()),
                    HttpHeaders.ContentRange to listOf("bytes ${prefix.length}-${total - 1}/$total"),
                ),
            )
        }
        val relativePath = OfflineRelativePath.safe("1/asset.mp4")
        val progress = mutableListOf<TransferProgress>()

        val result = transferer.transfer("https://cdn.example.com/file.mp4", relativePath) {
            progress.add(it)
        }

        assertThat(requestedRange).isEqualTo("bytes=${prefix.length}-")
        assertThat(result.getOrThrow()).isEqualTo(total.toLong())
        assertThat(String(staged.readBytes())).isEqualTo(prefix + suffix)
        assertThat(progress.last().bytesWritten).isEqualTo(total.toLong())
        assertThat(progress.last().totalBytes).isEqualTo(total.toLong())
    }

    @Test
    fun `restarts from zero when the server ignores the range`() = runTest {
        val body = "replacement body"
        val staged = tempDir.resolve("1/asset.mp4.part").toFile()
        staged.parentFile?.mkdirs()
        staged.writeText("stale bytes")
        val transferer = transfererWith { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentLength, body.length.toString()),
            )
        }

        val result = transferer.transfer("https://cdn.example.com/file.mp4", OfflineRelativePath.safe("1/asset.mp4"))

        assertThat(result.getOrThrow()).isEqualTo(body.length.toLong())
        assertThat(String(staged.readBytes())).isEqualTo(body)
    }

    @Test
    fun `completes when a 416 reports the staged bytes are the whole resource`() = runTest {
        val body = "already downloaded"
        val staged = tempDir.resolve("1/asset.mp4.part").toFile()
        staged.parentFile?.mkdirs()
        staged.writeText(body)
        var requestedRange: String? = null
        val transferer = transfererWith { request ->
            requestedRange = request.headers[HttpHeaders.Range]
            respond(
                content = ByteReadChannel(""),
                status = HttpStatusCode.RequestedRangeNotSatisfiable,
                headers = headersOf(HttpHeaders.ContentRange, "bytes */${body.length}"),
            )
        }

        val result = transferer.transfer("https://cdn.example.com/file.mp4", OfflineRelativePath.safe("1/asset.mp4"))

        assertThat(requestedRange).isEqualTo("bytes=${body.length}-")
        assertThat(result.getOrThrow()).isEqualTo(body.length.toLong())
        assertThat(String(staged.readBytes())).isEqualTo(body)
    }

    @Test
    fun `keeps the staged bytes on failure so the next run can resume`() = runTest {
        val staged = tempDir.resolve("1/asset.mp4.part").toFile()
        staged.parentFile?.mkdirs()
        staged.writeText("partial bytes")
        val transferer = transfererWith { _ ->
            respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
        }

        val result = transferer.transfer("https://cdn.example.com/file.mp4", OfflineRelativePath.safe("1/asset.mp4"))

        assertThat(result.isFailure).isTrue()
        assertThat(String(staged.readBytes())).isEqualTo("partial bytes")
    }

    @Test
    fun `deletes the staged bytes on failure when keepPartialOnFailure is false`() = runTest {
        val staged = tempDir.resolve("1/asset.mp4.part").toFile()
        staged.parentFile?.mkdirs()
        staged.writeText("partial bytes")
        val transferer = transfererWith { _ ->
            respond(content = ByteReadChannel(""), status = HttpStatusCode.InternalServerError)
        }

        val result = transferer.transfer(
            url = "https://cdn.example.com/file.mp4",
            relativePath = OfflineRelativePath.safe("1/asset.mp4"),
            keepPartialOnFailure = false,
        )

        assertThat(result.isFailure).isTrue()
        assertThat(staged.exists()).isFalse()
    }
}
