package net.subsloth

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.library.DownloadGroupItem
import net.subsloth.library.DownloadsContent
import net.subsloth.library.DownloadsUiState
import net.subsloth.library.LibraryContent
import net.subsloth.library.LibraryUiState
import net.subsloth.settings.DiagnosticsState
import net.subsloth.settings.SettingsContent
import net.subsloth.settings.SettingsUiState
import org.junit.Rule
import org.junit.Test
import kotlin.time.Instant

class LibrarySettingsDownloadsDesktopTest {

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

    private val sampleQuality = QualityDescriptor(
        resolution = Resolution(1920, 1080),
        label = "1080p",
        bitrate = 5000,
        mimeType = "video/mp4",
    )

    @Test
    fun libraryContent_showsContinueWatching() {
        composeRule.setContent {
            MaterialTheme {
                LibraryContent(
                    state = LibraryUiState.Content(
                        isLoggedIn = true,
                        continueWatching = persistentListOf(sampleMovie),
                        favorites = persistentListOf<Media>(),
                        watchLater = persistentListOf<Media>(),
                        availableOffline = persistentListOf<Media>(),
                        custom = persistentListOf<Media>(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Continue Watching").assertIsDisplayed()
        composeRule.onNodeWithText("The Grand Adventure").assertIsDisplayed()
        composeRule.onNodeWithText("The Grand Adventure").assertHasClickAction()
    }

    @Test
    fun libraryContent_favoritesItem_hasClickAction() {
        composeRule.setContent {
            MaterialTheme {
                LibraryContent(
                    state = LibraryUiState.Content(
                        isLoggedIn = true,
                        continueWatching = persistentListOf<Media>(),
                        favorites = persistentListOf(sampleMovie),
                        watchLater = persistentListOf<Media>(),
                        availableOffline = persistentListOf<Media>(),
                        custom = persistentListOf<Media>(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Favorites").assertIsDisplayed()
        composeRule.onNodeWithText("The Grand Adventure").assertHasClickAction()
    }

    @Test
    fun libraryContent_emptyMessage_loggedOut() {
        composeRule.setContent {
            MaterialTheme {
                LibraryContent(
                    state = LibraryUiState.Content(
                        isLoggedIn = false,
                        continueWatching = persistentListOf<Media>(),
                        favorites = persistentListOf<Media>(),
                        watchLater = persistentListOf<Media>(),
                        availableOffline = persistentListOf<Media>(),
                        custom = persistentListOf<Media>(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Offline Library").assertIsDisplayed()
        composeRule.onNodeWithText(
            "No offline content available. Go online to browse and download.",
        ).assertIsDisplayed()
    }

    @Test
    fun libraryContent_emptyMessage_loggedIn() {
        composeRule.setContent {
            MaterialTheme {
                LibraryContent(
                    state = LibraryUiState.Content(
                        isLoggedIn = true,
                        continueWatching = persistentListOf<Media>(),
                        favorites = persistentListOf<Media>(),
                        watchLater = persistentListOf<Media>(),
                        availableOffline = persistentListOf<Media>(),
                        custom = persistentListOf<Media>(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("My Library").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Your library is empty. Browse the catalog to add items.",
        ).assertIsDisplayed()
    }

    @Test
    fun downloadsContent_showsHeaderAndEmpty() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsContent(
                    state = DownloadsUiState.Content(
                        active = persistentListOf<DownloadGroupItem>(),
                        queuedOrPaused = persistentListOf<DownloadGroupItem>(),
                        failedOrUnavailable = persistentListOf<DownloadGroupItem>(),
                        completed = persistentListOf<DownloadGroupItem>(),
                        seasonQueues = persistentListOf(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Downloads").assertIsDisplayed()
        composeRule.onNodeWithText("No downloads yet.").assertIsDisplayed()
    }

    @Test
    fun downloadsContent_showsActiveItem() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsContent(
                    state = DownloadsUiState.Content(
                        active = persistentListOf(
                            DownloadGroupItem(
                                state = DownloadState.Active(
                                    localId = LocalMediaIdentifier("d1"),
                                    mediaId = Media.MediaId.Movie(MovieId(1)),
                                    quality = sampleQuality,
                                    subtitleLanguages = persistentSetOf(),
                                    progressPercent = 42,
                                ),
                                progressFraction = 0.42,
                            ),
                        ),
                        queuedOrPaused = persistentListOf<DownloadGroupItem>(),
                        failedOrUnavailable = persistentListOf<DownloadGroupItem>(),
                        completed = persistentListOf<DownloadGroupItem>(),
                        seasonQueues = persistentListOf(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Active").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertHasClickAction()
        composeRule.onNodeWithText("Pause").assertHasClickAction()
    }

    @Test
    fun downloadsContent_showsCompletedSection() {
        composeRule.setContent {
            MaterialTheme {
                DownloadsContent(
                    state = DownloadsUiState.Content(
                        active = persistentListOf<DownloadGroupItem>(),
                        queuedOrPaused = persistentListOf<DownloadGroupItem>(),
                        failedOrUnavailable = persistentListOf<DownloadGroupItem>(),
                        completed = persistentListOf(
                            DownloadGroupItem(
                                state = DownloadState.Completed(
                                    localId = LocalMediaIdentifier("c1"),
                                    mediaId = Media.MediaId.Movie(MovieId(5)),
                                    quality = sampleQuality,
                                    subtitleLanguages = persistentSetOf(),
                                    downloadedAtEpochSeconds = Instant.fromEpochSeconds(1_700_000_000),
                                    sizeBytes = 1_500_000_000L,
                                    videoPath = OfflineRelativePath("movie5.mp4"),
                                ),
                            ),
                        ),
                        seasonQueues = persistentListOf(),
                    ),
                )
            }
        }

        composeRule.onNodeWithText("Completed").assertIsDisplayed()
    }

    @Test
    fun settingsContent_displaysSections() {
        val settingsState = SettingsUiState.Content(
            subtitleEnabled = true,
            subtitleLanguage = "English",
            quality = "1080p",
            playbackSpeed = 1.0f,
            downloadsWifiOnly = true,
            diagnostics = DiagnosticsState(),
        )

        composeRule.setContent {
            MaterialTheme {
                SettingsContent(state = settingsState)
            }
        }

        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Subtitle").assertIsDisplayed()
        composeRule.onNodeWithText("Subtitles enabled").assertIsDisplayed()
        composeRule.onNodeWithText("Quality & Playback").assertIsDisplayed()
        composeRule.onNodeWithText("Logout").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Logout").performScrollTo().assertHasClickAction()
    }

    @Test
    fun settingsContent_displaysQualityValue() {
        val settingsState = SettingsUiState.Content(
            subtitleEnabled = true,
            subtitleLanguage = null,
            quality = "1080p",
            playbackSpeed = 1.0f,
            downloadsWifiOnly = false,
            diagnostics = DiagnosticsState(),
        )

        composeRule.setContent {
            MaterialTheme {
                SettingsContent(state = settingsState)
            }
        }

        composeRule.onNodeWithText("1080p").assertIsDisplayed()
    }
}
