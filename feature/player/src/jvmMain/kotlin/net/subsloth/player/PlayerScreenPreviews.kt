package net.subsloth.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.kdroidfilter.composemediaplayer.PreviewableVideoPlayerState
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.playback.PlaybackMode

@Preview
@Composable
private fun PlayerOverlayPreview() {
    PlayerOverlay(
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
        playerState = PreviewableVideoPlayerState(),
        viewModel = PlayerViewModel(
            mediaId = net.subsloth.core.model.media.Media.MediaId.Movie(
                net.subsloth.core.model.identifier.MovieId(1),
            ),
        ),
    )
}
