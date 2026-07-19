package net.subsloth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.subsloth.settings.DiagnosticsContent
import net.subsloth.settings.DiagnosticsState
import net.subsloth.settings.SettingsContent
import net.subsloth.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings → Diagnostics flow test that navigates from the settings
 * screen to the diagnostics screen via the Diagnostics button.
 *
 * This is a Playwright-style user-journey test: tap a button, verify
 * the next screen renders with the expected state.
 */
@RunWith(AndroidJUnit4::class)
class SettingsFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private sealed interface SettingsFlowScreen {
        data object Settings : SettingsFlowScreen

        data object Diagnostics : SettingsFlowScreen
    }

    @Composable
    private fun SettingsFlowShell(
        screen: SettingsFlowScreen,
        onNavigateToDiagnostics: () -> Unit,
    ) {
        when (screen) {
            SettingsFlowScreen.Settings -> {
                SettingsContent(
                    state =
                        SettingsUiState.Content(
                            subtitleEnabled = true,
                            subtitleLanguage = "English",
                            quality = "1080p",
                            playbackSpeed = 1.5f,
                            downloadsWifiOnly = true,
                            showLogoutCleanup = false,
                            diagnostics = DiagnosticsState.REDACTED,
                        ),
                    onNavigateToDiagnostics = onNavigateToDiagnostics,
                )
            }

            SettingsFlowScreen.Diagnostics -> {
                DiagnosticsContent(
                    state =
                        DiagnosticsState(
                            installedAppVersion = "3.0.0",
                            buildType = "debug",
                            versionCode = "300",
                            releaseChannel = "settings-flow-test",
                            authStateCategory = "authenticated",
                            kodiMode = "Kodi-compatible request mode: enabled",
                        ),
                )
            }
        }
    }

    @Test
    fun settingsToDiagnostics_navigatesAndDisplaysInfo() {
        var screen by mutableStateOf<SettingsFlowScreen>(SettingsFlowScreen.Settings)

        composeTestRule.setContent {
            SettingsFlowShell(
                screen = screen,
                onNavigateToDiagnostics = { screen = SettingsFlowScreen.Diagnostics },
            )
        }

        // On the settings screen
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()
        composeTestRule.onNodeWithText("Subtitles enabled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()

        // Tap Diagnostics
        composeTestRule.onNodeWithText("Diagnostics").performClick()

        // On the diagnostics screen
        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeTestRule.onNodeWithText("Installed app version").assertIsDisplayed()
        composeTestRule.onNodeWithText("3.0.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Release channel").assertIsDisplayed()
        composeTestRule.onNodeWithText("settings-flow-test").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auth state").assertIsDisplayed()
        composeTestRule.onNodeWithText("authenticated").assertIsDisplayed()
    }
}
