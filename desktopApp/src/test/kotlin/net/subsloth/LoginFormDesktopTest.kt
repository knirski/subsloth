package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.subsloth.auth.LoginFormContent
import net.subsloth.core.model.error.UiError
import org.junit.Rule
import org.junit.Test

class LoginFormDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun loginForm_displaysTitleAndFields() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "",
                    password = "",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = false,
                )
            }
        }

        composeRule.onNodeWithText("SubSloth").assertIsDisplayed()
        composeRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun loginForm_signInButton_disabledWhenFieldsEmpty() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "",
                    password = "",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = false,
                )
            }
        }

        composeRule.onNodeWithText("Sign In").assertIsNotEnabled()
    }

    @Test
    fun loginForm_showsAuthErrorMessage() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "user",
                    password = "pass",
                    isLoading = false,
                    error = UiError.AuthRequired(),
                    hasOfflineLibrary = false,
                )
            }
        }

        composeRule.onNodeWithText("Authentication required").assertIsDisplayed()
    }

    @Test
    fun loginForm_showsOfflineLibraryButton() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "",
                    password = "",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = true,
                )
            }
        }

        composeRule.onNodeWithText("Offline Library").assertIsDisplayed()
    }

    @Test
    fun loginForm_signInButton_hasClickAction() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "user",
                    password = "pass",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = false,
                )
            }
        }

        composeRule.onNodeWithText("Sign In").assertHasClickAction()
    }

    @Test
    fun loginForm_callsOnSignIn_whenClicked() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "user",
                    password = "pass",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = false,
                    onSignIn = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Sign In").performClick()
        kotlin.test.assertTrue(clicked, "onSignIn should have been called")
    }

    @Test
    fun loginForm_hidesOfflineButton_whenNotAvailable() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "",
                    password = "",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = false,
                )
            }
        }

        composeRule.onNodeWithText("Offline Library").assertDoesNotExist()
    }
}
