package net.subsloth.testing.tvfocus

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertAny
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Suppress("TooManyFunctions")
class TvFocusTestRule : TestRule {

    val composeRule = createComposeRule()

    override fun apply(base: Statement, description: Description): Statement {
        val composed = composeRule.apply(base, description)
        return object : Statement() {
            override fun evaluate() {
                // A headless CI emulator can still be on the lock screen; its
                // window never gets focus, so every focus request is dropped
                // even though composition and clicks work.
                wakeDeviceAndDismissKeyguard()
                composed.evaluate()
            }
        }
    }

    fun setContent(content: @Composable () -> Unit) {
        composeRule.setContent(content)
    }

    fun pressDpadUp() = sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP)

    fun pressDpadDown() = sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)

    fun pressDpadLeft() = sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)

    fun pressDpadRight() = sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)

    fun pressEnter() = sendKeyEvent(KeyEvent.KEYCODE_ENTER)

    fun pressBack() = sendKeyEvent(KeyEvent.KEYCODE_BACK)

    fun pressMediaPlayPause() = sendKeyEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

    fun pressMediaRewind() = sendKeyEvent(KeyEvent.KEYCODE_MEDIA_REWIND)

    fun pressMediaFastForward() = sendKeyEvent(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)

    fun assertFocused(tag: String): SemanticsNodeInteraction = composeRule.onNodeWithTag(tag).assertIsFocused()

    /** Asserts the single node with this accessibility description has focus. */
    fun assertFocusedContentDescription(description: String): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription(description).assertIsFocused()

    /** Waits until the description's node has focus (focus can land a layout later). */
    fun waitUntilFocused(description: String, timeoutMillis: Long = DEFAULT_FOCUS_TIMEOUT_MS) {
        composeRule.waitUntil(timeoutMillis) {
            composeRule.onAllNodesWithContentDescription(description).filter(isFocused())
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** Asserts at least one node with this tag has focus. */
    fun assertAnyFocused(tag: String) {
        composeRule.onAllNodesWithTag(tag).assertAny(isFocused())
    }

    /** Whether any node with this tag currently has focus, for traversal loops. */
    fun hasAnyFocused(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).filter(isFocused()).fetchSemanticsNodes().isNotEmpty()

    private fun sendKeyEvent(keyCode: Int) {
        composeRule.onRoot().performKeyInput {
            keyDown(Key(keyCode))
            keyUp(Key(keyCode))
        }
    }

    internal fun wakeDeviceAndDismissKeyguard() {
        val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
        uiAutomation.executeShellCommand("input keyevent KEYCODE_WAKEUP").close()
        uiAutomation.executeShellCommand("wm dismiss-keyguard").close()
    }

    private companion object {
        const val DEFAULT_FOCUS_TIMEOUT_MS = 5_000L
    }
}
