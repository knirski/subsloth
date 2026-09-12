package net.subsloth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import kotlinx.coroutines.runBlocking
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.desktop.DesktopContainer
import net.subsloth.desktop.DesktopRoot
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import kotlin.test.assertTrue

/**
 * Opt-in live E2E test that drives the real desktop composition root
 * ([DesktopContainer]) and nav host against a live backend.
 *
 * It signs in through the production login form, waits for the catalog sync,
 * searches for a show fetched straight from the live API, opens its detail
 * (asserting the episode list renders), and then resolves the first
 * playable episode's stream through the desktop playback port.
 *
 * Skipped automatically unless credentials are provided through the
 * environment, so CI never touches a live backend and never stores secrets.
 * Run it with the login, password, and API base URL environment variables
 * set, as documented in `docs/testing/desktop-tests.md`.
 */
class LiveDesktopE2ETest {
    @get:Rule
    val composeRule = createComposeRule()

    private val dataDir = Files.createTempDirectory("subsloth-live-desktop").toFile()
    private val container = DesktopContainer(dataDir)
    private val viewModelOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore = ViewModelStore()
        }

    @Before
    fun seedBaseUrl() {
        if (baseUrl.isBlank()) return
        runBlocking { container.userPreferences.setApiBaseUrl(baseUrl) }
    }

    @After
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    @Test
    fun login_browseCatalog_andResolveEpisodeSource() {
        composeRule.setContent {
            SubSlothTheme {
                CompositionLocalProvider(LocalViewModelStoreOwner provides viewModelOwner) {
                    DesktopRoot(container)
                }
            }
        }

        signIn()
        awaitText(SHOWS_ROW_LABEL, SYNC_TIMEOUT_MS)

        val episode = firstPlayableEpisode()
        openShowFromSearch(episode.showTitle)
        awaitText(episode.rowText, DETAIL_TIMEOUT_MS)
        composeRule.onNodeWithText(episode.rowText).assertIsDisplayed()

        val source = resolveEpisodeSource(episode.episodeId)
        assertTrue(
            source.playbackMode == PlaybackMode.ONLINE,
            "Expected an online source, got ${source.playbackMode}",
        )
        assertTrue(source.streamUrl.isNotBlank(), "Resolved stream URL is blank")
    }

    /**
     * Resolves the episode stream, retrying briefly: the upstream CDN
     * occasionally answers an otherwise valid API request with a bot
     * challenge page right after the catalog sync burst, and a retry
     * succeeds.
     */
    private fun resolveEpisodeSource(episodeId: Int): VideoSource {
        val deadline = System.currentTimeMillis() + SOURCE_TIMEOUT_MS
        var lastOutcome: Outcome<VideoSource>? = null
        while (System.currentTimeMillis() < deadline) {
            val outcome =
                runBlocking {
                    container.playbackPort.prepareSource(Media.MediaId.Episode(EpisodeId(episodeId)))
                }
            if (outcome is Outcome.Success) return outcome.value
            lastOutcome = outcome
            Thread.sleep(SOURCE_RETRY_MS)
        }
        throw AssertionError("prepareSource failed after ${SOURCE_TIMEOUT_MS}ms: $lastOutcome")
    }

    private fun signIn() {
        composeRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MS) {
            hasAnyNodeWithText(SIGN_IN) || hasAnyNodeWithText(SHOWS_ROW_LABEL)
        }
        if (!hasAnyNodeWithText(SIGN_IN)) return

        if (baseUrl.isNotBlank()) {
            // The login form seeds its field from the persisted preference
            // asynchronously; wait for that seed before typing so the
            // recomposition cannot replace the fields mid-input.
            composeRule.waitUntil(timeoutMillis = LOGIN_TIMEOUT_MS) { hasAnyNodeWithText(baseUrl) }
        }
        composeRule.onNodeWithText(LOGIN_LABEL).performTextInput(login)
        composeRule.onNodeWithText(PASSWORD_LABEL).performTextInput(password)
        composeRule.onNodeWithText(SIGN_IN).performClick()

        awaitText(SHOWS_ROW_LABEL, LOGIN_TIMEOUT_MS)
    }

    private fun openShowFromSearch(title: String) {
        composeRule.onNodeWithText(SEARCH_ICON).performClick()
        composeRule.onNodeWithText(SEARCH_PLACEHOLDER).performTextInput(title)
        composeRule.waitUntil(timeoutMillis = NETWORK_TIMEOUT_MS) {
            resultNodes(title).fetchSemanticsNodes().isNotEmpty()
        }
        resultNodes(title)[0].performClick()
    }

    private fun awaitText(text: String, timeoutMillis: Long) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) { hasAnyNodeWithText(text) }
    }

    private fun resultNodes(title: String) = composeRule.onAllNodes(
        hasText(title) and SemanticsMatcher.keyNotDefined(SemanticsActions.SetText),
    )

    private fun hasAnyNodeWithText(text: String): Boolean =
        composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    /**
     * Finds a playable episode using the same mapping the app applies:
     * `episodeNumber = episode ?: number`, `title = title ?: name`, and the
     * lowest season is selected by default. The row text mirrors
     * `EpisodeRow`'s `"<number>. <title>"` rendering.
     */
    private fun firstPlayableEpisode(): LiveEpisode {
        val client =
            ClientFactory.create(
                login = login,
                password = password,
                baseUrl = baseUrl,
                enableHttpLogging = false,
            )
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
                    episodeId = episode.id,
                    showTitle = title,
                    rowText = "$number. $episodeTitle",
                )
            }
            error("No playable episode found in the first $SHOW_CANDIDATE_LIMIT live shows")
        } finally {
            client.close()
        }
    }

    private data class LiveEpisode(val episodeId: Int, val showTitle: String, val rowText: String)

    companion object {
        private const val ARG_LOGIN = "SUBSLOTH_LOGIN"
        private const val ARG_PASSWORD = "SUBSLOTH_PASSWORD"
        private const val ARG_BASE_URL = "SUBSLOTH_API_BASE_URL"
        private const val LOGIN_TIMEOUT_MS = 60_000L
        private const val NETWORK_TIMEOUT_MS = 120_000L
        private const val SYNC_TIMEOUT_MS = 180_000L
        private const val DETAIL_TIMEOUT_MS = 120_000L
        private const val SHOW_CANDIDATE_LIMIT = 5
        private const val SOURCE_TIMEOUT_MS = 60_000L
        private const val SOURCE_RETRY_MS = 2_000L
        private const val SHOWS_ROW_LABEL = "Shows"
        private const val LOGIN_LABEL = "Login"
        private const val PASSWORD_LABEL = "Password"
        private const val SIGN_IN = "Sign In"
        private const val SEARCH_ICON = "🔍"
        private const val SEARCH_PLACEHOLDER = "Search movies and shows"

        private val login: String = System.getenv(ARG_LOGIN).orEmpty()
        private val password: String = System.getenv(ARG_PASSWORD).orEmpty()
        private val baseUrl: String = System.getenv(ARG_BASE_URL).orEmpty()

        @JvmStatic
        @BeforeClass
        fun requireCredentials() {
            assumeTrue(
                "Live desktop E2E skipped: set $ARG_LOGIN and $ARG_PASSWORD",
                login.isNotBlank() && password.isNotBlank(),
            )
        }
    }
}
