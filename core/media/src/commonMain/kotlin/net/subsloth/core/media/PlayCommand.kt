package net.subsloth.core.media

import io.github.kdroidfilter.composemediaplayer.SubtitleTrack

data class PlayCommand(val url: String, val positionSeconds: Long = 0L, val subtitleTrack: SubtitleTrack? = null)
