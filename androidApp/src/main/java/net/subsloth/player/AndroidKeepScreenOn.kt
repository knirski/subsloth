package net.subsloth.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

/**
 * Keeps the screen on while the player is on screen.
 *
 * ExoPlayer's wake mode keeps the CPU alive, not the display, and the player
 * library does not request `keepScreenOn`; without this flag the system dims
 * and turns the screen off mid-playback like any other idle app.
 */
@Composable
fun AndroidKeepScreenOn(activity: Activity?) {
    DisposableEffect(activity) {
        val window = activity?.window
        if (window == null) {
            onDispose {}
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }
}
