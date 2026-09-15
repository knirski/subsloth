package net.subsloth.player

import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the system bars on the player library's fullscreen Dialog window.
 *
 * On Android the library lays fullscreen video out in its own `Dialog`
 * (`FullScreenLayout`); since system-bar visibility follows the focused
 * window, hiding bars on the Activity window (see
 * [AndroidFullscreenWindowController]) has no effect while that dialog is on
 * top. This composable runs inside the dialog's composition — the host passes
 * it through `PlayerScreen`/`PlayerBridgeSurface`'s `fullscreenEffect`, which
 * the library emits around the fullscreen overlay — and controls the dialog
 * window itself.
 *
 * Two details matter for this to take effect on device:
 * - the `DialogWindowProvider` is not necessarily the view's direct parent, so
 *   the window is resolved by walking up the view tree; and
 * - insets-controller requests issued before the dialog window gains focus are
 *   dropped, so the request is re-applied on every focus gain (first show,
 *   transient system-bar reveal, returning from another window).
 */
@Composable
fun AndroidDialogFullscreenBars(isFullscreen: Boolean) {
    val view = LocalView.current
    val dialogWindow = remember(view) { findDialogWindow(view) }

    DisposableEffect(view, dialogWindow, isFullscreen) {
        val window = dialogWindow
        if (window == null) {
            onDispose {}
        } else {
            val controller = WindowCompat.getInsetsController(window, view)

            fun apply() {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (isFullscreen) {
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                } else {
                    controller.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            val focusListener =
                ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                    if (hasFocus) apply()
                }
            view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
            apply()

            onDispose {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
            }
        }
    }
}

private fun findDialogWindow(view: View): Window? = generateSequence(view) { it.parent as? View }
    .filterIsInstance<DialogWindowProvider>()
    .firstOrNull()
    ?.window
