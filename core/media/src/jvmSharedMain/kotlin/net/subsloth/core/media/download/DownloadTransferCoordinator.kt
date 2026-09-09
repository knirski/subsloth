package net.subsloth.core.media.download

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.database.dao.DownloadedMediaDao
import net.subsloth.database.entity.DownloadedMediaEntity
import kotlin.time.Clock

/** Target resolved for a queued download: a progressive (single-file) URL. */
data class DownloadTarget(val url: String, val extension: String)

/** Events emitted while the coordinator drives transfers. */
sealed interface TransferEvent {
    data class Progress(val localId: LocalMediaIdentifier, val bytesWritten: Long, val totalBytes: Long?) :
        TransferEvent

    data class Completed(val localId: LocalMediaIdentifier, val sizeBytes: Long) : TransferEvent

    data class Failed(val localId: LocalMediaIdentifier, val reason: DownloadFailureReason) : TransferEvent
}

/** Thrown from the progress callback to abort a stream mid-transfer. */
class TransferAbortedException(status: String) : RuntimeException("Transfer aborted: $status")

/**
 * Drives the byte-transfer pipeline for queued downloads: scans the
 * `downloaded_media` table for QUEUED rows and runs each through
 * [DownloadTransferer], enforcing the transfer policy on the way.
 *
 * Policy (from `DownloadPolicy` + the enqueue-time contract that every
 * download is Wi-Fi-only): a QUEUED item is moved to PAUSED (NeedsWifi)
 * while the network is metered instead of transferring. The stream URL
 * is resolved per transfer (signed URLs are ephemeral) via the injected
 * [resolveDownloadUrl] lambda — a `null` resolution fails the item.
 *
 * Pause/remove detected mid-transfer aborts the current stream; the
 * next run restarts it from scratch (no ranged resume — documented
 * limitation). Terminal states are persisted to the same
 * [DownloadedMediaEntity] rows [DownloadController] manages, so the
 * library/downloads/offline-playback surfaces see real files.
 */
