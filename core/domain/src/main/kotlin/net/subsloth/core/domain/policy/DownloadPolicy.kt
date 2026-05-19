package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor

/**
 * Pure policies for download decisions.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object DownloadPolicy {
    /** Download statuses that count as "active" for concurrency limiting. */
    private val ACTIVE_STATUSES: Set<DownloadStatus> =
        setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
        )

    /**
     * Returns `true` when the available free space is sufficient for a
     * new download, considering the minimum [reserveBytes] that must be
     * kept free.
     *
     * @param availableBytes free space on the storage device.
     * @param requiredBytes space needed for the download.
     * @param reserveBytes minimum free space that must remain after download.
     */
    fun hasSufficientStorage(
        availableBytes: Long,
        requiredBytes: Long,
        reserveBytes: Long,
    ): Boolean = availableBytes >= requiredBytes + reserveBytes

    /**
     * Returns `true` when a new download can be started given the current
     * list of [existingDownloads].
     *
     * Only one active video download is allowed at a time. Active downloads
     * include [DownloadStatus.QUEUED] and [DownloadStatus.DOWNLOADING].
     */
    fun canStartNewDownload(existingDownloads: List<DownloadState>): Boolean =
        existingDownloads.none { it.status in ACTIVE_STATUSES }

    /**
     * Returns `true` when [candidateMediaId] is already present in
     * [existingDownloads].
     */
    fun isDuplicate(
        existingDownloads: List<DownloadState>,
        candidateMediaId: Media.MediaId,
    ): Boolean = existingDownloads.any { it.mediaId == candidateMediaId }

    /**
     * Returns `true` when replacing [existing] quality with [candidate]
     * quality is allowed.
     *
     * Only upgrades to a strictly higher quality are permitted.
     */
    fun canReplaceQuality(
        existing: QualityDescriptor,
        candidate: QualityDescriptor,
    ): Boolean = candidate.resolution.pixelCount > existing.resolution.pixelCount

    /**
     * Returns a new list with all incomplete downloads paused.
     *
     * Completed and already-removed downloads are not affected.
     */
    fun pauseOnLogout(downloads: List<DownloadState>): List<DownloadState> =
        downloads.map { download ->
            if (download.status in ACTIVE_STATUSES) {
                download.copy(status = DownloadStatus.PAUSED)
            } else {
                download
            }
        }
}
