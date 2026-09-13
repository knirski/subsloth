package net.subsloth.player

import android.app.Activity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Applies the Android window changes that accompany playback fullscreen:
 * hidden system bars.
 *
 * Orientation is owned by the playback screen itself
 * ([PlayerOrientationViewModel]): playback is always sensor landscape and the
 * pre-playback orientation is restored when the player exits. The player
 * library owns the fullscreen mode itself (it renders the video full-window);
 * this controller only mirrors the immersive part onto the activity window and
 * restores the pre-fullscreen window state when fullscreen ends.
 */
class AndroidFullscreenWindowController(
    private val activity: Activity,
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
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else if (active) {
            active = false
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