class DownloadTransferCoordinator(
    private val downloadedMediaDao: DownloadedMediaDao,
    private val store: DownloadTransferStore,
    private val transferer: DownloadTransferer,
    private val connectivityChecker: ConnectivityPort,
    private val clock: Clock,
    private val resolveDownloadUrl: suspend (mediaId: Media.MediaId, qualityLabel: String?) -> DownloadTarget?,
) {
    private val log = Logger.withTag("DownloadTransferCoordinator")

    private val _events = MutableSharedFlow<TransferEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<TransferEvent> = _events

    /** Guards against overlapping scans (DAO re-emissions while processing). */
    private val processMutex = Mutex()

    /**
     * Long-running driver: re-scans the queue whenever the DAO emits and
     * processes all QUEUED items. Safe to run for the container's process
     * lifetime. Overlapping invocations of [processQueued] are collapsed.
     */
    suspend fun runWatcher() {
        downloadedMediaDao.getAll().collect { entities ->
            if (entities.any { it.status == DownloadStatus.QUEUED.name.lowercase() }) {
                processQueued()
            }
        }
    }

    /**
     * Processes every currently-QUEUED download, one at a time. Returns
     * the number of items processed (0 when another scan is already
     * running). Re-throws only [CancellationException]; item failures are
     * persisted as FAILED, never surfaced as throwables.
     */
    suspend fun processQueued(): Int {
        if (!processMutex.tryLock()) return 0
        try {
            val queued = downloadedMediaDao.getAll().first()
                .filter { it.status == DownloadStatus.QUEUED.name.lowercase() }
            for (entity in queued) {
                process(entity)
            }
            return queued.size
        } finally {
            processMutex.unlock()
        }
    }

    private suspend fun process(entity: DownloadedMediaEntity) {
        val localId = LocalMediaIdentifier("${entity.contentId}/${entity.id}")
        val mediaId = parseMediaId(entity.contentId, entity.mediaType)
        if (mediaId == null) {
            fail(localId, entity, DownloadFailureReason.DownloadFailed)
            return
        }

        if (connectivityChecker.isMetered()) {
            log.d { "Deferring download ${localId.value}: metered network (Wi-Fi-only policy)" }
            downloadedMediaDao.upsert(entity.copy(status = DownloadStatus.PAUSED.name.lowercase()))
            return
        }

        val target = resolveDownloadUrl(mediaId, entity.selectedQuality)
        if (target == null) {
            log.e(null) { "No downloadable URL resolved for ${localId.value} (quality=${entity.selectedQuality})" }
            fail(localId, entity, DownloadFailureReason.DownloadFailed)
            return
        }

        downloadedMediaDao.upsert(entity.copy(status = DownloadStatus.DOWNLOADING.name.lowercase()))

        var lastReportedBytes = -1L
        val relativePath = store.allocatePath(entity.contentId, target.extension)
        val result = transferer.transfer(target.url, relativePath) { progress ->
            if (progress.bytesWritten != lastReportedBytes) {
                lastReportedBytes = progress.bytesWritten
                _events.tryEmit(TransferEvent.Progress(localId, progress.bytesWritten, progress.totalBytes))
            }
            checkStillActive(localId)
        }

        // The item left the active state while streaming (paused/removed
        // via DownloadController): its persisted status is authoritative
        // already, so don't overwrite it — just drop the staged file.
        if (result.exceptionOrNull() is TransferAbortedException) return

        result.fold(
            onSuccess = { bytes -> complete(entity, relativePath, bytes) },
            onFailure = { error ->
                if (error is CancellationException) throw error
                log.e(error) { "Transfer failed for ${localId.value}" }
                fail(localId, entity, DownloadFailureReason.DownloadFailed)
            },
        )
    }

    private suspend fun complete(original: DownloadedMediaEntity, relativePath: OfflineRelativePath, bytes: Long) {
        val current = downloadedMediaDao.getById(original.id)
        if (current == null || current.status != DownloadStatus.DOWNLOADING.name.lowercase()) {
            // Removed/paused while streaming; discard the finished file.
            store.deleteMedia(relativePath)
            return
        }
        val localId = LocalMediaIdentifier("${current.contentId}/${current.id}")
        val staged = store.stageFile(relativePath)
        val target = store.finalFile(relativePath)
        if (!store.finalizeDownload(staged, target) || !target.exists()) {
            fail(localId, current, DownloadFailureReason.DownloadFailed)
            return
        }
        downloadedMediaDao.upsert(
            current.copy(
                localFilePath = relativePath.value,
                sizeBytes = bytes,
                status = DownloadStatus.COMPLETED.name.lowercase(),
                downloadedAtEpochSeconds = clock.now().epochSeconds,
            ),
        )
        _events.tryEmit(TransferEvent.Completed(localId, bytes))
    }

    private suspend fun checkStillActive(localId: LocalMediaIdentifier) {
        val current = findByLocalId(localId) ?: throw TransferAbortedException("removed")
        when (current.status.lowercase()) {
            DownloadStatus.PAUSED.name.lowercase(),
            DownloadStatus.REMOVED.name.lowercase(),
            DownloadStatus.FAILED.name.lowercase(),
            -> throw TransferAbortedException(current.status)
        }
    }

    private suspend fun fail(
        localId: LocalMediaIdentifier,
        entity: DownloadedMediaEntity,
        reason: DownloadFailureReason,
    ) {
        downloadedMediaDao.upsert(entity.copy(status = DownloadStatus.FAILED.name.lowercase()))
        _events.tryEmit(TransferEvent.Failed(localId, reason))
    }

    private suspend fun findByLocalId(localId: LocalMediaIdentifier): DownloadedMediaEntity? {
        val downloadId = parseLocalIdDownloadId(localId) ?: return null
        return downloadedMediaDao.getById(downloadId)
    }
}

private fun parseMediaId(contentId: String, mediaType: String): Media.MediaId? = when (mediaType) {
    "movie" -> contentId.toIntOrNull()?.let { Media.MediaId.Movie(MovieId(it)) }
    "episode" -> contentId.toIntOrNull()?.let { Media.MediaId.Episode(EpisodeId(it)) }
    "show" -> contentId.toIntOrNull()?.let { Media.MediaId.Show(ShowId(it)) }
    else -> null
}
