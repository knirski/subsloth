package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.SearchContent
import net.subsloth.catalog.SearchUiState
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

private val searchResults: ImmutableList<Media> =
    persistentListOf(
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(3)),
            title = "The Lost Kingdom",
            plot = "A fantasy epic about a forgotten civilization.",
            availability = Availability.Available,
            rating = 9.1,
            year = 2024,
            genres = persistentListOf("Fantasy", "Adventure"),
            durationMinutes = 150,
            slug = "the-lost-kingdom",
            imdbId = null,
            backdropUrl = null,
            posterUrl = "https://artwork.invalid/search-1.jpg",
        ),
        ShowSummary(
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
            posterUrl = "https://artwork.invalid/search-2.jpg",
            status = ShowStatus.ONGOING,
            countries = persistentListOf("US"),
        ),
        MovieSummary(
            id = Media.MediaId.Movie(MovieId(4)),
            title = "Midnight Express",
            plot = "A fast-paced action thriller in Tokyo.",
            availability = Availability.Available,
            rating = 7.2,
            year = 2023,
            genres = persistentListOf("Action", "Thriller"),
            durationMinutes = 110,
            slug = "midnight-express",
            imdbId = null,
            backdropUrl = null,
            posterUrl = "https://artwork.invalid/search-3.jpg",
        ),
    )

private const val SEARCH_QUERY = "kingdom"

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
@Composable
fun SearchScreenLightScreenshot() {
    installFakeArtworkLoader()
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SearchContent(
                state = SearchUiState.Results(query = SEARCH_QUERY, items = searchResults),
                query = SEARCH_QUERY,
            )
        }
    }
}

@PreviewTest
@Preview(name = "Phone Dark", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Dark", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Dark", device = DEVICE_TV, showBackground = true)
@Composable
fun SearchScreenDarkScreenshot() {
    installFakeArtworkLoader()
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            SearchContent(
                state = SearchUiState.Results(query = SEARCH_QUERY, items = searchResults),
                query = SEARCH_QUERY,
            )
        }
    }
}
