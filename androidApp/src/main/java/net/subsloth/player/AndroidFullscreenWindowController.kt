package net.subsloth.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Applies the Android window changes that accompany playback fullscreen:
 * landscape orientation and hidden system bars.
 *
 * The player library owns the fullscreen mode itself (it renders the video
 * full-window); this controller only mirrors that state onto the activity
 * window and restores the pre-fullscreen window state when fullscreen ends.
 */
class AndroidFullscreenWindowController(
    private val activity: Activity,
    private val restoreOrientation: Int = activity.requestedOrientation,
) {
    private val insetsController =
        WindowCompat.getInsetsController(activity.window, activity.window.decorView)
    private val originalBehavior: Int = insetsController.systemBarsBehavior
    private val barsInitiallyVisible: Boolean = WindowInsetsCompat
        .toWindowInsetsCompat(activity.window.decorView.rootWindowInsets)
        .isVisible(WindowInsetsCompat.Type.systemBars())
    private var active = false

    fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            active = true
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else if (active) {
            active = false
            activity.requestedOrientation = restoreOrientation
            insetsController.systemBarsBehavior = originalBehavior
            // Bars hidden by someone else before entry stay hidden.
            if (barsInitiallyVisible) {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    /** Restores the pre-fullscreen window state; safe to call repeatedly. */
    fun restore() {
        setFullscreen(false)
    }
}
