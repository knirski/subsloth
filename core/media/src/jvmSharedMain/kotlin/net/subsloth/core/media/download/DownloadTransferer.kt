package net.subsloth.core.media.download

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import net.subsloth.core.model.download.OfflineRelativePath
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/** Progress snapshot emitted by [DownloadTransferer] while streaming. */
data class TransferProgress(val bytesWritten: Long, val totalBytes: Long?)

/**
 * Streams a signed download URL to the store's staged (`.part`) file and
 * finalizes it on success. Purely imperative shell: HTTP read + file
 * write; all policy (metered networks, storage, dedup) lives upstream in
 * [DownloadTransferCoordinator]/`DownloadPolicy`.
 *
 * Progress is reported after each chunk; callers throttle if needed.
 * There is no ranged resume: a cancelled or failed transfer restarts
 * from zero on the next run (documented limitation).
 */
class DownloadTransferer(private val client: HttpClient, private val store: DownloadTransferStore) {
    suspend fun transfer(
        url: String,
        relativePath: OfflineRelativePath,
        onProgress: suspend (TransferProgress) -> Unit = {},
    ): Result<Long> {
        val staged = store.stageFile(relativePath)
        staged.parentFile?.mkdirs()
        return runCatching {
            var written = 0L
            client.prepareGet(url).execute { response ->
                if (!response.status.isSuccess()) {
                    error("Download request failed with HTTP ${response.status.value}")
                }
                val total = response.headers["Content-Length"]?.toLongOrNull()
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(CHUNK_BYTES.toInt())
                staged.outputStream().use { output ->
                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer, 0, buffer.size)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(TransferProgress(written, total))
                    }
                }
            }
            written
        }.also { result ->
            // Failed or cancelled transfers discard the staged file: the
            // next run restarts from scratch (no ranged resume).
            if (result.isFailure) staged.delete()
        }.onFailure { if (it is CancellationException) throw it }
    }

    companion object {
        private const val CHUNK_BYTES = 64L * 1024
    }
}
