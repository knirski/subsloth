package net.subsloth.e2e

import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import kotlinx.coroutines.runBlocking
import net.subsloth.BuildConfig
import net.subsloth.MainActivity
import net.subsloth.SubSlothApplication
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Opt-in live E2E tests driving the real [MainActivity] against a real
 * backend.
 *
 * The suite signs in through the production login form using the base URL
 * and credentials passed as instrumentation args, then exercises the real
 * catalog and player:
 *
 * - search finds a live title in the synced catalog;
 * - selecting an episode and pressing Play starts playback and the position
 *   advances;
 * - selecting a movie does the same (skipped when the account's movie list
 *   is empty).
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
        launchSignedIn()

        composeTestRule.onNodeWithText(SEARCH_ICON).performClick()
        composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).performTextInput(liveTitle)
        awaitSearchResult(liveTitle)

        resultNodes(liveTitle)[0].assertIsDisplayed()
    }

    @Test
    fun episode_playbackStartsAndProgresses() {
        val episode = firstPlayableEpisode()
        launchSignedIn()
        openFromSearch(episode.showTitle)

        composeTestRule.waitUntil(timeoutMillis = DETAIL_TIMEOUT_MS) {
            hasAnyNodeWithText(episode.rowText)
        }
        composeTestRule.onNodeWithText(episode.rowText).performScrollTo().performClick()
        composeTestRule.waitUntil(timeoutMillis = DETAIL_TIMEOUT_MS) {
            hasAnyNodeWithText(episode.episodeLabel)
        }
        startPlayback()
    }

    @Test
    fun movie_playbackStartsAndProgresses() {
        val movieTitle = firstLiveMovieTitle()
        assumeTrue("No movies on this account — movie playback skipped", movieTitle != null)
        launchSignedIn()
        openFromSearch(movieTitle.orEmpty())

        startPlayback()
    }

    private fun launchSignedIn() {
        seedBaseUrlBeforeLaunch()
        ActivityScenario.launch(MainActivity::class.java)
        signIn()
        awaitCatalogSynced()
    }

    private fun openFromSearch(title: String) {
        composeTestRule.onNodeWithText(SEARCH_ICON).performClick()
        composeTestRule.onNodeWithText(SEARCH_PLACEHOLDER).performTextInput(title)
        awaitSearchResult(title)
        resultNodes(title)[0].performClick()
    }

    private fun awaitSearchResult(title: String) {
        composeTestRule.waitUntil(timeoutMillis = NETWORK_TIMEOUT_MS) {
            resultNodes(title).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tapWhenVisible(
        label: String,
        timeoutMillis: Long,
    ) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) { hasAnyNodeWithText(label) }
        composeTestRule.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick) { it() }
        composeTestRule.waitForIdle()
    }

    /**
     * Clears logcat, taps Play, then waits for the app's player to resolve a
     * stream and start playback. Video frame rendering is CDN and codec
     * dependent on emulators, so the deterministic signal is the player's own
     * "Starting playback" log rather than pixel output.
     */
    private fun startPlayback() {
        shell("logcat -c")
        tapWhenVisible(PLAY, DETAIL_TIMEOUT_MS)
        val deadline = System.currentTimeMillis() + PLAYBACK_START_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (playerLogs().contains(STARTING_PLAYBACK)) return
            Thread.sleep(PROGRESS_SAMPLE_MS)
        }
        throw AssertionError("Player did not start playback. Player logs: ${playerLogs().take(500)}")
    }

    private fun playerLogs(): String = shell("logcat -d -s PlayerViewModel:D")

    /**
     * Runs the suite in landscape: the player forces sensor-landscape, and a
     * mid-test orientation change recreates the activity, which races the
     * navigation save and drops the player from the back stack on this
     * emulator. Starting landscape keeps the player's orientation request a
     * no-op.
     */
    @Before
    fun configureDeviceForPlayback() {
        shell("settings put secure immersive_mode_confirmations confirmed")
        shell("settings put system accelerometer_rotation 0")
        shell("settings put system user_rotation 1")
    }

    private fun shell(command: String): String {
        val descriptor =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
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
        val client = createLiveClient()
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

    private fun firstLiveMovieTitle(): String? {
        val client = createLiveClient()
        return try {
            runBlocking { Api(client).listMovies(page = 1, perPage = 1) }
                .movies
                .firstOrNull { !it.name.isNullOrBlank() }
                ?.name
        } finally {
            client.close()
        }
    }

    /**
     * Finds a playable episode using the same mapping the app applies:
     * `episodeNumber = episode ?: number`, `title = title ?: name`, and the
     * lowest season is selected by default. The row text mirrors
     * `EpisodeRow`'s `"<number>. <title>"` rendering.
     */
    private fun firstPlayableEpisode(): LiveEpisode {
        val client = createLiveClient()
        return try {
            val api = Api(client)
            val shows = runBlocking { api.listShows(page = 1, perPage = SHOW_CANDIDATE_LIMIT) }.shows
            for (show in shows) {
                val title = show.title ?: show.name ?: continue
                val detail = runBlocking { api.getShow(show.id) }
                val episode =
                    detail.episodes
                        .orEmpty()
                        .filter { it.available != false }
                        .minWithOrNull(compareBy({ it.season ?: 0 }, { it.episode ?: it.number ?: 0 }))
                        ?: continue
                val number = episode.episode ?: episode.number ?: 0
                val episodeTitle = episode.title ?: episode.name ?: "Episode $number"
                return LiveEpisode(
                    showTitle = title,
                    rowText = "$number. $episodeTitle",
                    episodeLabel = "S${episode.season ?: 0} · E$number",
                )
            }
            error("No playable episode found in the first $SHOW_CANDIDATE_LIMIT live shows")
        } finally {
            client.close()
        }
    }

    private fun createLiveClient(): HttpClient =
        ClientFactory.create(
            login = login,
            password = password,
            baseUrl = baseUrl,
            enableHttpLogging = false,
        )

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

    private data class LiveEpisode(
        val showTitle: String,
        val rowText: String,
        val episodeLabel: String,
    )

    companion object {
        private const val ARG_LOGIN = "SUBSLOTH_LOGIN"
        private const val ARG_PASSWORD = "SUBSLOTH_PASSWORD"
        private const val ARG_BASE_URL = "SUBSLOTH_API_BASE_URL"
        private const val LOGIN_TIMEOUT_MS = 60_000L
        private const val NETWORK_TIMEOUT_MS = 120_000L
        private const val SYNC_TIMEOUT_MS = 180_000L
        private const val DETAIL_TIMEOUT_MS = 120_000L
        private const val PLAYBACK_START_TIMEOUT_MS = 120_000L
        private const val PROGRESS_SAMPLE_MS = 2_000L
        private const val SHOW_CANDIDATE_LIMIT = 5
        private const val SHOWS_ROW_LABEL = "Shows"
        private const val LOGIN_LABEL = "Login"
        private const val PASSWORD_LABEL = "Password"
        private const val API_BASE_URL_LABEL = "API Base URL"
        private const val SIGN_IN = "Sign In"
        private const val SEARCH_ICON = "🔍"
        private const val SEARCH_PLACEHOLDER = "Search movies and shows"
        private const val PLAY = "Play"
        private const val STARTING_PLAYBACK = "Starting playback: mode=ONLINE"

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
