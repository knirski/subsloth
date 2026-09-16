package net.subsloth.player

import android.app.Activity
import android.view.ViewTreeObserver
import androidx.core.view.ViewCompat
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
    private var active = false

    /**
     * Insets-controller requests made before a window gains focus are dropped;
     * re-apply whenever this activity window regains focus while fullscreen is
     * active (returning to the app, dismissing a transient bar reveal).
     */
    private val focusListener =
        ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus && active) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

    /**
     * Re-assert immersive mode whenever the system reports the bars visible
     * again while fullscreen is active. Some devices re-show the navigation
     * bar on their own (OEM behaviour, transient reveals, insets re-dispatch),
     * and the controller must keep the screen fullscreen as requested.
     */
    private val insetsListener =
        ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView) { _, insets ->
            if (active &&
                (insets.isVisible(WindowInsetsCompat.Type.statusBars()) ||
                    insets.isVisible(WindowInsetsCompat.Type.navigationBars()))
            ) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
            }
            insets
        }

    init {
        activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
    }

    fun setFullscreen(fullscreen: Boolean) {
        if (fullscreen) {
            active = true
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else if (active) {
            active = false
            insetsController.systemBarsBehavior = originalBehavior
            // Always restore the bars this controller hid. Deciding from a
            // captured "were they visible before?" flag is unreliable: the
            // decor view's insets can be missing when the controller is
            // constructed, and a stale flag left users in a player with no
            // navigation bar after leaving fullscreen.
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    /** Restores the pre-fullscreen window state; safe to call repeatedly. */
    fun restore() {
        setFullscreen(false)
    }

    /** Restores the window state and stops listening; call when leaving the player. */
    fun dispose() {
        restore()
        activity.window.decorView.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
        ViewCompat.setOnApplyWindowInsetsListener(activity.window.decorView, null)
    }
}
