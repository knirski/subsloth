package net.subsloth.screenshot

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.subsloth.catalog.MediaCard
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

private val movieItems: ImmutableList<Media> =
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
            plot = "A fast-paced action thriller in Tokyo.",
            year = 2023,
            rating = 7.2,
            genres = persistentListOf("Action", "Thriller"),
            availability = Availability.Available,
            backdropUrl = null,
            slug = "midnight-express",
            imdbId = null,
            durationMinutes = 110,
        ),
    )

private val showItems: ImmutableList<Media> =
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
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HomeRowLabel(label = "Movies")
            MediaRow(items = movieItems)
            HomeRowLabel(label = "Shows")
            MediaRow(items = showItems)
        }
    }
}

@Composable
private fun HomeRowLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun MediaRow(items: ImmutableList<Media>) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 4.dp),
    ) {
        items.forEach { media ->
            item(key = media.title) {
                MediaCard(media = media)
            }
        }
    }
}
