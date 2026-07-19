@file:Suppress("ktlint:standard:max-line-length")

package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.details.MovieDetailContent
import net.subsloth.details.MovieDetailUiState
import net.subsloth.screenshot.DEVICE_PHONE
import net.subsloth.screenshot.DEVICE_TABLET
import net.subsloth.screenshot.DEVICE_TV

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
@Composable
fun MovieDetailLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MovieDetailContent(state = movieDetailContentState())
        }
    }
}

@PreviewTest
@Preview(name = "Phone Dark", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Dark", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Dark", device = DEVICE_TV, showBackground = true)
@Composable
fun MovieDetailDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            MovieDetailContent(state = movieDetailContentState())
        }
    }
}

private fun movieDetailContentState(): MovieDetailUiState.Content =
    MovieDetailUiState.Content(
        details =
            MovieDetails(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Sample Movie Title",
                plot = "A brave explorer discovers an ancient civilization hidden beneath the ocean, facing challenges that test both courage and wit.",
                description = "An epic adventure spanning three continents, this critically acclaimed film follows the journey of discovery, loss, and redemption.",
                availability = Availability.Available,
                rating = 8.5,
                year = 2024,
                genres = persistentListOf("Adventure", "Drama", "Sci-Fi"),
                durationMinutes = 148,
                qualities = persistentListOf(),
                subtitles = persistentListOf(),
                slug = null,
                imdbId = null,
                tmdbId = null,
                countries = persistentListOf("US", "UK"),
                posterUrl = null,
                backdropUrl = null,
            ),
        isFavorite = true,
        isWatchLater = false,
        isDownloaded = false,
        progressFraction = 0.0,
    )
