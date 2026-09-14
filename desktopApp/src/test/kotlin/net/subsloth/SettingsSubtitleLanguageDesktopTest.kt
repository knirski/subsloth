package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.subsloth.settings.SettingsContent
import net.subsloth.settings.SettingsUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsSubtitleLanguageDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun content(language: String?) = SettingsUiState.Content(
        subtitleEnabled = true,
        subtitleLanguage = language,
        quality = null,
        downloadsWifiOnly = true,
    )

    @Test
    fun selectingALanguageReportsItsCode() {
        var selected: String? = "unset"
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = content(language = null),
                    onSubtitleLanguageChanged = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Subtitles language").performClick()
        composeRule.onNodeWithText("English").performClick()

        assertEquals("en", selected)
    }

    @Test
    fun selectingDefaultReportsNull() {
        var selected: String? = "unset"
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(
                    state = content(language = "en"),
                    onSubtitleLanguageChanged = { selected = it },
                )
            }
        }

        composeRule.onNodeWithText("Subtitles language").performClick()
        composeRule.onNodeWithText("Default").performClick()

        assertEquals(null, selected)
    }

    @Test
    fun showsTheStoredLanguageLabel() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(state = content(language = "pl"))
            }
        }

        composeRule.onNodeWithText("Polish").assertIsDisplayed()
    }

    @Test
    fun showsAnUnknownStoredValueAsIs() {
        composeRule.setContent {
            MaterialTheme {
                SettingsContent(state = content(language = "tok"))
            }
        }

        composeRule.onNodeWithText("tok").assertIsDisplayed()
    }
}
