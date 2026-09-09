package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath
import java.io.File

/**
 * Port for allocating, staging, finalizing, and cleaning up download
 * artifacts inside the platform's app-private download storage.
 *
 * Implemented by Android's `DownloadStorageManager` and desktop's
 * `DesktopDownloadStore` (both already expose these operations); the
 * transfer pipeline ([DownloadTransferer]/[DownloadTransferCoordinator])
 * is the consumer.
 */
interface DownloadTransferStore : DownloadFileStore {
    /** Allocates an opaque relative path for [contentId] within the store root. */
    fun allocatePath(contentId: String, extension: String): OfflineRelativePath

    /** The staged (partial-download) file — writes land here first. */
    fun stageFile(relativePath: OfflineRelativePath): File

    /** The finalized file for [relativePath]. */
    fun finalFile(relativePath: OfflineRelativePath): File

    /** Renames the staged file into its final location. */
    fun finalizeDownload(staged: File, target: File): Boolean
}
