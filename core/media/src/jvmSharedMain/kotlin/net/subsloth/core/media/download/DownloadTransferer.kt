package net.subsloth.core.media.download

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import net.subsloth.core.model.download.OfflineRelativePath
import java.io.FileOutputStream
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
 *
 * Ranged resume: when a staged file already holds bytes, the request is
 * sent with `Range: bytes=<staged size>-`, and a `206 Partial Content`
 * response is appended while a `200 OK` (server ignored the range, e.g.
 * the asset changed) restarts the file from zero. Reported progress is
 * always absolute (staged bytes + newly received), so callers can resume
 * UI/notifications from the same numbers.
 *
 * Staged bytes are kept on failure by default: pause/remove/crash can then
 * resume instead of restarting. Callers for whom a partial is useless
 * (subtitle sidecars re-resolved per attempt) pass
 * [keepPartialOnFailure] `false` to avoid orphaned files.
 */
class DownloadTransferer(private val client: HttpClient, private val store: DownloadTransferStore) {
    suspend fun transfer(
        url: String,
        relativePath: OfflineRelativePath,
        keepPartialOnFailure: Boolean = true,
        onProgress: suspend (TransferProgress) -> Unit = {},
    ): Result<Long> {
        val staged = store.stageFile(relativePath)
        staged.parentFile?.mkdirs()
        val resumeFrom = staged.length().coerceAtLeast(0L)
        return runCatching {
            var written = 0L
            client.prepareGet(url) {
                if (resumeFrom > 0L) {
                    header(HttpHeaders.Range, "bytes=$resumeFrom-")
                }
            }.execute { response ->
                // A resume whose partial already equals the resource total:
                // the server rejects the (empty) range but the staged bytes
                // are the complete body, so the finalize step can run.
                if (response.status == HttpStatusCode.RequestedRangeNotSatisfiable) {
                    val total = response.contentRangeTotal()
                    if (resumeFrom > 0L && total != null && total == resumeFrom) {
                        written = resumeFrom
                        return@execute
                    }
                    error("Download request failed with HTTP ${response.status.value}")
                }
                val resuming = resumeFrom > 0L && response.status == HttpStatusCode.PartialContent
                if (!resuming && !response.status.isSuccess()) {
                    error("Download request failed with HTTP ${response.status.value}")
                }
                val start = if (resuming) response.contentRangeStart() else 0L
                if (resuming && start != resumeFrom) {
                    error("Server resumed at byte $start, expected $resumeFrom")
                }
                val total = if (resuming) {
                    response.contentRangeTotal()
                } else {
                    response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                }
                written = if (resuming) resumeFrom else 0L
                val channel = response.bodyAsChannel()
                val buffer = ByteArray(CHUNK_BYTES.toInt())
                FileOutputStream(staged, resuming).use { output ->
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
            // Failed/cancelled transfers keep the staged bytes so the next
            // run resumes; callers that opt out delete them instead.
            if (result.isFailure && !keepPartialOnFailure) staged.delete()
        }.onFailure { if (it is CancellationException) throw it }
    }

    companion object {
        private const val CHUNK_BYTES = 64L * 1024
    }
}

/** Total resource size from a `Content-Range: bytes start-end/total` header. */
private fun HttpResponse.contentRangeTotal(): Long? = headers[HttpHeaders.ContentRange]
    ?.substringAfter('/', missingDelimiterValue = "")
    ?.trim()
    ?.toLongOrNull()
    ?.takeIf { it > 0L }

/** First byte position from a `Content-Range: bytes start-end/total` header. */
private fun HttpResponse.contentRangeStart(): Long? = headers[HttpHeaders.ContentRange]
    ?.substringAfter("bytes ", missingDelimiterValue = "")
    ?.substringBefore('-')
    ?.trim()
    ?.toLongOrNull()
