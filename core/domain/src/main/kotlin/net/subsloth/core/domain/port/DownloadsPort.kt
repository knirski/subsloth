package net.subsloth.core.domain.port

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.media.Media

/**
 * Port for managing download state and offline media.
 *
 * Implementations are provided by the Android/media shell.
 */
interface DownloadsPort {
    /** Returns all download records. */
    suspend fun listDownloads(): Result<List<DownloadState>>

    /** Enqueues a new download for the given media item. */
    suspend fun enqueue(mediaId: Media.MediaId): Result<Unit>

    /** Cancels an active download. */
    suspend fun cancel(localId: LocalMediaIdentifier): Result<Unit>

    /** Removes a completed download and its local files. */
    suspend fun remove(localId: LocalMediaIdentifier): Result<Unit>
}
