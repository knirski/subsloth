package net.subsloth.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the system bars on the player library's fullscreen Dialog window.
 *
 * On Android the library lays fullscreen video out in its own `Dialog`; since
 * system-bar visibility is per-window, hiding bars on the Activity window
 * (see [AndroidFullscreenWindowController]) has no effect while that dialog is
 * on top. This composable runs inside the dialog's composition — the host
 * passes it through `PlayerScreen`/`PlayerBridgeSurface`'s `fullscreenEffect`,
 * which is emitted from the surface content — and controls the dialog window
 * itself.
 */
@Composable
fun AndroidDialogFullscreenBars(isFullscreen: Boolean) {
    val view = LocalView.current
    val dialogWindow = remember(view) { (view.parent as? DialogWindowProvider)?.window }

    LaunchedEffect(dialogWindow, isFullscreen) {
        val window = dialogWindow ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (isFullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
