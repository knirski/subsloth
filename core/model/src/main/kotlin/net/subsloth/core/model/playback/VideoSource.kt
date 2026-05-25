package net.subsloth.core.model.playback

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.Subtitle

@Immutable
data class VideoSource(
    val mediaId: Media.MediaId,
    val streamUrl: String,
    val selectedQuality: Quality,
    val availableQualities: ImmutableList<Quality>,
    val availableSubtitles: ImmutableList<Subtitle>,
    val durationSeconds: Long,
    val playbackMode: PlaybackMode = PlaybackMode.ONLINE,
)
