package net.subsloth.core.media

import android.app.Application
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@UnstableApi
class MediaPlaybackController(private val application: Application) {
    private var player: ExoPlayer? = null

    fun buildPlayer(): ExoPlayer {
        release()
        val exoPlayer = ExoPlayer.Builder(application)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(application)
                    .setLiveTargetOffsetMs(DEFAULT_LIVE_OFFSET.inWholeMilliseconds),
            )
            .build()
        player = exoPlayer
        return exoPlayer
    }

    fun getPlayer(): ExoPlayer? = player

    fun release() {
        player?.release()
        player = null
    }

    fun currentPosition(): Duration = player?.currentPosition?.let { pos ->
        if (pos == C.TIME_UNSET) Duration.ZERO else pos.milliseconds
    } ?: Duration.ZERO

    fun duration(): Duration = player?.duration?.let { dur ->
        if (dur == C.TIME_UNSET) Duration.ZERO else dur.milliseconds
    } ?: Duration.ZERO

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun playWhenReady() {
        player?.playWhenReady = true
    }

    fun pause() {
        player?.playWhenReady = false
    }

    fun seekTo(position: Duration) {
        player?.seekTo(position.inWholeMilliseconds)
    }

    private companion object {
        private val DEFAULT_LIVE_OFFSET: Duration = 5.seconds
    }
}
