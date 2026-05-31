package net.subsloth.player

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.playback.PlaybackMode

// Previews
@Preview
@Composable
private fun PlayerContentPreview() {
    PlayerContent(
        state = PlayerUiState.Content(
            title = "Sample Video",
            positionSeconds = 0L,
            durationSeconds = 120L,
            isPlaying = false,
            playbackSpeed = 1.0f,
            selectedSubtitle = null,
            availableSubtitles = persistentListOf(),
            availableQualities = persistentListOf(),
            selectedQualityLabel = null,
            nextEpisode = null,
            showNextEpisodePrompt = false,
            playbackError = null,
            playbackMode = PlaybackMode.ONLINE,
            qualityFallbackNotice = null,
            subtitleFallbackNotice = null,
        ),
    )
}
