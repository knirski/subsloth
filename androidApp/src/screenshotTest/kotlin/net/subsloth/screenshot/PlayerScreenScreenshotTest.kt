package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import io.github.kdroidfilter.composemediaplayer.PreviewableVideoPlayerState
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.player.PlayerOverlay
import net.subsloth.player.PlayerUiState

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Light", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Light", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Light", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun PlayerScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
            )
        }
    }
}

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Dark", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Dark", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Dark", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun PlayerScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
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
            )
        }
    }
}
