package net.subsloth.core.model.playback

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.Subtitle

data class VideoSource(
    val mediaId: Media.MediaId,
    val streamUrl: String,
    val selectedQuality: Quality,
    val availableQualities: ImmutableList<Quality>,
    val availableSubtitles: ImmutableList<Subtitle>,
    val durationSeconds: Long,
    val playbackMode: PlaybackMode = PlaybackMode.ONLINE,
    val localId: LocalMediaIdentifier? = null,
)
