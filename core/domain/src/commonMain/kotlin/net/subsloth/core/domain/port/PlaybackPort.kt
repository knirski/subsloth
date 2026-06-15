package net.subsloth.core.domain.port

import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.playback.VideoSource

/**
 * Port for starting and controlling media playback.
 *
 * Implementations are provided by the Android/media shell.
 * The ViewModel consumes this port via typed lambdas for testability;
 * the concrete implementation lives in `:core:media`.
 */
interface PlaybackPort {
    /**
     * Prepares a [VideoSource] for playback of the given media item.
     * Returns a typed [Outcome.Failure] (typically a
     * [net.subsloth.core.model.error.NetworkError.Technical]) on
     * failure.
     */
    suspend fun prepareSource(mediaId: Media.MediaId): Outcome<VideoSource>

    /** Starts playback of the prepared video source. */
    suspend fun play(source: VideoSource, positionSeconds: Long): Outcome<Unit>

    /** Pauses the current playback. */
    suspend fun pause(): Outcome<Unit>

    /** Seeks to the given position in the current playback. */
    suspend fun seek(positionSeconds: Long): Outcome<Unit>

    /**
     * Refreshes the stream URL for the current media item.
     *
     * At most one refresh is allowed per playback session. Returns the
     * refreshed [VideoSource] on success. Offline playback must never
     * call this method.
     */
    suspend fun refreshStreamUrl(mediaId: Media.MediaId): Outcome<VideoSource>
}
