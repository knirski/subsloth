package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState

/** Pure policies for selecting downloads to remove when storage is low. */
object StorageCleanupPolicy {
    fun cleanupCandidates(downloads: List<DownloadState>): List<DownloadState.Completed> = downloads
        .filterIsInstance<DownloadState.Completed>()
        .sortedBy { it.downloadedAtEpochSeconds }

    fun estimatedReclaimableBytes(candidates: List<DownloadState.Completed>): Long =
        candidates.sumOf { it.sizeBytes ?: 0L }
}
