package subsloth.player

import subsloth.core.model.playback.VideoSource
import kotlin.time.Duration

/**
 * Abstraction over a platform-specific media player controller.
 *
 * Android provides [MediaPlaybackController] backed by Media3/ExoPlayer.
 * Other platforms will provide their own implementations (if any).
 */
interface PlayerController {
    fun buildPlayer()
    fun buildLocalPlayer()
    fun startPlayback(source: VideoSource, positionSeconds: Long = 0L)
    fun startLocalPlayback(localFileUri: String, source: VideoSource, positionSeconds: Long = 0L)
    fun setErrorCallback(callback: (Throwable) -> Unit)
    fun setPlaybackSpeed(speed: Float)
    fun setPreferredTextLanguage(language: String?)
    fun release()
    fun currentPosition(): Duration
    fun duration(): Duration
    fun isPlaying(): Boolean
    fun playWhenReady()
    fun pause()
    fun seekTo(position: Duration)
}
