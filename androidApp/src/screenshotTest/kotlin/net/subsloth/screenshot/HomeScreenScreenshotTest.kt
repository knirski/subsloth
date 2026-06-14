package net.subsloth.screenshot

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.CatalogContent
import net.subsloth.catalog.HomeRow
import net.subsloth.catalog.HomeTab
import net.subsloth.catalog.HomeUiState
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun HomeScreenScreenshot() {
    MaterialTheme {
        CatalogContent(
            state =
                HomeUiState.Content(
                    rows =
                        persistentListOf(
                            HomeRow.Movies(
                                items =
                                    persistentListOf(
                                        MovieSummary(
                                            id = Media.MediaId.Movie(MovieId(1)),
                                            title = "The Grand Adventure",
                                            plot = "An epic journey across uncharted lands.",
                                            year = 2024,
                                            rating = 8.5,
                                            genres = persistentListOf("Adventure", "Drama"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "the-grand-adventure",
                                            imdbId = null,
                                            durationMinutes = 120,
                                        ),
                                        MovieSummary(
                                            id = Media.MediaId.Movie(MovieId(2)),
                                            title = "Stellar Origins",
                                            plot = "A sci-fi thriller about the origins of the universe.",
                                            year = 2023,
                                            rating = 7.8,
                                            genres = persistentListOf("Sci-Fi", "Thriller"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "stellar-origins",
                                            imdbId = null,
                                            durationMinutes = 135,
                                        ),
                                        MovieSummary(
                                            id = Media.MediaId.Movie(MovieId(3)),
                                            title = "The Lost Kingdom",
                                            plot = "A fantasy epic about a forgotten civilization.",
                                            year = 2024,
                                            rating = 9.1,
                                            genres = persistentListOf("Fantasy", "Adventure"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "the-lost-kingdom",
                                            imdbId = null,
                                            durationMinutes = 150,
                                        ),
                                        MovieSummary(
                                            id = Media.MediaId.Movie(MovieId(4)),
                                            title = "Midnight Express",
                                            plot = "A fast-paced action thriller set in the neon-lit streets of Tokyo.",
                                            year = 2023,
                                            rating = 7.2,
                                            genres = persistentListOf("Action", "Thriller"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "midnight-express",
                                            imdbId = null,
                                            durationMinutes = 110,
                                        ),
                                    ),
                            ),
                            HomeRow.Shows(
                                items =
                                    persistentListOf(
                                        ShowSummary(
                                            id = Media.MediaId.Show(ShowId(1)),
                                            title = "The Last Kingdom",
                                            plot = "A tale of warriors and kingdoms.",
                                            year = 2023,
                                            rating = 8.9,
                                            genres = persistentListOf("Fantasy", "Adventure"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "the-last-kingdom",
                                            imdbId = null,
                                            durationMinutes = 55,
                                            status = ShowStatus.ONGOING,
                                            countries = persistentListOf("US"),
                                        ),
                                        ShowSummary(
                                            id = Media.MediaId.Show(ShowId(2)),
                                            title = "Quantum Break",
                                            plot = "Scientists discover a way to manipulate time.",
                                            year = 2024,
                                            rating = 8.3,
                                            genres = persistentListOf("Sci-Fi", "Drama"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "quantum-break",
                                            imdbId = null,
                                            durationMinutes = 45,
                                            status = ShowStatus.ENDED,
                                            countries = persistentListOf("US", "UK"),
                                        ),
                                        ShowSummary(
                                            id = Media.MediaId.Show(ShowId(3)),
                                            title = "Ocean's Reach",
                                            plot = "A marine biology drama set on a remote island.",
                                            year = 2024,
                                            rating = 7.6,
                                            genres = persistentListOf("Drama", "Nature"),
                                            availability = Availability.Available,
                                            backdropUrl = null,
                                            slug = "oceans-reach",
                                            imdbId = null,
                                            durationMinutes = 50,
                                            status = ShowStatus.UPCOMING,
                                            countries = persistentListOf("Australia"),
                                        ),
                                    ),
                            ),
                        ),
                    selectedTab = HomeTab.MOVIES,
                ),
        )
    }
}
