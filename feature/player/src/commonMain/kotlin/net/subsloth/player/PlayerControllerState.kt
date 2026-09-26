package net.subsloth.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Visibility state for the player chrome.
 *
 * The chrome is visible while [isFullVisible] (toggled by user taps) or while
 * at least one [alwaysOn] requester is active — an open picker, an in-flight
 * slider drag, a hovered control. Requesters are identity-based, so a
 * component can say "keep the controls visible while I exist" without knowing
 * about the auto-hide timer or about other components; precedence is a single
 * derived rule instead of a growing list of conditions at the timer.
 */
@Stable
class PlayerControllerState(initiallyVisible: Boolean = true) {
    var isFullVisible by mutableStateOf(initiallyVisible)
        private set

    private val alwaysOnRequesters = mutableStateListOf<Any>()

    /**
     * Bumped by [show] so the auto-hide timer restarts even when the chrome is
     * already visible (a skip press, for example).
     */
    var lastInteraction by mutableIntStateOf(0)
        private set

    /** Whether a requester currently needs the chrome to stay on screen. */
    val alwaysOn: Boolean by derivedStateOf { alwaysOnRequesters.isNotEmpty() }

    val isVisible: Boolean by derivedStateOf { isFullVisible || alwaysOn }

    /** Shows the chrome and restarts the auto-hide timer. */
    fun show() {
        isFullVisible = true
        lastInteraction++
    }

    fun hide() {
        isFullVisible = false
    }

    fun toggle() {
        if (isVisible) hide() else show()
    }

    /**
     * Registers ([alwaysOn] = true) or releases ([alwaysOn] = false) a request
     * to keep the chrome visible. [requester] must be a stable identity, so
     * requesters with equal content never collide.
     */
    fun setAlwaysOn(requester: Any, alwaysOn: Boolean) {
        if (alwaysOn) {
            if (requester !in alwaysOnRequesters) alwaysOnRequesters.add(requester)
        } else {
            alwaysOnRequesters.remove(requester)
        }
    }
}

@Composable
fun rememberPlayerControllerState(): PlayerControllerState = remember { PlayerControllerState() }

/**
 * Keeps the chrome visible for as long as [active] is true. [owner] must be a
 * stable identity (typically `remember { Any() }`); releasing is idempotent,
 * so flipping [active] or leaving the composition cannot leak a request.
 */
@Composable
fun PlayerControllerState.KeepVisibleWhile(owner: Any, active: Boolean) {
    DisposableEffect(this, owner, active) {
        if (active) setAlwaysOn(owner, true)
        onDispose { setAlwaysOn(owner, false) }
    }
}
