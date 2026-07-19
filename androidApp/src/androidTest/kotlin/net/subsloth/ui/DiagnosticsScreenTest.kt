package net.subsloth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.subsloth.settings.DiagnosticsContent
import net.subsloth.settings.DiagnosticsState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Playwright-style Compose UI tests for the Diagnostics screen.
 *
 * Tests the read-only diagnostics display: version info, device info,
 * network info, cache/storage info, and conditional field visibility.
 */
@RunWith(AndroidJUnit4::class)
class DiagnosticsScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun diagnostics_displaysTitle() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Diagnostics").assertIsDisplayed()
    }

    @Test
    fun diagnostics_displaysSectionHeaders() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Version information").assertIsDisplayed()
        composeTestRule.onNodeWithText("Device").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network").assertIsDisplayed()
        composeTestRule.onNodeWithText("Cache & Storage").assertIsDisplayed()
    }

    @Test
    fun diagnostics_displaysVersionFields() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Installed app version").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.0.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("Build type").assertIsDisplayed()
        composeTestRule.onNodeWithText("debug").assertIsDisplayed()
        composeTestRule.onNodeWithText("Version code").assertIsDisplayed()
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Release channel").assertIsDisplayed()
        composeTestRule.onNodeWithText("debug-sideload").assertIsDisplayed()
    }

    @Test
    fun diagnostics_displaysNetworkFields() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("API base URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("redacted").assertIsDisplayed()
        composeTestRule.onNodeWithText("Auth state").assertIsDisplayed()
        composeTestRule.onNodeWithText("unknown").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kodi mode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Kodi-compatible request mode: enabled").assertIsDisplayed()
    }

    @Test
    fun diagnostics_displaysFooterDisclaimer() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule
            .onNodeWithText(
                "No export, share, or copy actions are available in v1.",
            ).assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_gitSha_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        // The entire row is only rendered when gitSha is not null
        composeTestRule.onNodeWithText("Git SHA").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_gitSha_shownWhenSet() {
        val state = DiagnosticsState(gitSha = "abc123def456")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Git SHA").assertIsDisplayed()
        composeTestRule.onNodeWithText("abc123def456").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_cacheAge_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        // The "Cache age" label row is only rendered when cacheAge is not null
        composeTestRule.onNodeWithText("Cache age").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_cacheAge_shownWhenSet() {
        val state = DiagnosticsState(cacheAge = "2 hours ago")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Cache age").assertIsDisplayed()
        composeTestRule.onNodeWithText("2 hours ago").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_lastRefresh_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Last refresh").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_lastRefresh_shownWhenSet() {
        val state = DiagnosticsState(lastRefreshTime = "2024-01-15 10:30")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Last refresh").assertIsDisplayed()
        composeTestRule.onNodeWithText("2024-01-15 10:30").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_downloadQueue_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Download queue").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_downloadQueue_shownWhenSet() {
        val state = DiagnosticsState(downloadQueueCounts = "3 active, 2 paused")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Download queue").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 active, 2 paused").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_storageUsage_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Storage usage").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_storageUsage_shownWhenSet() {
        val state = DiagnosticsState(storageUsage = "1.2 GB / 32 GB")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Storage usage").assertIsDisplayed()
        composeTestRule.onNodeWithText("1.2 GB / 32 GB").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_lastStatus_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Last status").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_lastStatus_shownWhenSet() {
        val state = DiagnosticsState(lastStatusCategory = "success")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Last status").assertIsDisplayed()
        composeTestRule.onNodeWithText("success").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_lastSuccessfulRefresh_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Last successful refresh").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_lastSuccessfulRefresh_shownWhenSet() {
        val state = DiagnosticsState(lastSuccessfulRefreshAge = "5 minutes ago")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Last successful refresh").assertIsDisplayed()
        composeTestRule.onNodeWithText("5 minutes ago").assertIsDisplayed()
    }

    @Test
    fun diagnostics_conditionalField_deviceApiLevel_hiddenWhenNull() {
        composeTestRule.setContent {
            DiagnosticsContent(state = DiagnosticsState.REDACTED)
        }

        composeTestRule.onNodeWithText("Device / API level").assertDoesNotExist()
    }

    @Test
    fun diagnostics_conditionalField_deviceApiLevel_shownWhenSet() {
        val state = DiagnosticsState(deviceApiLevel = "34")

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("Device / API level").assertIsDisplayed()
        composeTestRule.onNodeWithText("34").assertIsDisplayed()
    }

    @Test
    fun diagnostics_customValues_displayCorrectly() {
        val state =
            DiagnosticsState(
                installedAppVersion = "2.1.0",
                buildType = "release",
                versionCode = "210",
                releaseChannel = "stable",
                apiBaseUrl = "redacted",
                authStateCategory = "authenticated",
                kodiMode = "Kodi-compatible request mode: enabled",
            )

        composeTestRule.setContent {
            DiagnosticsContent(state = state)
        }

        composeTestRule.onNodeWithText("2.1.0").assertIsDisplayed()
        composeTestRule.onNodeWithText("release").assertIsDisplayed()
        composeTestRule.onNodeWithText("210").assertIsDisplayed()
        composeTestRule.onNodeWithText("stable").assertIsDisplayed()
        composeTestRule.onNodeWithText("authenticated").assertIsDisplayed()
    }
}
