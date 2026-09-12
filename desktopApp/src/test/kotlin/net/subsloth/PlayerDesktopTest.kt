package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.kdroidfilter.composemediaplayer.PreviewableVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.player.PlayerOverlay
import net.subsloth.player.PlayerUiState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun playerOverlay_displaysTitle() {
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test Video",
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
                )
            }
        }

        composeRule.onNodeWithText("Test Video").assertIsDisplayed()
    }

    @Test
    fun playerOverlay_showsPlayButton_whenNotPlaying() {
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
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
                )
            }
        }

        composeRule.onNodeWithText("Play").assertIsDisplayed()
        composeRule.onNodeWithText("Play").assertHasClickAction()
    }

    @Test
    fun playerOverlay_showsPauseButton_whenPlaying() {
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
                        positionSeconds = 0L,
                        durationSeconds = 120L,
                        isPlaying = true,
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

        composeRule.onNodeWithText("Pause").assertIsDisplayed()
        composeRule.onNodeWithText("Pause").assertHasClickAction()
    }

    @Test
    fun playerOverlay_showsErrorAndRetry() {
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
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
                        playbackError = PlaybackError.Recoverable(),
                        playbackMode = PlaybackMode.ONLINE,
                        qualityFallbackNotice = null,
                        subtitleFallbackNotice = null,
                    ),
                    playerState = PreviewableVideoPlayerState(),
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Retry").assertIsDisplayed()
        composeRule.onNodeWithText("Back to details").assertIsDisplayed()
    }

    @Test
    fun playerOverlay_callsOnRetry() {
        var retried = false
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
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
                        playbackError = PlaybackError.Recoverable(),
                        playbackMode = PlaybackMode.ONLINE,
                        qualityFallbackNotice = null,
                        subtitleFallbackNotice = null,
                    ),
                    playerState = PreviewableVideoPlayerState(),
                    onRetry = { retried = true },
                )
            }
        }

        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retried, "onRetry should have been called")
    }

    @Test
    fun playerOverlay_displaysPositionAndDuration() {
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
                        positionSeconds = 65L,
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
                )
            }
        }

        composeRule.onNodeWithText("01:05").assertIsDisplayed()
        composeRule.onNodeWithText("02:00").assertIsDisplayed()
    }

    @Test
    fun playerOverlay_togglesFullscreen() {
        val playerState = FakeFullscreenVideoPlayerState()
        var reportedFullscreen: Boolean? = null
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
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
                    playerState = playerState,
                    onFullscreenChanged = { reportedFullscreen = it },
                )
            }
        }

        composeRule.onNodeWithText("Fullscreen").assertIsDisplayed()
        composeRule.onNodeWithText("Fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Exit fullscreen").assertIsDisplayed()
        assertTrue(playerState.isFullscreen, "player state should be fullscreen")
        assertEquals(true, reportedFullscreen, "host should observe the fullscreen change")
    }

    private class FakeFullscreenVideoPlayerState : VideoPlayerState by PreviewableVideoPlayerState() {
        override var isFullscreen: Boolean by mutableStateOf(false)

        override fun toggleFullscreen() {
            isFullscreen = !isFullscreen
        }
    }
}
