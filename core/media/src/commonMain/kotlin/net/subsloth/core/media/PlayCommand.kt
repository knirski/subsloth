package net.subsloth.core.media

import io.github.kdroidfilter.composemediaplayer.SubtitleTrack

/**
 * A request to the player bridge to open [url].
 *
 * [playbackSpeed] is applied after the source is opened because the
 * underlying player resets its playback parameters when a new media source
 * loads.
 */
data class PlayCommand(
    val url: String,
    val positionSeconds: Long = 0L,
    val subtitleTrack: SubtitleTrack? = null,
    val playbackSpeed: Float = 1f,
)
