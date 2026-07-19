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
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.details.ShowDetailContent
import net.subsloth.details.ShowDetailUiState
import kotlin.time.Instant

@PreviewTest
@Preview(name = "Phone Light", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Light", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Light", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun SeriesDetailLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ShowDetailContent(state = showDetailContentState())
        }
    }
}

@PreviewTest
@Preview(name = "Phone Dark", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Dark", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Dark", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun SeriesDetailDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            ShowDetailContent(state = showDetailContentState())
        }
    }
}

private fun showDetailContentState(): ShowDetailUiState.Content =
    ShowDetailUiState.Content(
        details =
            ShowDetails(
                id = Media.MediaId.Show(ShowId(1)),
                title = "Sample Series Title",
                plot = "In a world where magic is real and dragons roam the skies, a young apprentice must master the ancient arts to save her kingdom.",
                description = "A thrilling fantasy series that combines epic storytelling with stunning visual effects, following the journey of hope and destiny.",
                availability = Availability.Available,
                rating = 9.2,
                year = 2023,
                genres = persistentListOf("Fantasy", "Adventure", "Action"),
                durationMinutes = 55,
                qualities = persistentListOf(),
                subtitles = persistentListOf(),
                slug = null,
                imdbId = null,
                tmdbId = null,
                countries = persistentListOf("US", "UK", "Canada"),
                posterUrl = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                popularity = 95,
                seasons =
                    persistentListOf(
                        Season(
                            seasonNumber = 1,
                            title = "Season 1",
                            plot = "The beginning of the journey",
                            episodes =
                                persistentListOf(
                                    Episode(
                                        id = EpisodeId(1),
                                        showId = ShowId(1),
                                        seasonNumber = 1,
                                        episodeNumber = 1,
                                        title = "The Awakening",
                                        plot = "A young apprentice discovers her hidden powers.",
                                        durationSeconds = 3300,
                                        availability = Availability.Available,
                                        imdbId = null,
                                        qualities = persistentListOf(),
                                        subtitles = persistentListOf(),
                                        airDateEpochSeconds = Instant.parse("2023-01-15T00:00:00Z"),
                                        premiereDateEpochSeconds = null,
                                    ),
                                    Episode(
                                        id = EpisodeId(2),
                                        showId = ShowId(1),
                                        seasonNumber = 1,
                                        episodeNumber = 2,
                                        title = "The Journey Begins",
                                        plot = "Our hero sets out on an epic quest.",
                                        durationSeconds = 3000,
                                        availability = Availability.Available,
                                        imdbId = null,
                                        qualities = persistentListOf(),
                                        subtitles = persistentListOf(),
                                        airDateEpochSeconds = Instant.parse("2023-01-22T00:00:00Z"),
                                        premiereDateEpochSeconds = null,
                                    ),
                                ),
                        ),
                        Season(
                            seasonNumber = 2,
                            title = "Season 2",
                            plot = "The stakes get higher",
                            episodes =
                                persistentListOf(
                                    Episode(
                                        id = EpisodeId(3),
                                        showId = ShowId(1),
                                        seasonNumber = 2,
                                        episodeNumber = 1,
                                        title = "New Horizons",
                                        plot = "New challenges await our heroes.",
                                        durationSeconds = 3600,
                                        availability = Availability.Available,
                                        imdbId = null,
                                        qualities = persistentListOf(),
                                        subtitles = persistentListOf(),
                                        airDateEpochSeconds = Instant.parse("2024-01-10T00:00:00Z"),
                                        premiereDateEpochSeconds = null,
                                    ),
                                ),
                        ),
                    ),
            ),
        selectedSeason = 1,
        isFavorite = true,
        isWatchLater = false,
        isDownloaded = false,
        progressFraction = 0.33,
    )
