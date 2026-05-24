package net.subsloth.core.model.download

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import kotlin.time.Instant

/**
 * Represents the state of a downloaded media item available for offline playback.
 *
 * This is a persistent record. It identifies the local storage location via
 * [localId] and does not contain any raw stream URLs, which are
 * ephemeral and obtained from the server at stream time.
 */
@Immutable
data class DownloadState(
    val localId: LocalMediaIdentifier,
    val mediaId: Media.MediaId,
    val status: DownloadStatus,
    val quality: QualityDescriptor,
    val downloadedAtEpochSeconds: Instant,
    /** Total file size in bytes, if known. */
    val sizeBytes: Long?,
    /** Storage path relative to the app's download directory. */
    val relativePath: String?,
)

/** Possible states of a download operation. */
@Immutable
enum class DownloadStatus {
    /** Download is queued and waiting to start. */
    QUEUED,

    /** Download is actively in progress. */
    DOWNLOADING,

    /** Download completed successfully and is available offline. */
    COMPLETED,

    /** Download failed and may be retried. */
    FAILED,

    /** Download was paused by the user or system. */
    PAUSED,

    /** Download has been removed from local storage. */
    REMOVED,
}
