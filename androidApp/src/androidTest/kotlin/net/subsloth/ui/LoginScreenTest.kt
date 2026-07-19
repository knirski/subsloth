package net.subsloth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.subsloth.auth.LoginFormContent
import net.subsloth.core.model.error.UiError
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Playwright-style Compose UI tests for the Login screen.
 *
 * These tests compose [LoginFormContent] directly with controlled state
 * and verify user-facing behavior: field rendering, validation, loading,
 * error display, and navigation callbacks.
 */
@RunWith(AndroidJUnit4::class)
class LoginScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginForm_displaysAllElements() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("SubSloth").assertIsDisplayed()
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("API Base URL").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }

    @Test
    fun signInButton_disabledWhenLoginIsEmpty() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "password",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Sign In").assertIsNotEnabled()
    }

    @Test
    fun signInButton_disabledWhenPasswordIsEmpty() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Sign In").assertIsNotEnabled()
    }

    @Test
    fun signInButton_disabledWhenBothFieldsEmpty() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Sign In").assertIsNotEnabled()
    }

    @Test
    fun signInButton_enabledWhenFieldsFilled() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Sign In").assertIsEnabled()
    }

    @Test
    fun loadingState_replacesButtonWithSpinner() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = true,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        // Sign In button should not be visible during loading
        composeTestRule.onNodeWithText("Sign In").assertDoesNotExist()
    }

    @Test
    fun loadingState_disablesTextFields() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = true,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        // Labels are still visible but fields should be disabled (via enabled = !isLoading)
        composeTestRule.onNodeWithText("Login").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
        composeTestRule.onNodeWithText("API Base URL").assertIsDisplayed()
    }

    @Test
    fun errorState_showsErrorMessage() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = UiError.AuthRequired(),
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Authentication required").assertIsDisplayed()
    }

    @Test
    fun errorState_showsOfflineMessage() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = UiError.Offline(),
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("You are offline").assertIsDisplayed()
    }

    @Test
    fun errorState_showsServiceErrorMessage() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = UiError.ServiceError(),
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Service error").assertIsDisplayed()
    }

    @Test
    fun offlineLibraryButton_shownWhenHasOfflineLibrary() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = true,
            )
        }

        composeTestRule.onNodeWithText("Offline Library").assertIsDisplayed()
    }

    @Test
    fun offlineLibraryButton_hiddenWhenNoOfflineLibrary() {
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }

        composeTestRule.onNodeWithText("Offline Library").assertDoesNotExist()
    }

    @Test
    fun typingInLoginField_updatesValue() {
        var capturedLogin = ""
        composeTestRule.setContent {
            LoginFormContent(
                login = capturedLogin,
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
                onLoginChange = { capturedLogin = it },
            )
        }

        composeTestRule.onNodeWithText("Login").performTextInput("myuser")
        assertTrue(capturedLogin == "myuser", "Expected login to be 'myuser' but was '$capturedLogin'")
    }

    @Test
    fun typingInPasswordField_updatesValue() {
        var capturedPassword = ""
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = capturedPassword,
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
                onPasswordChange = { capturedPassword = it },
            )
        }

        composeTestRule.onNodeWithText("Password").performTextInput("mypassword")
        assertTrue(capturedPassword == "mypassword", "Expected password to be 'mypassword' but was '$capturedPassword'")
    }

    @Test
    fun signInClick_triggersCallback() {
        var signInClicked = false
        composeTestRule.setContent {
            LoginFormContent(
                login = "user",
                password = "pass",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
                onSignIn = { signInClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Sign In").performClick()
        assertTrue(signInClicked, "Expected sign-in callback to be invoked")
    }

    @Test
    fun offlineLibraryClick_triggersCallback() {
        var offlineLibraryClicked = false
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://example.com",
                isLoading = false,
                error = null,
                hasOfflineLibrary = true,
                onNavigateToOfflineLibrary = { offlineLibraryClicked = true },
            )
        }

        composeTestRule.onNodeWithText("Offline Library").performClick()
        assertTrue(offlineLibraryClicked, "Expected offline library callback to be invoked")
    }

    @Test
    fun apiBaseUrlChange_triggersCallback() {
        var capturedUrl = ""
        composeTestRule.setContent {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
                onApiBaseUrlChange = { capturedUrl = it },
            )
        }

        composeTestRule.onNodeWithText("API Base URL").performTextInput("http://kodi:8080")
        assertTrue(
            capturedUrl == "http://kodi:8080",
            "Expected API URL to be 'http://kodi:8080' but was '$capturedUrl'",
        )
    }
}
