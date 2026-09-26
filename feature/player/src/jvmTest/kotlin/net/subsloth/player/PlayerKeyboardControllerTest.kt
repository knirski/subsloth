package net.subsloth.player

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import io.github.kdroidfilter.composemediaplayer.PreviewableVideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerKeyboardControllerTest {

    @Test
    fun `space toggles playback on key up`() = runTest {
        val player = RecordingKeyboardVideoPlayerState(initiallyPlaying = true)
        val controller = controller(player)

        controller.onKey(Key.Spacebar, KeyEventType.KeyDown)
        assertThat(player.pauseCalls).isEqualTo(0)

        controller.onKey(Key.Spacebar, KeyEventType.KeyUp)
        assertThat(player.pauseCalls).isEqualTo(1)

        player.isPlaying = false
        controller.onKey(Key.Spacebar, KeyEventType.KeyUp)
        assertThat(player.playCalls).isEqualTo(1)
    }

    @Test
    fun `right arrow tap seeks forward five seconds`() = runTest {
        val player = RecordingKeyboardVideoPlayerState()
        val controller = controller(player)

        controller.onKey(Key.DirectionRight, KeyEventType.KeyDown)
        advanceTimeBy(KEYBOARD_HOLD_DELAY_MS / 2)
        controller.onKey(Key.DirectionRight, KeyEventType.KeyUp)

        assertThat(player.seekValues.single()).isWithin(0.01f).of(35f / 120f * 1000f)
    }

    @Test
    fun `left arrow tap seeks backward five seconds`() = runTest {
        val player = RecordingKeyboardVideoPlayerState()
        val controller = controller(player)

        controller.onKey(Key.DirectionLeft, KeyEventType.KeyDown)
        advanceTimeBy(KEYBOARD_HOLD_DELAY_MS / 2)
        controller.onKey(Key.DirectionLeft, KeyEventType.KeyUp)

        assertThat(player.seekValues.single()).isWithin(0.01f).of(25f / 120f * 1000f)
    }

    @Test
    fun `holding an arrow accelerates until release`() = runTest {
        val player = RecordingKeyboardVideoPlayerState()
        val controller = controller(player)

        controller.onKey(Key.DirectionRight, KeyEventType.KeyDown)
        advanceTimeBy(KEYBOARD_HOLD_DELAY_MS + 10)

        assertThat(controller.isFastForwarding).isTrue()
        assertThat(player.playbackSpeed).isEqualTo(KEYBOARD_FAST_FORWARD_SPEED)

        controller.onKey(Key.DirectionRight, KeyEventType.KeyUp)

        assertThat(controller.isFastForwarding).isFalse()
        assertThat(player.playbackSpeed).isEqualTo(1f)
        assertThat(player.seekValues).isEmpty()
    }

    @Test
    fun `dispose restores the speed after a hold`() = runTest {
        val player = RecordingKeyboardVideoPlayerState()
        val controller = controller(player)

        controller.onKey(Key.DirectionRight, KeyEventType.KeyDown)
        advanceTimeBy(KEYBOARD_HOLD_DELAY_MS + 10)
        controller.dispose()

        assertThat(controller.isFastForwarding).isFalse()
        assertThat(player.playbackSpeed).isEqualTo(1f)
    }

    @Test
    fun `F toggles fullscreen on key up`() = runTest {
        val player = RecordingKeyboardVideoPlayerState()
        var toggles = 0
        val controller = PlayerKeyboardController(
            playerState = player,
            controllerState = PlayerControllerState(),
            scope = this,
            onToggleFullscreen = { toggles++ },
        )

        controller.onKey(Key.F, KeyEventType.KeyDown)
        assertThat(toggles).isEqualTo(0)

        controller.onKey(Key.F, KeyEventType.KeyUp)
        assertThat(toggles).isEqualTo(1)
    }

    @Test
    fun `unhandled keys are ignored`() = runTest {
        val player = RecordingKeyboardVideoPlayerState()
        val controller = controller(player)

        assertThat(controller.onKey(Key.M, KeyEventType.KeyUp)).isFalse()
    }

    private fun TestScope.controller(player: VideoPlayerState): PlayerKeyboardController = PlayerKeyboardController(
        playerState = player,
        controllerState = PlayerControllerState(),
        scope = this,
        onToggleFullscreen = {},
    )
}

private class RecordingKeyboardVideoPlayerState(initiallyPlaying: Boolean = true) :
    VideoPlayerState by PreviewableVideoPlayerState(
        isPlaying = initiallyPlaying,
        currentTime = 30.0,
        duration = 120.0,
        sliderPos = 250f,
        playbackSpeed = 1f,
    ) {
    override var isPlaying = initiallyPlaying
    var playCalls = 0
    var pauseCalls = 0
    val seekValues = mutableListOf<Float>()

    override fun play() {
        playCalls++
    }

    override fun pause() {
        pauseCalls++
    }

    override fun seekTo(value: Float) {
        seekValues += value
    }
}
