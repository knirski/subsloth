package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.auth.LoginFormContent
import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.details.MovieDetailContent
import net.subsloth.details.MovieDetailUiState
import org.junit.Rule
import org.junit.Test

class AccessibilityDesktopTest {

    @get:Rule
    val composeRule = createComposeRule()

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
    fun loginForm_offlineLibraryButton_hasClickAction() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "",
                    password = "",
                    apiBaseUrl = "http://localhost:8080/api/v2/",
                    isLoading = false,
                    error = null,
                    hasOfflineLibrary = true,
                )
            }
        }

        composeRule.onNodeWithText("Offline Library").assertHasClickAction()
    }

    @Test
    fun loginForm_authErrorMessage_displays() {
        composeRule.setContent {
            MaterialTheme {
                LoginFormContent(
                    login = "user",
                    password = "pass",
                    apiBaseUrl = "http://localhost:8080/api/v2/",
                    isLoading = false,
                    error = UiError.NotFound(),
                    hasOfflineLibrary = false,
                )
            }
        }
    }

    @Test
    fun movieDetail_favoriteButton_hasClickAction() {
        composeRule.setContent {
            MaterialTheme {
                MovieDetailContent(
                    state = MovieDetailUiState.Content(details = movieDetails),
                )
            }
        }

        composeRule.onNodeWithText("Favorite").assertHasClickAction()
    }
}
