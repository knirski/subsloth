package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import io.github.kdroidfilter.composemediaplayer.PreviewableVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.player.PLAYER_OVERLAY_TAG
import net.subsloth.player.PLAYER_SWIPE_PROGRESS_TAG
import net.subsloth.player.PlayerOverlay
import net.subsloth.player.PlayerUiState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Slider-value tolerance for the 0..1000 scale used by the player library. */
private const val SEEK_TOLERANCE = 0.01f

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
    fun playerOverlay_skipsBackwardAndForwardByTenSeconds() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
                        positionSeconds = 30L,
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
                    playerState = playerState,
                )
            }
        }

        // The controls carry the full wording as their accessibility
        // description; the compact label is decorative.
        composeRule.onNodeWithContentDescription("Rewind 10 seconds").performClick()
        assertEquals(20f / 120f * 1000f, playerState.lastSeekValue!!, SEEK_TOLERANCE)

        // The second skip starts from the first target, proving consecutive
        // skips accumulate even when the backend has not polled a new position.
        composeRule.onNodeWithContentDescription("Fast forward 10 seconds").performClick()
        assertEquals(30f / 120f * 1000f, playerState.lastSeekValue!!, SEEK_TOLERANCE)
    }

    @Test
    fun playerOverlay_disablesSkipControlsWhenDurationIsUnknown() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 0.0, durationSeconds = 0.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = PlayerUiState.Content(
                        title = "Test",
                        positionSeconds = 0L,
                        durationSeconds = 0L,
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
                    playerState = playerState,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rewind 10 seconds").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Fast forward 10 seconds").assertIsNotEnabled()
    }

    @Test
    fun playerOverlay_mouseClickTogglesPlayback() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(isPlaying = true), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performMouseInput { click(center) }
        // A click waits out the double-click window before it fires.
        composeRule.mainClock.advanceTimeBy(500)

        assertEquals(1, playerState.pauseCalls)
    }

    @Test
    fun playerOverlay_touchTapTogglesControls() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(isPlaying = true), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput { click(center) }
        // A touch tap waits out the double-tap window before it fires.
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Pause").assertDoesNotExist()

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(500)
        composeRule.onNodeWithText("Pause").assertIsDisplayed()
    }

    @Test
    fun playerOverlay_skipPressRestartsAutoHide() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(isPlaying = true), playerState = playerState)
            }
        }

        composeRule.mainClock.advanceTimeBy(3_500)
        composeRule.onNodeWithContentDescription("Fast forward 10 seconds").performClick()

        composeRule.mainClock.advanceTimeBy(3_500)
        composeRule.onNodeWithContentDescription("Fast forward 10 seconds").assertIsDisplayed()

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.onNodeWithContentDescription("Fast forward 10 seconds").assertDoesNotExist()
    }

    @Test
    fun playerOverlay_swipeUpCancelsSeek() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        seekSlider().performTouchInput {
            down(center)
            // Start a horizontal drag first (a mostly-vertical gesture is
            // treated as a tap and commits immediately), then move up far
            // enough to arm the cancel.
            moveBy(Offset(60f, 0f))
            moveBy(Offset(0f, -160f))
            moveBy(Offset(0f, -160f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(playerState.seekValues.isEmpty(), "a cancelled swipe must not seek")
    }

    @Test
    fun playerOverlay_sliderDragSeeksOnRelease() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        seekSlider().performTouchInput {
            down(center)
            moveBy(Offset(150f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(1, playerState.seekValues.size)
        assertTrue(playerState.seekValues.single() > 250f, "dragging right should seek forward")
    }

    @Test
    fun playerOverlay_keyboardSpaceTogglesPlayback() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = playerContent(isPlaying = true),
                    playerState = playerState,
                    enableKeyboardShortcuts = true,
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performKeyInput { pressKey(Key.Spacebar) }
        composeRule.waitForIdle()

        assertEquals(1, playerState.pauseCalls)
    }

    @Test
    fun playerOverlay_restoresFocusAfterPickerCloses() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(
                    state = playerContent(isPlaying = true),
                    playerState = playerState,
                    enableKeyboardShortcuts = true,
                )
            }
        }

        composeRule.onNodeWithContentDescription("Speed").performClick()
        composeRule.onNodeWithText("2.0x").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).assertIsFocused()
    }

    @Test
    fun playerOverlay_doubleTapLeftSeeksBackward() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            doubleClick(Offset(width * 0.2f, height * 0.5f))
        }
        composeRule.waitForIdle()

        assertEquals(20f / 120f * 1000f, playerState.seekValues.single(), SEEK_TOLERANCE)
    }

    @Test
    fun playerOverlay_doubleTapRightSeeksForward() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            doubleClick(Offset(width * 0.8f, height * 0.5f))
        }
        composeRule.waitForIdle()

        assertEquals(40f / 120f * 1000f, playerState.seekValues.single(), SEEK_TOLERANCE)
    }

    @Test
    fun playerOverlay_doubleTapMiddleTogglesPlayback() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(isPlaying = true), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            doubleClick(Offset(width * 0.5f, height * 0.5f))
        }
        composeRule.waitForIdle()

        assertEquals(1, playerState.pauseCalls)
    }

    @Test
    fun playerOverlay_longPressFastForwardsAndRestores() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(isPlaying = true), playerState = playerState)
            }
        }

        val overlay = composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG)
        overlay.performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(1_000)
        assertEquals(2.5f, playerState.playbackSpeed)

        overlay.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals(1f, playerState.playbackSpeed)
    }

    @Test
    fun playerOverlay_horizontalSwipeSeeksOnRelease() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            down(center)
            moveBy(Offset(200f, 0f))
            moveBy(Offset(100f, 0f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(playerState.seekValues.isNotEmpty(), "a horizontal swipe should seek")
        assertTrue(
            playerState.seekValues.last() > 250f,
            "swiping right should seek forward, got ${playerState.seekValues.last()}",
        )
    }

    @Test
    fun playerOverlay_swipeUpCancelsScrub() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            down(center)
            moveBy(Offset(150f, 0f))
            moveBy(Offset(0f, -160f))
            moveBy(Offset(0f, -160f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(playerState.seekValues.isEmpty(), "a cancelled scrub must not seek")
    }

    @Test
    fun playerOverlay_verticalSwipeOnRightChangesVolume() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(), playerState = playerState)
            }
        }

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            down(Offset(width * 0.8f, height * 0.5f))
            moveBy(Offset(0f, 120f))
            moveBy(Offset(0f, 120f))
            moveBy(Offset(0f, 120f))
            up()
        }
        composeRule.waitForIdle()

        assertTrue(
            playerState.volume < 1f,
            "swiping down on the right third should lower the volume, got ${playerState.volume}",
        )
    }

    @Test
    fun playerOverlay_swipeShowsDetachedProgressWhenControlsHidden() {
        val playerState = RecordingSeekVideoPlayerState(positionSeconds = 30.0, durationSeconds = 120.0)
        composeRule.setContent {
            MaterialTheme {
                PlayerOverlay(state = playerContent(isPlaying = true), playerState = playerState)
            }
        }

        // A touch tap hides the chrome (waiting out the double-tap window).
        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput { click(center) }
        composeRule.mainClock.advanceTimeBy(500)

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput {
            down(center)
            moveBy(Offset(150f, 0f))
            moveBy(Offset(50f, 0f))
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PLAYER_SWIPE_PROGRESS_TAG).assertExists()

        composeRule.onNodeWithTag(PLAYER_OVERLAY_TAG).performTouchInput { up() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PLAYER_SWIPE_PROGRESS_TAG).assertDoesNotExist()
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

        // The control bar shows the compact label ("Full"/"Exit") with the
        // full wording as its accessibility description.
        composeRule.onNodeWithContentDescription("Fullscreen").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Fullscreen").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Exit fullscreen").assertIsDisplayed()
        assertTrue(playerState.isFullscreen, "player state should be fullscreen")
        assertEquals(true, reportedFullscreen, "host should observe the fullscreen change")
    }

    private fun playerContent(isPlaying: Boolean = false, positionSeconds: Long = 30L, durationSeconds: Long = 120L) =
        PlayerUiState.Content(
            title = "Test",
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
            isPlaying = isPlaying,
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
        )

    /** The only node with progress-bar semantics is the seek slider. */
    private fun seekSlider() = composeRule.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
    )

    private class FakeFullscreenVideoPlayerState : VideoPlayerState by PreviewableVideoPlayerState() {
        override var isFullscreen: Boolean by mutableStateOf(false)

        override fun toggleFullscreen() {
            isFullscreen = !isFullscreen
        }
    }

    private class RecordingSeekVideoPlayerState(
        positionSeconds: Double,
        durationSeconds: Double,
        initiallyPlaying: Boolean = true,
    ) : VideoPlayerState by PreviewableVideoPlayerState(
        currentTime = positionSeconds,
        duration = durationSeconds,
        isPlaying = initiallyPlaying,
        sliderPos = if (durationSeconds > 0.0) {
            (positionSeconds / durationSeconds * 1000.0).toFloat()
        } else {
            0f
        },
    ) {
        override var isPlaying = initiallyPlaying
        var lastSeekValue: Float? = null
        val seekValues = mutableListOf<Float>()
        var playCalls = 0
        var pauseCalls = 0

        override fun seekTo(value: Float) {
            lastSeekValue = value
            seekValues += value
        }

        override fun play() {
            playCalls++
        }

        override fun pause() {
            pauseCalls++
        }
    }
}
