package net.subsloth.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.subsloth.core.ui.FullscreenController

/**
 * Android playback fullscreen: landscape orientation plus hidden system bars.
 *
 * The orientation and system-bar state present before the first fullscreen
 * entry are restored when fullscreen is left (or the player is disposed), so
 * the activity returns to the user's previous configuration.
 */
class AndroidFullscreenController(
    private val activity: Activity,
    private val restoreOrientation: Int = activity.requestedOrientation,
) : FullscreenController {
    private val fullscreenState = mutableStateOf(false)
    private val originalBehavior: Int =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView).systemBarsBehavior
    private val barsInitiallyVisible: Boolean = WindowInsetsCompat
        .toWindowInsetsCompat(activity.window.decorView.rootWindowInsets)
        .isVisible(WindowInsetsCompat.Type.systemBars())

    override val isFullscreen: State<Boolean> = fullscreenState

    override fun toggle() {
        setFullscreen(!fullscreenState.value)
    }

    /** Leaves fullscreen, restoring the pre-fullscreen window state. */
    fun restore() {
        if (fullscreenState.value) {
            setFullscreen(false)
        }
    }

    private fun setFullscreen(enabled: Boolean) {
        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (enabled) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity.requestedOrientation = restoreOrientation
            insetsController.systemBarsBehavior = originalBehavior
            // Bars hidden by someone else before entry stay hidden.
            if (barsInitiallyVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        fullscreenState.value = enabled
    }
}
