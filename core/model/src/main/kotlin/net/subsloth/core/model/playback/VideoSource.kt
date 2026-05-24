package net.subsloth.core.model.playback

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.Subtitle

/**
 * Ephemeral playback source containing the active stream URLs for a
 * playback session.
 *
 * This model is never persisted. It is created when a playback session
 * starts and discarded when the session ends. The [streamUrl] and related
 * URLs are signed, time-limited resources obtained from the Media API.
 */
@Immutable
data class VideoSource(
    val mediaId: Media.MediaId,
    val streamUrl: String,
    val selectedQuality: Quality,
    val availableQualities: List<Quality>,
    val availableSubtitles: List<Subtitle>,
    val durationSeconds: Long,
    /** Whether this source is streamed online or played from a local file. */
    val playbackMode: PlaybackMode = PlaybackMode.ONLINE,
)
