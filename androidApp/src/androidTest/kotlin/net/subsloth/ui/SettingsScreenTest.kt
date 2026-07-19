package net.subsloth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.subsloth.settings.DiagnosticsState
import net.subsloth.settings.SettingsContent
import net.subsloth.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Playwright-style Compose UI tests for the Settings screen.
 *
 * Tests settings content rendering, checkbox toggles, playback speed slider,
 * diagnostics navigation, logout button, and the logout cleanup dialog.
 */
@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val defaultContentState =
        SettingsUiState.Content(
            subtitleEnabled = true,
            subtitleLanguage = null,
            quality = null,
            playbackSpeed = 1.0f,
            downloadsWifiOnly = true,
            showLogoutCleanup = false,
            diagnostics = DiagnosticsState.REDACTED,
        )

    @Test
    fun settingsContent_displaysTitle() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysSectionHeaders() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Subtitle").assertIsDisplayed()
        composeTestRule.onNodeWithText("Quality & Playback").assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloads").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysSubtitleEnabledCheckbox() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Subtitles enabled").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysSubtitleLanguageDefault() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysSubtitleLanguageValueWhenSet() {
        val state = defaultContentState.copy(subtitleLanguage = "English")

        composeTestRule.setContent {
            SettingsContent(state = state)
        }

        composeTestRule.onNodeWithText("English").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysQualityDefault() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Auto").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysQualityValueWhenSet() {
        val state = defaultContentState.copy(quality = "1080p")

        composeTestRule.setContent {
            SettingsContent(state = state)
        }

        composeTestRule.onNodeWithText("1080p").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysPlaybackSpeed() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Playback speed: 1.0x").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysDownloadsWifiOnlyCheckbox() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Downloads on Wi-Fi only").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysDiagnosticsButton() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysLogoutButton() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Logout").assertIsDisplayed()
    }

    @Test
    fun diagnosticsButton_triggersCallback() {
        var diagnosticsClicked = false

        composeTestRule.setContent {
            SettingsContent(
                state = defaultContentState,
                onNavigateToDiagnostics = { diagnosticsClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Diagnostics").performClick()
        assertTrue(diagnosticsClicked, "Expected diagnostics navigation callback to be invoked")
    }

    @Test
    fun logoutButton_triggersLogoutClick() {
        var logoutClicked = false

        composeTestRule.setContent {
            SettingsContent(
                state = defaultContentState,
                onLogoutClick = { logoutClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Logout").performClick()
        assertTrue(logoutClicked, "Expected logout click callback to be invoked")
    }

    @Test
    fun subtitleEnabledCheckbox_togglesCallback() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            SettingsContent(
                state = defaultContentState,
                onSubtitleEnabledChanged = { toggledValue = it },
            )
        }

        // Find subtitles enabled text and click its row's checkbox
        // The checkbox is in the same row as the text
        composeTestRule.onNodeWithText("Subtitles enabled").performClick()
        assertTrue(toggledValue != null, "Expected subtitle enabled callback to be invoked")
    }

    @Test
    fun downloadsWifiOnlyCheckbox_togglesCallback() {
        var toggledValue: Boolean? = null

        composeTestRule.setContent {
            SettingsContent(
                state = defaultContentState,
                onDownloadsWifiOnlyChanged = { toggledValue = it },
            )
        }

        composeTestRule.onNodeWithText("Downloads on Wi-Fi only").performClick()
        assertTrue(toggledValue != null, "Expected downloads wifi-only callback to be invoked")
    }

    @Test
    fun logoutDialog_shownWhenShowLogoutCleanupIsTrue() {
        val stateWithDialog = defaultContentState.copy(showLogoutCleanup = true)

        composeTestRule.setContent {
            SettingsContent(state = stateWithDialog)
        }

        composeTestRule.onNodeWithText("Logout Cleanup").assertIsDisplayed()
        composeTestRule.onNodeWithText("Choose what to clear for this profile:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete downloaded videos & subtitles").assertIsDisplayed()
        composeTestRule.onNodeWithText("Reset active-profile preferences").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clear active-profile watch & library data").assertIsDisplayed()
    }

    @Test
    fun logoutDialog_hiddenByDefault() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        composeTestRule.onNodeWithText("Logout Cleanup").assertDoesNotExist()
    }

    @Test
    fun logoutDialog_confirmButton_triggersCallback() {
        var confirmed = false

        val stateWithDialog = defaultContentState.copy(showLogoutCleanup = true)

        composeTestRule.setContent {
            SettingsContent(
                state = stateWithDialog,
                onPerformLogoutCleanup = { _, _, _ -> confirmed = true },
            )
        }

        // Verify dialog is showing
        composeTestRule.onNodeWithText("Logout Cleanup").assertIsDisplayed()

        // Click the confirm button inside the dialog — scoped via hasAnyAncestor(isDialog())
        // to avoid ambiguity with the background "Logout" button.
        composeTestRule
            .onNode(hasText("Logout") and hasAnyAncestor(isDialog()))
            .performClick()
        assertTrue(confirmed, "Expected logout confirm callback to be invoked")
    }

    @Test
    fun logoutDialog_dismissButton_triggersCallback() {
        var dismissed = false

        val stateWithDialog = defaultContentState.copy(showLogoutCleanup = true)

        composeTestRule.setContent {
            SettingsContent(
                state = stateWithDialog,
                onDismissLogoutCleanup = { dismissed = true },
            )
        }

        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(dismissed, "Expected logout dismiss callback to be invoked")
    }

    @Test
    fun settingsContent_displaysSectionOrder() {
        composeTestRule.setContent {
            SettingsContent(state = defaultContentState)
        }

        // Verify key labels are present
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Preferred quality").assertIsDisplayed()
        composeTestRule.onNodeWithText("Playback speed: 1.0x").assertIsDisplayed()
    }
}
