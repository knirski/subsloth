package net.subsloth.details

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.media.Media
/**
 * UI-facing download lifecycle for the media item shown on a detail screen.
 *
 * [QUEUED] covers everything with a local row that is not actively
 * transferring (queued, partial, paused); [FAILED] covers failed and
 * unavailable rows, so the button offers a retry instead of silently
 * staying on "Download".
 */
enum class DownloadStatus {
    NOT_DOWNLOADED,
    QUEUED,
    DOWNLOADING,
    FAILED,
    DOWNLOADED,
}

internal data class DetailFlags(
    val isFavorite: Boolean = false,
    val isWatchLater: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NOT_DOWNLOADED,
    val downloadProgressPercent: Int? = null,
)

internal data class DownloadSnapshot(val status: DownloadStatus, val progressPercent: Int?)

/**
 * Picks the most significant download row for [mediaId] (a completed copy
 * wins over an active transfer, which wins over queued/failed leftovers).
 */
internal fun downloadSnapshot(downloads: List<DownloadState>, mediaId: Media.MediaId): DownloadSnapshot {
    val state =
        downloads
            .filter { it.mediaId == mediaId }
            .maxByOrNull { it.downloadRank() }
            ?: return DownloadSnapshot(DownloadStatus.NOT_DOWNLOADED, null)
    return when (state) {
        is DownloadState.Completed -> DownloadSnapshot(DownloadStatus.DOWNLOADED, null)

        is DownloadState.Active -> DownloadSnapshot(DownloadStatus.DOWNLOADING, state.progressPercent)

        is DownloadState.Queued,
        is DownloadState.Partial,
        is DownloadState.Paused,
        -> DownloadSnapshot(DownloadStatus.QUEUED, null)

        is DownloadState.Failed,
        is DownloadState.Unavailable,
        -> DownloadSnapshot(DownloadStatus.FAILED, null)

        is DownloadState.Removed -> DownloadSnapshot(DownloadStatus.NOT_DOWNLOADED, null)
    }
}

private fun DownloadState.downloadRank(): Int = when (this) {
    is DownloadState.Completed -> 6
    is DownloadState.Active -> 5
    is DownloadState.Queued -> 4
    is DownloadState.Partial -> 3
    is DownloadState.Paused -> 2
    is DownloadState.Failed -> 1
    is DownloadState.Unavailable -> 1
    is DownloadState.Removed -> 0
}

internal const val DOWNLOAD_MONITOR_INTERVAL_MS = 1_000L

internal const val DOWNLOAD_MONITOR_ATTEMPTS = 180
