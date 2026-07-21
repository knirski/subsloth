package net.subsloth.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.SearchContent
import net.subsloth.catalog.SearchUiState
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertTrue

/**
 * Playwright-style Compose UI tests for the Search screen.
 *
 * Tests all search UI states: idle, loading, results, and no-results.
 * Also verifies search field interaction and result item clicks.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleMovie =
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(42)),
            title = "Matrix Reloaded",
            plot = "Neo fights more agents",
            availability = Availability.Available,
            rating = 7.2,
            year = 2003,
            genres = persistentListOf("Sci-Fi", "Action"),
            durationMinutes = 138,
            slug = "matrix-reloaded",
            imdbId = ExternalId("tt0234215", ExternalIdSource.IMDb),
            backdropUrl = null,
            posterUrl = null,
        )

    @Test
    fun idleState_showsPromptMessage() {
        composeTestRule.setContent {
            SearchContent(
                state = SearchUiState.Idle,
                query = "",
            )
        }

        composeTestRule.onNodeWithText("Search for movies and shows").assertIsDisplayed()
    }

    @Test
    fun idleState_showsSearchField() {
        composeTestRule.setContent {
            SearchContent(
                state = SearchUiState.Idle,
                query = "",
            )
        }

        composeTestRule.onNodeWithText("Search movies and shows").assertIsDisplayed()
    }

    @Test
    fun loadingState_hidesPromptMessage() {
        composeTestRule.setContent {
            SearchContent(
                state = SearchUiState.Loading(query = "matrix"),
                query = "matrix",
            )
        }

        composeTestRule.onNodeWithText("Search for movies and shows").assertDoesNotExist()
    }

    @Test
    fun resultsState_displaysMovieTitles() {
        val resultsState =
            SearchUiState.Results(
                query = "matrix",
                items = persistentListOf(sampleMovie),
            )

        composeTestRule.setContent {
            SearchContent(state = resultsState, query = "matrix")
        }

        composeTestRule.onNodeWithText("Matrix Reloaded").assertIsDisplayed()
    }

    @Test
    fun resultsState_displaysMoviePlot() {
        val resultsState =
            SearchUiState.Results(
                query = "matrix",
                items = persistentListOf(sampleMovie),
            )

        composeTestRule.setContent {
            SearchContent(state = resultsState, query = "matrix")
        }

        composeTestRule.onNodeWithText("Neo fights more agents").assertIsDisplayed()
    }

    @Test
    fun resultsState_displaysYearAndGenres() {
        val resultsState =
            SearchUiState.Results(
                query = "matrix",
                items = persistentListOf(sampleMovie),
            )

        composeTestRule.setContent {
            SearchContent(state = resultsState, query = "matrix")
        }

        composeTestRule.onNodeWithText("2003 · Sci-Fi, Action").assertIsDisplayed()
    }

    @Test
    fun noResultsState_showsNoResultsMessage() {
        val resultsState =
            SearchUiState.Results(
                query = "zzzunknown",
                items = persistentListOf(),
            )

        composeTestRule.setContent {
            SearchContent(state = resultsState, query = "zzzunknown")
        }

        composeTestRule.onNodeWithText("No results for \"zzzunknown\"").assertIsDisplayed()
    }

    @Test
    fun resultItemClick_triggersMovieCallback() {
        var clickedId: Media.MediaId.Movie? = null

        val resultsState =
            SearchUiState.Results(
                query = "matrix",
                items = persistentListOf(sampleMovie),
            )

        composeTestRule.setContent {
            SearchContent(
                state = resultsState,
                query = "matrix",
                onMovieClick = { clickedId = it },
            )
        }

        composeTestRule.onNodeWithText("Matrix Reloaded").performClick()
        assertTrue(clickedId != null, "Expected movie click callback to be invoked")
        assertTrue(clickedId.value.value == 42, "Expected movie id 42, got ${clickedId.value.value}")
    }

    @Test
    fun queryChange_updatesCallback() {
        var capturedQuery = ""

        composeTestRule.setContent {
            SearchContent(
                state = SearchUiState.Idle,
                query = capturedQuery,
                onQueryChange = { capturedQuery = it },
            )
        }

        composeTestRule.onNodeWithText("Search movies and shows").performTextInput("inception")
        assertTrue(capturedQuery == "inception", "Expected query to be 'inception', got '$capturedQuery'")
    }

    @Test
    fun results_displaysYearWhenAvailable() {
        val resultsState =
            SearchUiState.Results(
                query = "matrix",
                items = persistentListOf(sampleMovie),
            )

        composeTestRule.setContent {
            SearchContent(state = resultsState, query = "matrix")
        }

        // Year is displayed as part of the subtitle: "2003 · Sci-Fi, Action"
        composeTestRule.onNodeWithText("2003 · Sci-Fi, Action").assertIsDisplayed()
    }

    @Test
    fun multipleResults_allDisplayed() {
        val movie2 =
            sampleMovie.copy(
                id = Media.MediaId.Movie(MovieId(99)),
                title = "The Matrix",
                year = 1999,
            )

        val resultsState =
            SearchUiState.Results(
                query = "matrix",
                items = persistentListOf(sampleMovie, movie2),
            )

        composeTestRule.setContent {
            SearchContent(state = resultsState, query = "matrix")
        }

        composeTestRule.onNodeWithText("Matrix Reloaded").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Matrix").assertIsDisplayed()
    }
}
