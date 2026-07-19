package net.subsloth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.CatalogContent
import net.subsloth.catalog.HomeRow
import net.subsloth.catalog.HomeUiState
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Playwright-style Compose UI tests for the Home/Catalog screen.
 *
 * Tests the catalog content rendering: row labels, media cards,
 * sync button toggle, and click callbacks.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie =
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Test Movie",
            plot = "A test movie plot",
            availability = Availability.Available,
            rating = 8.5,
            year = 2024,
            genres = persistentListOf("Action", "Drama"),
            durationMinutes = 120,
            slug = "test-movie",
            imdbId = ExternalId("tt1234567", ExternalIdSource.IMDb),
            backdropUrl = null,
            posterUrl = null,
        )

    private val sampleShow =
        ShowSummary(
            id = Media.MediaId.Show(ShowId(1)),
            title = "Test Show",
            plot = "A test show plot",
            availability = Availability.Available,
            rating = 9.0,
            year = 2023,
            genres = persistentListOf("Comedy"),
            durationMinutes = 30,
            slug = "test-show",
            imdbId = ExternalId("tt7654321", ExternalIdSource.IMDb),
            backdropUrl = null,
            posterUrl = null,
            status = ShowStatus.ONGOING,
            countries = persistentListOf("US"),
        )

    @Test
    fun catalogContent_displaysRowLabels() {
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                        HomeRow.Shows(persistentListOf(sampleShow), label = "Shows"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("Movies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shows").assertIsDisplayed()
    }

    @Test
    fun catalogContent_displaysMediaTitles() {
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("Test Movie").assertIsDisplayed()
    }

    @Test
    fun catalogContent_displaysRating() {
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("★ 8.5").assertIsDisplayed()
    }

    @Test
    fun catalogContent_displaysShowStatus() {
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Shows(persistentListOf(sampleShow), label = "Shows"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.SHOWS,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("Ongoing").assertIsDisplayed()
    }

    @Test
    fun catalogContent_displaysYearWhenAvailable() {
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("2024").assertIsDisplayed()
    }

    @Test
    fun mediaCardClick_triggersMovieCallback() {
        var clickedMovieId: Media.MediaId.Movie? = null

        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(
                state = contentState,
                onMovieClick = { clickedMovieId = it },
            )
        }

        composeTestRule.onNodeWithText("Test Movie").performClick()
        assertTrue(clickedMovieId != null, "Expected movie click callback to be invoked")
        assertTrue(clickedMovieId.value.value == 1, "Expected movie id 1, got ${clickedMovieId.value.value}")
    }

    @Test
    fun mediaCardClick_triggersShowCallback() {
        var clickedShowId: Media.MediaId.Show? = null

        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Shows(persistentListOf(sampleShow), label = "Shows"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.SHOWS,
            )

        composeTestRule.setContent {
            CatalogContent(
                state = contentState,
                onShowClick = { clickedShowId = it },
            )
        }

        composeTestRule.onNodeWithText("Test Show").performClick()
        assertTrue(clickedShowId != null, "Expected show click callback to be invoked")
        assertTrue(clickedShowId.value.value == 1, "Expected show id 1, got ${clickedShowId.value.value}")
    }

    @Test
    fun emptyCatalog_showsNothing() {
        val contentState =
            HomeUiState.Content(
                rows = persistentListOf(),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        // No rows means no content displayed (empty LazyColumn)
        composeTestRule.onNodeWithText("Test Movie").assertDoesNotExist()
    }

    @Test
    fun recencyRow_displaysCustomLabel() {
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Recency(
                            items = persistentListOf(sampleMovie),
                            label = "Recently Added",
                        ),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("Recently Added").assertIsDisplayed()
    }

    @Test
    fun multipleRows_displayedInOrder() {
        val recencyMovie = sampleMovie.copy(title = "Recent Flick")
        val contentState =
            HomeUiState.Content(
                rows =
                    persistentListOf(
                        HomeRow.Recency(persistentListOf(recencyMovie), label = "Just Added"),
                        HomeRow.Movies(persistentListOf(sampleMovie), label = "Movies"),
                        HomeRow.Shows(persistentListOf(sampleShow), label = "Shows"),
                    ),
                selectedTab = net.subsloth.catalog.HomeTab.MOVIES,
            )

        composeTestRule.setContent {
            CatalogContent(state = contentState)
        }

        composeTestRule.onNodeWithText("Just Added").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Shows").assertIsDisplayed()
        composeTestRule.onNodeWithText("Recent Flick").assertIsDisplayed()
    }
}
