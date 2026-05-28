package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState

object StorageCleanupPolicy {
    fun cleanupCandidates(downloads: List<DownloadState>): List<DownloadState> =
        downloads
            .filterIsInstance<DownloadState.Completed>()
            .sortedBy { it.downloadedAtEpochSeconds }

    fun estimatedReclaimableBytes(candidates: List<DownloadState>): Long =
        candidates.sumOf {
            if (it is DownloadState.Completed) it.sizeBytes ?: 0L else 0L
        }
}
