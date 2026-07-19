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
import net.subsloth.screenshot.DEVICE_PHONE
import net.subsloth.screenshot.DEVICE_TABLET
import net.subsloth.screenshot.DEVICE_TV

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
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

@PreviewTest
@Preview(name = "Phone Dark", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Dark", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Dark", device = DEVICE_TV, showBackground = true)
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
