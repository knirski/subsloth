package net.subsloth.player

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The player screen must hold [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON]
 * while it is on screen, so the display cannot dim or sleep mid-playback.
 */
class AndroidKeepScreenOnTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keepScreenOnIsSetWhileThePlayerIsOnScreen() {
        val playerVisible = mutableStateOf(true)
        var activity: Activity? = null
        composeRule.setContent {
            val current = requireNotNull(LocalActivity.current) { "test host has no activity" }
            activity = remember { current }
            if (playerVisible.value) {
                AndroidKeepScreenOn(current)
            }
        }
        composeRule.waitForIdle()

        val target = requireNotNull(activity)
        assertTrue(
            target.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
            "FLAG_KEEP_SCREEN_ON not set while the player is on screen",
        )

        composeRule.runOnUiThread { playerVisible.value = false }
        composeRule.waitForIdle()

        assertEquals(
            0,
            target.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            "FLAG_KEEP_SCREEN_ON left set after the player left the screen",
        )
    }
}
