package net.subsloth.e2e

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.subsloth.BuildConfig
import net.subsloth.MainActivity
import net.subsloth.SubSlothApplication
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in live E2E test driving the real [MainActivity] against a real
 * backend.
 *
 * It signs in through the production login form using the base URL and
 * credentials passed as instrumentation args, fetches a show title straight
 * from the live API, then verifies the app's search finds that title in the
 * synced catalog.
 *
 * Skipped automatically when credentials are not passed, so the regular
 * instrumented CI run never touches a live backend and never stores secrets:
 *
 * ```
 * adb -e shell am instrument -w \
 *   -e class net.subsloth.e2e.LiveCatalogE2ETest \
 *   -e SUBSLOTH_LOGIN "$SUBSLOTH_LOGIN" \
 *   -e SUBSLOTH_PASSWORD "$SUBSLOTH_PASSWORD" \
 *   -e SUBSLOTH_API_BASE_URL "$SUBSLOTH_API_BASE_URL" \
 *   net.subsloth.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LiveCatalogE2ETest {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val arguments = InstrumentationRegistry.getArguments()
    private val login: String = arguments.getString(ARG_LOGIN).orEmpty()
    private val password: String = arguments.getString(ARG_PASSWORD).orEmpty()
    private val baseUrl: String =
        arguments.getString(ARG_BASE_URL)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.SUBSLOTH_API_BASE_URL

    @Test
    fun login_thenSearchFindsLiveTitle() {
        val liveTitle = firstLiveShowTitle()
        seedBaseUrlBeforeLaunch()
        ActivityScenario.launch(MainActivity::class.java)
        signIn()
        awaitCatalogSynced()

        composeTestRule.onNodeWithText(SEARCH_ICON).performClick()
        composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).performTextInput(liveTitle)

        composeTestRule.waitUntil(timeoutMillis = NETWORK_TIMEOUT_MS) {
            resultNodes(liveTitle).fetchSemanticsNodes().isNotEmpty()
        }
        resultNodes(liveTitle)[0].assertIsDisplayed()
    }

    /**
     * Search filters the locally synced catalog, so it must wait until the
     * home screen has rendered shows; searching earlier would legitimately
     * report "No results" for an empty database.
     */
    private fun awaitCatalogSynced() {
        composeTestRule.waitUntil(timeoutMillis = SYNC_TIMEOUT_MS) {
            hasAnyNodeWithText(SHOWS_ROW_LABEL)
        }
    }

    /**
     * The login form saves the API base URL asynchronously when Sign In is
     * tapped while validation reads the persisted value immediately, so the
     * test seeds the preference up front to stay deterministic. Production
     * users cannot hit this window because they type the URL long before
     * tapping Sign In.
     */
    private fun seedBaseUrlBeforeLaunch() {
        if (baseUrl.isBlank()) return
        val application = ApplicationProvider.getApplicationContext<SubSlothApplication>()
        runBlocking { application.container.userPreferences.setApiBaseUrl(baseUrl) }
    }

    private fun firstLiveShowTitle(): String {
        val client =
            ClientFactory.create(
                login = login,
                password = password,
                baseUrl = baseUrl,
                enableHttpLogging = false,
            )
        return try {
            val shows = runBlocking { Api(client).listShows(page = 1, perPage = 1) }.shows
            val title = shows.firstOrNull { !it.name.isNullOrBlank() }?.name
            assertTrue(
                "Live API returned no named shows — cannot verify catalog rendering",
                title != null,
            )
            title.orEmpty()
        } finally {
            client.close()
        }
    }

    private fun signIn() {
        composeTestRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MS) {
            hasAnyNodeWithText(SIGN_IN) || hasAnyNodeWithText(SEARCH_ICON)
        }
        if (!hasAnyNodeWithText(SIGN_IN)) return

        if (baseUrl.isNotBlank()) {
            composeTestRule.onNodeWithText(API_BASE_URL_LABEL).performTextClearance()
            composeTestRule.onNodeWithText(API_BASE_URL_LABEL).performTextInput(baseUrl)
        }
        composeTestRule.onNodeWithText(LOGIN_LABEL).performTextInput(login)
        composeTestRule.onNodeWithText(PASSWORD_LABEL).performTextInput(password)
        composeTestRule.onNodeWithText(SIGN_IN).performClick()

        composeTestRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MS) { hasAnyNodeWithText(SEARCH_ICON) }
    }

    private fun resultNodes(title: String) =
        composeTestRule.onAllNodes(
            hasText(title) and SemanticsMatcher.keyNotDefined(SemanticsActions.SetText),
        )

    private fun hasAnyNodeWithText(text: String): Boolean =
        composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    companion object {
        private const val ARG_LOGIN = "SUBSLOTH_LOGIN"
        private const val ARG_PASSWORD = "SUBSLOTH_PASSWORD"
        private const val ARG_BASE_URL = "SUBSLOTH_API_BASE_URL"
        private const val LOGIN_TIMEOUT_MS = 60_000L
        private const val NETWORK_TIMEOUT_MS = 120_000L
        private const val SYNC_TIMEOUT_MS = 180_000L
        private const val SHOWS_ROW_LABEL = "Shows"
        private const val LOGIN_LABEL = "Login"
        private const val PASSWORD_LABEL = "Password"
        private const val API_BASE_URL_LABEL = "API Base URL"
        private const val SIGN_IN = "Sign In"
        private const val SEARCH_ICON = "🔍"
        private const val SEARCH_PLACEHOLDER = "Search movies and shows"

        @JvmStatic
        @BeforeClass
        fun requireCredentials() {
            val args = InstrumentationRegistry.getArguments()
            assumeTrue(
                "Live E2E skipped: pass -e $ARG_LOGIN and -e $ARG_PASSWORD instrumentation args",
                !args.getString(ARG_LOGIN).isNullOrBlank() &&
                    !args.getString(ARG_PASSWORD).isNullOrBlank(),
            )
        }
    }
}
