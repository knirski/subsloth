package net.subsloth.core.media.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeString
import kotlinx.coroutines.test.runTest
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.readBytes

class DownloadTransfererTest {

    private val tempDir = Files.createTempDirectory("transferer-test")

    private fun transfererWith(handler: suspend MockRequestHandleScope.(String) -> HttpResponseData) =
        DownloadTransferer(
            client = HttpClient(MockEngine { handler(it.url.toString()) }),
            store = DesktopDownloadStore(tempDir.toFile()),
        )

    @Test
    fun `transfers the full body to the staged file and reports progress`() = runTest {
        val body = "hello download world"
        val transferer = transfererWith { _ ->
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = io.ktor.http.headersOf("Content-Length", body.length.toString()),
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
    fun `a non-2xx response fails the transfer and discards the staged file`() = runTest {
        val transferer = transfererWith { _ ->
            respond(content = ByteReadChannel(""), status = HttpStatusCode.Forbidden)
        }
        val relativePath = OfflineRelativePath.safe("1/asset.mp4")

        val result = transferer.transfer("https://cdn.example.com/file.mp4", relativePath)

        assertThat(result.isFailure).isTrue()
        assertThat(tempDir.resolve("1/asset.mp4.part").toFile().exists()).isFalse()
    }
}
