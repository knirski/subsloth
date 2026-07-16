package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.MediaCard
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.details.MovieDetailContent
import net.subsloth.details.MovieDetailUiState
import net.subsloth.settings.DiagnosticsContent
import net.subsloth.settings.DiagnosticsState
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertTrue

class CatalogDetailDesktopTest {

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

    private val sampleShow = ShowSummary(
        id = Media.MediaId.Show(ShowId(1)),
        title = "The Last Kingdom",
        plot = "A tale of warriors and kingdoms.",
        availability = Availability.Available,
        rating = 8.9,
        year = 2023,
        genres = persistentListOf("Fantasy", "Adventure"),
        durationMinutes = 55,
        slug = "the-last-kingdom",
        imdbId = null,
        backdropUrl = null,
        status = ShowStatus.ONGOING,
        countries = persistentListOf("US"),
    )

    private val movieDetails = MovieDetails(
        id = Media.MediaId.Movie(MovieId(1)),
        title = "The Grand Adventure",
        plot = "An epic journey across uncharted lands.",
        description = null,
        availability = Availability.Available,
        rating = 8.5,
        year = 2024,
        genres = persistentListOf("Adventure", "Drama"),
        durationMinutes = 120,
        qualities = persistentListOf(
            Quality(info = QualityDescriptor(Resolution.HD_720, "720p", null, null), url = null, downloadUrl = null),
        ),
        subtitles = persistentListOf(
            Subtitle(
                language = LanguageCode("en"),
                languageDisplayName = "English",
                url = null,
                downloadUrl = null,
                format = SubtitleFormat.SRT,
            ),
        ),
        slug = "the-grand-adventure",
        imdbId = null,
        tmdbId = null,
        countries = persistentListOf("US", "UK"),
        posterUrl = null,
        backdropUrl = null,
    )

    @Test
    fun mediaCard_displaysMovieTitleAndRating() {
        composeRule.setContent {
            MaterialTheme {
                MediaCard(media = sampleMovie, onClick = {})
            }
        }

        composeRule.onNodeWithText("The Grand Adventure").assertIsDisplayed()
        composeRule.onNodeWithText("★ 8.5").assertIsDisplayed()
    }

    @Test
    fun mediaCard_displaysShowInfo() {
        composeRule.setContent {
            MaterialTheme {
                MediaCard(media = sampleShow, onClick = {})
            }
        }

        composeRule.onNodeWithText("The Last Kingdom").assertIsDisplayed()
        composeRule.onNodeWithText("★ 8.9").assertIsDisplayed()
        composeRule.onNodeWithText("Ongoing").assertIsDisplayed()
    }

    @Test
    fun mediaCard_hasClickAction() {
        composeRule.setContent {
            MaterialTheme {
                MediaCard(media = sampleMovie, onClick = {})
            }
        }

        composeRule.onNodeWithText("The Grand Adventure").assertHasClickAction()
    }

    @Test
    fun mediaCard_callsOnClick() {
        var clicked = false
        composeRule.setContent {
            MaterialTheme {
                MediaCard(media = sampleMovie, onClick = { clicked = true })
            }
        }

        composeRule.onNodeWithText("The Grand Adventure").performClick()
        assertTrue(clicked, "onClick should have been called")
    }

    @Test
    fun movieDetail_displaysTitleAndMetadata() {
        composeRule.setContent {
            MaterialTheme {
                MovieDetailContent(
                    state = MovieDetailUiState.Content(details = movieDetails),
                )
            }
        }

        composeRule.onNodeWithText("The Grand Adventure").assertIsDisplayed()
        composeRule.onNodeWithText("2024").assertIsDisplayed()
        composeRule.onNodeWithText("★ 8.5").assertIsDisplayed()
        composeRule.onNodeWithText("Adventure, Drama").assertIsDisplayed()
    }

    @Test
    fun movieDetail_displaysPlayButton() {
        composeRule.setContent {
            MaterialTheme {
                MovieDetailContent(
                    state = MovieDetailUiState.Content(details = movieDetails),
                )
            }
        }

        composeRule.onNodeWithText("Play").assertIsDisplayed()
        composeRule.onNodeWithText("Play").assertHasClickAction()
    }

    @Test
    fun movieDetail_noCommentsPresent() {
        composeRule.setContent {
            MaterialTheme {
                MovieDetailContent(
                    state = MovieDetailUiState.Content(details = movieDetails),
                )
            }
        }

        composeRule.onNodeWithText("Comments").assertDoesNotExist()
    }

    @Test
    fun movieDetail_showsDurationAndCountries() {
        composeRule.setContent {
            MaterialTheme {
                MovieDetailContent(
                    state = MovieDetailUiState.Content(details = movieDetails),
                )
            }
        }

        composeRule.onNodeWithText("120 min").assertIsDisplayed()
        composeRule.onNodeWithText("US, UK").assertIsDisplayed()
    }

    @Test
    fun diagnosticsContent_displaysTitle() {
        composeRule.setContent {
            MaterialTheme {
                DiagnosticsContent(state = DiagnosticsState())
            }
        }

        composeRule.onNodeWithText("Diagnostics").assertIsDisplayed()
        composeRule.onNodeWithText("Version information").assertIsDisplayed()
        composeRule.onNodeWithText("1.0.0").assertIsDisplayed()
    }
}
