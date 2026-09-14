package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
import net.subsloth.library.LibraryScreen
import net.subsloth.library.LibraryViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NavigationEntryPointsDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeScreen_entryPointButtons_invokeCallbacks() {
        var downloadsClicks = 0
        var settingsClicks = 0

        composeRule.setContent {
            MaterialTheme {
                HomeScreen(
                    viewModel = HomeViewModel(),
                    onDownloadsClick = { downloadsClicks++ },
                    onSettingsClick = { settingsClicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Downloads").assertHasClickAction().performClick()
        composeRule.onNodeWithContentDescription("Settings").assertHasClickAction().performClick()

        assertEquals(1, downloadsClicks)
        assertEquals(1, settingsClicks)
    }

    @Test
    fun screenNavigateBackButton_invokesCallback() {
        var backClicks = 0

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    viewModel = LibraryViewModel(),
                    onNavigateBack = { backClicks++ },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Back").assertHasClickAction().performClick()

        assertEquals(1, backClicks)
    }
}
