package subsloth.testing.tvfocus

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

@Suppress("TooManyFunctions")
class TvFocusTestRule : TestRule {

    val composeRule = createComposeRule()

    override fun apply(base: Statement, description: Description): Statement = composeRule.apply(base, description)

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

    private fun sendKeyEvent(keyCode: Int) {
        composeRule.onRoot().performKeyInput {
            keyDown(Key(keyCode))
            keyUp(Key(keyCode))
        }
    }
}
