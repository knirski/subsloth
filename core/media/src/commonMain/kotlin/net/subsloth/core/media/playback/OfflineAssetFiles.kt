package net.subsloth.core.media.playback

import net.subsloth.core.model.download.OfflineRelativePath

/**
 * Port for locating and verifying a stored offline asset's file.
 *
 * Android's `DownloadStorageManager` and desktop's `DesktopDownloadStore`
 * both implement this over their app-private download directories. The
 * returned URI is only read at playback time — it is never persisted.
 */
interface OfflineAssetFiles {
    /** An absolute, player-addressable URI (`file://`) for the stored file. */
    fun fileUri(localPath: OfflineRelativePath): String

    /** True when the file exists and is non-empty (see `DownloadPolicy.fileIntegrityStatus`). */
    fun verifyFile(localPath: OfflineRelativePath): Boolean
}
