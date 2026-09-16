package net.subsloth.player

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import net.subsloth.MainActivity
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue
import android.graphics.Color as AndroidColor

/**
 * Device recipe: the activity window's insets controller must hide both system
 * bars (Android playback fullscreen uses native immersive mode; the player
 * library's fullscreen Dialog cannot cover the status bar).
 *
 * Ignored on CI: insets-controller hide requests are honoured for the focused
 * window only, and the headless CI emulator never grants window focus
 * (see `docs/agent/lessons-learned.md`, lesson 23). Run it on a local
 * emulator: `./gradlew :androidApp:connectedDebugAndroidTest
 * -Pandroid.testInstrumentationRunnerArguments.class=net.subsloth.player.AndroidFullscreenWindowControllerDeviceTest`
 *
 * Covers entering fullscreen (bars hidden), a system/OEM re-show (re-hidden)
 * and exiting fullscreen (bars restored).
 */
@Ignore("Requires window focus; the headless CI emulator never grants it. Run on a local emulator.")
class AndroidFullscreenWindowControllerDeviceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun immersiveHidesBothSystemBars() {
        val activity = composeRule.activity
        composeRule.runOnUiThread {
            activity.addContentView(
                View(activity).apply { setBackgroundColor(AndroidColor.RED) },
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        val controller = AndroidFullscreenWindowController(activity)
        try {
            composeRule.runOnUiThread { controller.setFullscreen(true) }
            composeRule.waitForIdle()
            Thread.sleep(1_000)

            val insets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars())
            val navVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars())
            println("IMMERSIVE-CHECK statusVisible=$statusVisible navVisible=$navVisible")
            assertTrue(statusVisible == false, "status bar still visible")
            assertTrue(navVisible == false, "navigation bar still visible")

            // Simulate the system (or an OEM quirk) re-showing the bars while
            // fullscreen is active; the controller must re-hide them.
            composeRule.runOnUiThread {
                WindowCompat
                    .getInsetsController(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            Thread.sleep(1_000)
            val reShownInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val reShownNav = reShownInsets?.isVisible(WindowInsetsCompat.Type.navigationBars())
            println("IMMERSIVE-CHECK reShownNavVisible=$reShownNav")
            assertTrue(reShownNav == false, "navigation bar re-appeared after a system reveal")

            // Exiting fullscreen must bring both bars back.
            composeRule.runOnUiThread { controller.setFullscreen(false) }
            Thread.sleep(1_000)
            val exitInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
            val exitStatus = exitInsets?.isVisible(WindowInsetsCompat.Type.statusBars())
            val exitNav = exitInsets?.isVisible(WindowInsetsCompat.Type.navigationBars())
            println("IMMERSIVE-CHECK exitStatusVisible=$exitStatus exitNavVisible=$exitNav")
            assertTrue(exitStatus == true, "status bar not restored on exit")
            assertTrue(exitNav == true, "navigation bar not restored on exit")

            // Screenshot for manual inspection.
            val screenshot: Bitmap =
                InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            println("IMMERSIVE-CHECK top=#${Integer.toHexString(screenshot.getPixel(screenshot.width / 2, 4))}")
        } finally {
            composeRule.runOnUiThread { controller.dispose() }
        }
    }
}
