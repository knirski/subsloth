package net.subsloth.core.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Platform hook for the playback fullscreen mode.
 *
 * Platform roots provide an implementation: Android hides the system bars and
 * forces landscape orientation, desktop toggles the window placement, and the
 * browser requests the Fullscreen API. The no-op default keeps previews and
 * platform tests free of windowing concerns while still rendering the control.
 */
interface FullscreenController {
    /** Whether the platform is currently in fullscreen mode. */
    val isFullscreen: State<Boolean>

    /** Enters fullscreen when windowed, leaves it when already fullscreen. */
    fun toggle()
}

/** The [FullscreenController] installed by the current platform root. */
val LocalFullscreenController = staticCompositionLocalOf<FullscreenController> {
    NoOpFullscreenController
}

private object NoOpFullscreenController : FullscreenController {
    override val isFullscreen: State<Boolean> = mutableStateOf(false)

    override fun toggle() = Unit
}
