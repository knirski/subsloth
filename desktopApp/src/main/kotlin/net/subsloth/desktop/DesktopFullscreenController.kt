package net.subsloth.desktop

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import net.subsloth.core.ui.FullscreenController

/**
 * Desktop playback fullscreen: toggles the window between
 * [WindowPlacement.Fullscreen] and the placement it had before, so leaving
 * fullscreen restores a maximized window as maximized.
 *
 * Deriving [isFullscreen] from the window state keeps the control in sync
 * when the placement changes outside the button (window manager shortcuts,
 * F11-style toggles).
 */
class DesktopFullscreenController(private val windowState: WindowState) : FullscreenController {
    private var placementBeforeFullscreen: WindowPlacement? = null

    override val isFullscreen: State<Boolean> = derivedStateOf {
        windowState.placement == WindowPlacement.Fullscreen
    }

    override fun toggle() {
        if (windowState.placement == WindowPlacement.Fullscreen) {
            windowState.placement = placementBeforeFullscreen ?: WindowPlacement.Floating
            placementBeforeFullscreen = null
        } else {
            placementBeforeFullscreen = windowState.placement
            windowState.placement = WindowPlacement.Fullscreen
        }
    }
}
