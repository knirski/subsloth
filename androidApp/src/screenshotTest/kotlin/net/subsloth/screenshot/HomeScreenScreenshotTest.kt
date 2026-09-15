package net.subsloth.screenshot

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.HomeRow
import net.subsloth.catalog.HomeScreenScaffold
import net.subsloth.catalog.HomeTab
import net.subsloth.catalog.HomeUiState
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.screenshot.DEVICE_PHONE
import net.subsloth.screenshot.DEVICE_TABLET
import net.subsloth.screenshot.DEVICE_TV

private val movieItems: ImmutableList<MovieSummary> =
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
            posterUrl = "https://artwork.invalid/movie-1.jpg",
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
            posterUrl = "https://artwork.invalid/movie-2.jpg",
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
            posterUrl = "https://artwork.invalid/movie-3.jpg",
            imdbId = null,
            durationMinutes = 150,
        ),
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(4)),
            title = "Midnight Express",
            plot = "A fast-paced action thriller in Tokyo.",
            year = 2023,
            rating = 7.2,
            genres = persistentListOf("Action", "Thriller"),
            availability = Availability.Available,
            backdropUrl = null,
            slug = "midnight-express",
            posterUrl = "https://artwork.invalid/movie-4.jpg",
            imdbId = null,
            durationMinutes = 110,
        ),
    )

private val showItems: ImmutableList<ShowSummary> =
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
            posterUrl = "https://artwork.invalid/show-1.jpg",
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
            posterUrl = "https://artwork.invalid/show-2.jpg",
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
            posterUrl = "https://artwork.invalid/show-3.jpg",
            imdbId = null,
            durationMinutes = 50,
            status = ShowStatus.UPCOMING,
            countries = persistentListOf("Australia"),
        ),
    )

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
@Composable
fun HomeScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        HomeScreenScreenshotContent()
    }
}

@PreviewTest
@Preview(name = "Phone Dark", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Dark", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Dark", device = DEVICE_TV, showBackground = true)
@Composable
fun HomeScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        HomeScreenScreenshotContent()
    }
}

@Composable
private fun HomeScreenScreenshotContent() {
    installFakeArtworkLoader()
    val state =
        HomeUiState.Content(
            rows =
                persistentListOf(
                    HomeRow.Movies(items = movieItems),
                    HomeRow.Shows(items = showItems),
                ),
            selectedTab = HomeTab.HOME,
            continueWatching = persistentListOf(movieItems[0], showItems[0]),
            availableOffline = persistentListOf(movieItems[1]),
        )
    HomeScreenScaffold(
        state = state,
        isSyncing = false,
        snackbarHostState = remember { SnackbarHostState() },
    )
}
