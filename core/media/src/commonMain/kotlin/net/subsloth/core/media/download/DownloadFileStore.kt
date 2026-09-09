package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath

/**
 * Port for deleting stored download artifacts from the platform's
 * app-private download storage.
 *
 * [DownloadController] only ever removes files (on cancel/remove); the
 * transfer pipeline that *writes* files is platform-specific
 * (`DownloadForegroundService` on Android). Android's
 * `DownloadStorageManager` and desktop's `DesktopDownloadStore` both
 * implement this interface.
 */
interface DownloadFileStore {
    /** Deletes both the finalized file and its staged (`.part`) counterpart. */
    fun deleteMedia(localPath: OfflineRelativePath): Boolean
}
