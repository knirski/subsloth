package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.CatalogContent
import net.subsloth.catalog.HomeTab
import net.subsloth.catalog.HomeUiState
import org.junit.Rule
import org.junit.Test

class CatalogDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun catalogContent_showsMoviesUnavailableNotice() {
        composeRule.setContent {
            MaterialTheme {
                CatalogContent(
                    state = HomeUiState.Content(
                        rows = persistentListOf(),
                        selectedTab = HomeTab.MOVIES,
                        moviesUnavailable = true,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Movies aren't available on this account.").assertIsDisplayed()
    }

    @Test
    fun catalogContent_hidesMoviesUnavailableNotice_whenMoviesAvailable() {
        composeRule.setContent {
            MaterialTheme {
                CatalogContent(
                    state = HomeUiState.Content(
                        rows = persistentListOf(),
                        selectedTab = HomeTab.MOVIES,
                        moviesUnavailable = false,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Movies aren't available on this account.").assertDoesNotExist()
    }
}
