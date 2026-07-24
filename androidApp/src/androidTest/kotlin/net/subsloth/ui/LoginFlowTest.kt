package net.subsloth.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.catalog.CatalogContent
import net.subsloth.catalog.HomeRow
import net.subsloth.catalog.HomeUiState
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.AuthError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.preferences.UserPreferences
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Login flow tests that exercise the full Login → Home navigation
 * via the real [LoginViewModel] and [InMemorySessionState].
 *
 * These are Playwright-style user-journey tests: type credentials,
 * click Sign In, and verify the catalog screen appears.
 */
@RunWith(AndroidJUnit4::class)
class LoginFlowTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private sealed interface LoginFlowScreen {
        data object Login : LoginFlowScreen

        data object Home : LoginFlowScreen
    }

    @Composable
    private fun LoginFlowShell(
        screen: LoginFlowScreen,
        onLoginSuccess: () -> Unit,
        sessionPort: SessionPort = remember { InMemorySessionState() },
    ) {
        when (screen) {
            LoginFlowScreen.Login -> {
                LoginScreen(
                    viewModel =
                        LoginViewModel(
                            sessionPort = sessionPort,
                            readApiBaseUrl = { flowOf(UserPreferences.DEFAULT_API_BASE_URL) },
                            saveApiBaseUrl = {},
                        ),
                    onNavigateToCatalog = onLoginSuccess,
                )
            }

            LoginFlowScreen.Home -> {
                CatalogContent(state = homeContentState)
            }
        }
    }

    private val homeContentState =
        HomeUiState.Content(
            rows =
                persistentListOf(
                    HomeRow.Movies(
                        items =
                            persistentListOf(
                                MovieSummary(
                                    id = Media.MediaId.Movie(MovieId(1)),
                                    title = "Login Flow Movie",
                                    plot = "Reached after successful login",
                                    availability = Availability.Available,
                                    rating = 8.0,
                                    year = 2025,
                                    genres = persistentListOf("Action"),
                                    durationMinutes = 100,
                                    slug = "login-flow-movie",
                                    imdbId = ExternalId("tt1111111", ExternalIdSource.IMDb),
                                    backdropUrl = null,
                                    posterUrl = null,
                                ),
                            ),
                        label = "Movies",
                    ),
                ),
            selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
        )

    @Test
    fun loginSucceeds_navigatesToCatalog() {
        var screen by mutableStateOf<LoginFlowScreen>(LoginFlowScreen.Login)

        composeTestRule.setContent {
            LoginFlowShell(
                screen = screen,
                onLoginSuccess = { screen = LoginFlowScreen.Home },
            )
        }

        // On the login screen
        composeTestRule.onNodeWithText("SubSloth").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()

        // Fill in valid credentials
        composeTestRule.onNodeWithText("Login").performTextInput("alice@subsloth.app")
        composeTestRule.onNodeWithText("Password").performTextInput("secret")

        // Sign in triggers sessionPort.open → Outcome.Success → LoggedIn → navigation
        composeTestRule.onNodeWithText("Sign In").performClick()

        // Verify the catalog screen rendered
        composeTestRule.onNodeWithText("Login Flow Movie").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movies").assertIsDisplayed()
        composeTestRule.onNodeWithText("★ 8.0").assertIsDisplayed()
    }

    @Test
    fun loginFails_showsErrorMessage() {
        val failingPort = FailingSessionPort()

        var screen by mutableStateOf<LoginFlowScreen>(LoginFlowScreen.Login)

        composeTestRule.setContent {
            LoginFlowShell(
                screen = screen,
                onLoginSuccess = { screen = LoginFlowScreen.Home },
                sessionPort = failingPort,
            )
        }

        // Fill in credentials (non-blank so Sign In is enabled)
        composeTestRule.onNodeWithText("Login").performTextInput("alice")
        composeTestRule.onNodeWithText("Password").performTextInput("wrong")

        composeTestRule.onNodeWithText("Sign In").performClick()

        // Error message appears; we stay on the login screen
        composeTestRule.onNodeWithText("Authentication required").assertIsDisplayed()

        // The form is still visible (we didn't navigate)
        composeTestRule.onNodeWithText("SubSloth").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sign In").assertIsDisplayed()
    }
}

private class FailingSessionPort : SessionPort {
    override val state = MutableStateFlow<Session>(Session.Anonymous).asStateFlow()

    override fun current(): Session = Session.Anonymous

    override suspend fun open(credentials: Credentials): Outcome<Unit> = Outcome.Failure(AuthError.InvalidCredentials)

    override suspend fun close(): Outcome<Unit> = Outcome.Success(Unit)

    override suspend fun invalidate(): Outcome<Unit> = Outcome.Success(Unit)
}
