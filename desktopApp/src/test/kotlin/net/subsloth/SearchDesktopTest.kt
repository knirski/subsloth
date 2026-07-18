package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.SearchContent
import net.subsloth.catalog.SearchUiState
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import org.junit.Rule
import org.junit.Test

class SearchDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleMovie = MovieSummary(
        id = Media.MediaId.Movie(MovieId(1)),
        title = "The Grand Adventure",
        plot = "An epic journey across uncharted lands.",
        availability = Availability.Available,
        rating = 8.5,
        year = 2024,
        genres = persistentListOf("Adventure", "Drama"),
        durationMinutes = 120,
        slug = "the-grand-adventure",
        imdbId = null,
        backdropUrl = null,
    )

    @Test
    fun searchContent_displaysIdleState() {
        composeRule.setContent {
            MaterialTheme {
                SearchContent(state = SearchUiState.Idle, query = "")
            }
        }

        composeRule.onNodeWithText("Search for movies and shows").assertIsDisplayed()
    }

    @Test
    fun searchContent_displaysSearchFieldPlaceholder() {
        composeRule.setContent {
            MaterialTheme {
                SearchContent(state = SearchUiState.Idle, query = "")
            }
        }

        composeRule.onNodeWithText("Search movies and shows").assertIsDisplayed()
    }

    @Test
    fun searchContent_showsLoadingState() {
        composeRule.setContent {
            MaterialTheme {
                SearchContent(state = SearchUiState.Loading(query = "test"), query = "test")
            }
        }

        composeRule.onNodeWithText("test").assertIsDisplayed()
    }

    @Test
    fun searchContent_showsEmptyResults() {
        composeRule.setContent {
            MaterialTheme {
                SearchContent(
                    state = SearchUiState.Results(query = "nonexistent", items = persistentListOf()),
                    query = "nonexistent",
                )
            }
        }

        composeRule.onNodeWithText("No results for \"nonexistent\"").assertIsDisplayed()
    }

    @Test
    fun searchContent_showsResults() {
        composeRule.setContent {
            MaterialTheme {
                SearchContent(
                    state = SearchUiState.Results(query = "grand", items = persistentListOf(sampleMovie)),
                    query = "grand",
                )
            }
        }

        composeRule.onNodeWithText("The Grand Adventure").assertIsDisplayed()
        composeRule.onNodeWithText("An epic journey across uncharted lands.").assertIsDisplayed()
    }
}
