package net.subsloth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.flowOf
import net.subsloth.catalog.HomeScreen
import net.subsloth.catalog.HomeViewModel
import net.subsloth.catalog.MEDIA_CARD_TEST_TAG
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.ui.LocalIsTelevision
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.testing.tvfocus.TvFocusTestRule
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TV D-pad focus traversal, consuming [TvFocusTestRule].
 *
 * Ignored in CI: the headless emulator never grants the test window focus, so
 * every focus request is dropped (verified in CI logs: `Focused = false`).
 * Run on a TV device/emulator (see `docs/testing/device-acceptance.md`) where
 * focus is observable. Initial focus is covered by `TvFocusDesktopTest`.
 */
@Ignore("Headless CI emulator never grants window focus; run on a TV device/emulator")
@RunWith(AndroidJUnit4::class)
class TvFocusTraversalTest {
    @get:Rule
    val tvFocusRule = TvFocusTestRule()

    private val sampleMovie =
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Focus Movie",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
        )

    @Test
    fun backButton_receivesInitialFocusOnTv() {
        tvFocusRule.setContent {
            CompositionLocalProvider(LocalIsTelevision provides true) {
                MaterialTheme {
                    SubSlothBackButton(onClick = {})
                }
            }
        }

        tvFocusRule.waitUntilFocused("Back")
        tvFocusRule.assertFocusedContentDescription("Back")
    }

    @Test
    fun homeSearch_takesInitialFocusAndDpadReachesMediaCards() {
        val viewModel =
            HomeViewModel(
                catalogItems = { contentType ->
                    flowOf(if (contentType == "movie") listOf(sampleMovie) else emptyList())
                },
                savedState = mapOf("selectedTab" to "MOVIES"),
            )

        tvFocusRule.setContent {
            CompositionLocalProvider(LocalIsTelevision provides true) {
                MaterialTheme {
                    HomeScreen(viewModel = viewModel)
                }
            }
        }

        tvFocusRule.waitUntilFocused("Search")
        tvFocusRule.assertFocusedContentDescription("Search")

        var reachedCard = false
        repeat(MAX_DPAD_PRESSES) {
            if (!reachedCard) {
                tvFocusRule.pressDpadDown()
                reachedCard = tvFocusRule.hasAnyFocused(MEDIA_CARD_TEST_TAG)
            }
        }
        assertTrue("D-pad did not reach a media card", reachedCard)
    }

    private companion object {
        const val MAX_DPAD_PRESSES = 10
    }
}
