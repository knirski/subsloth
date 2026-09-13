package net.subsloth.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.CatalogContent
import net.subsloth.catalog.HomeRow
import net.subsloth.catalog.HomeTab
import net.subsloth.catalog.HomeUiState
import net.subsloth.catalog.MEDIA_CARD_TEST_TAG
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.ui.LocalIsTelevision
import net.subsloth.core.ui.SubSlothBackButton
import net.subsloth.testing.tvfocus.TvFocusTestRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TV D-pad focus traversal, consuming [TvFocusTestRule].
 *
 * Runs on the regular instrumented emulator; `LocalIsTelevision provides true`
 * enables the TV-only focus behaviour so the same assertions hold on a
 * leanback device.
 */
@RunWith(AndroidJUnit4::class)
class TvFocusTraversalTest {

    @get:Rule
    val tvFocusRule = TvFocusTestRule()

    private val sampleMovie = MovieSummary(
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

        tvFocusRule.assertFocusedContentDescription("Back")
    }

    @Test
    fun homeSearch_takesInitialFocusAndDpadReachesMediaCards() {
        tvFocusRule.setContent {
            CompositionLocalProvider(LocalIsTelevision provides true) {
                MaterialTheme {
                    CatalogContent(
                        state = HomeUiState.Content(
                            rows = persistentListOf(
                                HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                            ),
                            selectedTab = HomeTab.MOVIES,
                        ),
                    )
                }
            }
        }

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
        const val MAX_DPAD_PRESSES = 6
    }
}
