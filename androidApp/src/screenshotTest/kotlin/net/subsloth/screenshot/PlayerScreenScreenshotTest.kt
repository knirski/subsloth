package net.subsloth.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kdroidfilter.composemediaplayer.PreviewableVideoPlayerState
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.player.PlayerOverlay
import net.subsloth.player.PlayerUiState
import net.subsloth.player.PlayerViewModel

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun PlayerScreenScreenshot() {
    MaterialTheme {
        PlayerOverlay(
            state =
                PlayerUiState.Content(
                    title = "Sample Video Title",
                    positionSeconds = 3600L,
                    durationSeconds = 7200L,
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
            viewModel =
                PlayerViewModel(
                    mediaId =
                        net.subsloth.core.model.media.Media.MediaId.Movie(
                            net.subsloth.core.model.identifier
                                .MovieId(1),
                        ),
                ),
        )
    }
}
