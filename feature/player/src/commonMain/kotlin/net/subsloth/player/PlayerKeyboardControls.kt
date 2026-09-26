package net.subsloth.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Seconds moved by one arrow-key tap. */
internal const val KEYBOARD_SEEK_SECONDS = 5L

/** How long an arrow key must be held before fast-forward kicks in. */
internal const val KEYBOARD_HOLD_DELAY_MS = 200L

/** Temporary playback speed while an arrow key is held. */
internal const val KEYBOARD_FAST_FORWARD_SPEED = 2f

/**
 * Desktop/web keyboard shortcuts for the player:
 * - Space or K toggles playback;
 * - Left/Right seeks by [KEYBOARD_SEEK_SECONDS], and holding one temporarily
 *   switches to [KEYBOARD_FAST_FORWARD_SPEED] until the key is released;
 * - F toggles fullscreen.
 *
 * The controller owns the hold job and the pre-hold speed so cancellation and
 * disposal always restore playback. It is a plain class (no Compose UI), so
 * the key handling is unit-testable with a fake [VideoPlayerState].
 */
@Stable
internal class PlayerKeyboardController(
    private val playerState: VideoPlayerState,
    private val controllerState: PlayerControllerState,
    private val scope: CoroutineScope,
    private val onToggleFullscreen: () -> Unit,
) {
    var isFastForwarding by mutableStateOf(false)
        private set

    private var holdJob: Job? = null
    private var speedBeforeHold: Float? = null

    fun onKeyEvent(event: KeyEvent): Boolean = onKey(event.key, event.type)

    /** Key handling without the platform event wrapper, so it is unit-testable. */
    internal fun onKey(key: Key, type: KeyEventType): Boolean = when (key) {
        Key.Spacebar, Key.K -> {
            if (type == KeyEventType.KeyUp) {
                if (playerState.isPlaying) playerState.pause() else playerState.play()
                controllerState.show()
            }
            true
        }

        Key.DirectionLeft -> onSeekKey(type, direction = -1L)

        Key.DirectionRight -> onSeekKey(type, direction = 1L)

        Key.F -> {
            if (type == KeyEventType.KeyUp) onToggleFullscreen()
            true
        }

        else -> false
    }

    fun dispose() {
        holdJob?.cancel()
        holdJob = null
        stopFastForward()
    }

    private fun onSeekKey(type: KeyEventType, direction: Long): Boolean = when (type) {
        KeyEventType.KeyDown -> {
            if (holdJob == null) {
                holdJob = scope.launch {
                    delay(KEYBOARD_HOLD_DELAY_MS)
                    startFastForward()
                }
            }
            true
        }

        KeyEventType.KeyUp -> {
            val wasFastForwarding = speedBeforeHold != null
            holdJob?.cancel()
            holdJob = null
            if (wasFastForwarding) {
                stopFastForward()
            } else {
                playerState.seekBy(direction * KEYBOARD_SEEK_SECONDS)
                controllerState.show()
            }
            true
        }

        else -> true
    }

    private fun startFastForward() {
        if (speedBeforeHold != null) return
        speedBeforeHold = playerState.playbackSpeed
        playerState.playbackSpeed = KEYBOARD_FAST_FORWARD_SPEED
        isFastForwarding = true
        controllerState.show()
    }

    private fun stopFastForward() {
        speedBeforeHold?.let {
            playerState.playbackSpeed = it
            speedBeforeHold = null
        }
        isFastForwarding = false
    }
}

@Composable
internal fun rememberPlayerKeyboardController(
    playerState: VideoPlayerState,
    controllerState: PlayerControllerState,
    onToggleFullscreen: () -> Unit,
): PlayerKeyboardController {
    val scope = rememberCoroutineScope()
    val currentOnToggleFullscreen = rememberUpdatedState(onToggleFullscreen)
    val controller = remember(playerState, controllerState) {
        PlayerKeyboardController(
            playerState = playerState,
            controllerState = controllerState,
            scope = scope,
            onToggleFullscreen = { currentOnToggleFullscreen.value() },
        )
    }
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    return controller
}
