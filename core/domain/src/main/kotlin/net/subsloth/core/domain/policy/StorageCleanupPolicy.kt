package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus

/**
 * Pure policies for storage cleanup decisions.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object StorageCleanupPolicy {
    /**
     * Selects downloads eligible for cleanup when storage is low.
     *
     * Cleanup candidates are sorted by least recently downloaded first.
     * Only completed downloads are considered for cleanup; active,
     * failed, and paused downloads are preserved.
     */
    fun cleanupCandidates(downloads: List<DownloadState>): List<DownloadState> =
        downloads
            .filter { it.status == DownloadStatus.COMPLETED }
            .sortedBy { it.downloadedAtEpochSeconds }

    /**
     * Returns the estimated reclaimable bytes from the [candidates].
     *
     * Only downloads with a known [DownloadState.sizeBytes] are counted.
     */
    fun estimatedReclaimableBytes(candidates: List<DownloadState>): Long = candidates.sumOf { it.sizeBytes ?: 0L }
}
