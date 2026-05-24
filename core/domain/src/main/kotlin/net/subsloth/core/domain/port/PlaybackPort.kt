package net.subsloth.core.domain.port

import net.subsloth.core.model.media.Media
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.VideoSource

/**
 * Port for starting and controlling media playback.
 *
 * Implementations are provided by the Android/media shell.
 */
interface PlaybackPort {
    /**
     * Prepares a [VideoSource] for playback of the given media item.
     */
    suspend fun prepareSource(mediaId: Media.MediaId): Result<VideoSource>

    /** Starts playback of the prepared video source. */
    suspend fun play(
        source: VideoSource,
        positionSeconds: Long,
    ): Result<Unit>

    /** Pauses the current playback. */
    suspend fun pause(): Result<Unit>

    /** Seeks to the given position in the current playback. */
    suspend fun seek(positionSeconds: Long): Result<Unit>

    /**
     * Refreshes the stream URL for the current media item.
     *
     * At most one refresh is allowed per playback session. Returns the
     * refreshed [VideoSource] on success, or a [PlaybackError] on failure.
     * Offline playback must never call this method.
     */
    suspend fun refreshStreamUrl(mediaId: Media.MediaId): Result<VideoSource>
}
