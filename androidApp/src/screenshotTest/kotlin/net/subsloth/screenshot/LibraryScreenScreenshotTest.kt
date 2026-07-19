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
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.ui.theme.SubSlothTheme
import net.subsloth.library.LibraryContent
import net.subsloth.library.LibraryUiState
import net.subsloth.screenshot.DEVICE_PHONE
import net.subsloth.screenshot.DEVICE_TABLET
import net.subsloth.screenshot.DEVICE_TV

@PreviewTest
@Preview(name = "Phone Light", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Light", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Light", device = DEVICE_TV, showBackground = true)
@Composable
fun LibraryScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryContent(
                state =
                    LibraryUiState.Content(
                        isLoggedIn = true,
                        continueWatching =
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
                                    slug = null,
                                    imdbId = null,
                                    durationMinutes = 120,
                                ),
                            ),
                        favorites =
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
                                    slug = null,
                                    imdbId = null,
                                    durationMinutes = 55,
                                    status = ShowStatus.ONGOING,
                                    countries = persistentListOf("US"),
                                ),
                            ),
                        watchLater = persistentListOf(),
                        availableOffline = persistentListOf(),
                        custom = persistentListOf(),
                    ),
            )
        }
    }
}

@PreviewTest
@Preview(name = "Phone Dark", device = DEVICE_PHONE, showBackground = true)
@Preview(name = "Tablet Dark", device = DEVICE_TABLET, showBackground = true)
@Preview(name = "TV Dark", device = DEVICE_TV, showBackground = true)
@Composable
fun LibraryScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LibraryContent(
                state =
                    LibraryUiState.Content(
                        isLoggedIn = true,
                        continueWatching =
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
                                    slug = null,
                                    imdbId = null,
                                    durationMinutes = 120,
                                ),
                            ),
                        favorites =
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
                                    slug = null,
                                    imdbId = null,
                                    durationMinutes = 55,
                                    status = ShowStatus.ONGOING,
                                    countries = persistentListOf("US"),
                                ),
                            ),
                        watchLater = persistentListOf(),
                        availableOffline = persistentListOf(),
                        custom = persistentListOf(),
                    ),
            )
        }
    }
}
