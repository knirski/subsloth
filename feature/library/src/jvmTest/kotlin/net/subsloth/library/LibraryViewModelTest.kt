package net.subsloth.library

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleMovieId = Media.MediaId.Movie(MovieId(1))
    private val sampleMovie = MovieSummary(
        id = sampleMovieId,
        title = "Test Movie",
        plot = "A test movie",
        availability = Availability.Available,
        rating = 8.0,
        year = 2024,
        genres = persistentListOf("Action"),
        durationMinutes = 120,
        slug = "test-movie",
        imdbId = null,
        backdropUrl = null,
    )
    private val sampleQuality = QualityDescriptor(
        resolution = Resolution(1920, 1080),
        label = "1080p",
        bitrate = null,
        mimeType = null,
    )
    private val samplePath = OfflineRelativePath.safe("videos/test.mp4")
    private val sampleCompleted = DownloadState.Completed(
        localId = LocalMediaIdentifier("test-local-id"),
        mediaId = sampleMovieId,
        quality = sampleQuality,
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(1000),
        sizeBytes = 1024L * 1024L * 500L,
        videoPath = samplePath,
    )

    @Test
    fun `loads library and emits content with rows when logged in`() = runTest(testDispatcher) {
        val libraryItems = listOf(
            LibraryItem(
                mediaId = sampleMovieId,
                collection = LibraryCollection.FAVORITES,
                addedAtEpochSeconds = Instant.fromEpochSeconds(100),
                sortOrder = 1,
            ),
        )
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(libraryItems) },
            downloadsPort = { Result.success(persistentListOf()) },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.isLoggedIn).isTrue()
            assertThat(content.favorites).isNotEmpty()
        }
    }

    @Test
    fun `includes continue watching row when progress exists`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf()) },
            listProgress = {
                Result.success(
                    listOf(
                        net.subsloth.core.model.progress.PlaybackProgress(
                            mediaId = sampleMovieId,
                            positionSeconds = 300L,
                            durationSeconds = 1200L,
                            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1000),
                            isWatched = false,
                        ),
                    ),
                )
            },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.continueWatching).isNotEmpty()
            assertThat(content.continueWatching.any { it.title == "Test Movie" }).isTrue()
        }
    }

    @Test
    fun `includes favorites row when favorites exist`() = runTest(testDispatcher) {
        val libraryItems = listOf(
            LibraryItem(
                mediaId = sampleMovieId,
                collection = LibraryCollection.FAVORITES,
                addedAtEpochSeconds = Instant.fromEpochSeconds(100),
                sortOrder = 1,
            ),
        )
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(libraryItems) },
            downloadsPort = { Result.success(persistentListOf()) },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.favorites).isNotEmpty()
        }
    }

    @Test
    fun `includes watch later row when history exists`() = runTest(testDispatcher) {
        val libraryItems = listOf(
            LibraryItem(
                mediaId = sampleMovieId,
                collection = LibraryCollection.HISTORY,
                addedAtEpochSeconds = Instant.fromEpochSeconds(100),
                sortOrder = 1,
            ),
        )
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(libraryItems) },
            downloadsPort = { Result.success(persistentListOf()) },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.watchLater).isNotEmpty()
        }
    }

    @Test
    fun `includes available offline row when downloads are completed`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf(sampleCompleted)) },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.availableOffline).isNotEmpty()
        }
    }

    @Test
    fun `emits offline library when logged out`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf(sampleCompleted)) },
            isLoggedIn = { false },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.isLoggedIn).isFalse()
            assertThat(content.availableOffline).isNotEmpty()
        }
    }

    @Test
    fun `offline library does not show favorites or watch later`() = runTest(testDispatcher) {
        val libraryItems = listOf(
            LibraryItem(
                mediaId = sampleMovieId,
                collection = LibraryCollection.FAVORITES,
                addedAtEpochSeconds = Instant.fromEpochSeconds(100),
                sortOrder = 1,
            ),
        )
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(libraryItems) },
            downloadsPort = { Result.success(persistentListOf(sampleCompleted)) },
            isLoggedIn = { false },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.isLoggedIn).isFalse()
            assertThat(content.favorites).isEmpty()
            assertThat(content.availableOffline).isNotEmpty()
        }
    }

    @Test
    fun `offline library does not allow catalog search actions`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf(sampleCompleted)) },
            isLoggedIn = { false },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.isLoggedIn).isFalse()
        }
    }

    @Test
    fun `includes custom row when custom collection exists`() = runTest(testDispatcher) {
        val libraryItems = listOf(
            LibraryItem(
                mediaId = sampleMovieId,
                collection = LibraryCollection.CUSTOM,
                addedAtEpochSeconds = Instant.fromEpochSeconds(100),
                sortOrder = 1,
            ),
        )
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(libraryItems) },
            downloadsPort = { Result.success(persistentListOf()) },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.custom).isNotEmpty()
        }
    }

    @Test
    fun `hides continue watching when no progress`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf()) },
            listProgress = { Result.success(emptyList()) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            assertThat(content.continueWatching).isEmpty()
        }
    }

    @Test
    fun `delete download action removes download`() = runTest(testDispatcher) {
        var removed = false
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf(sampleCompleted)) },
            removeDownload = {
                removed = true
                Result.success(DownloadCommandOutcome.Applied)
            },
            listMovies = { Result.success(listOf(sampleMovie)) },
        )
        viewModel.deleteDownload("test-local-id")
        assertThat(removed).isTrue()
    }

    @Test
    fun `does not include comments-related data in any row`() = runTest(testDispatcher) {
        val viewModel = LibraryViewModel(
            libraryPort = { Outcome.Success(emptyList()) },
            downloadsPort = { Result.success(persistentListOf()) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as LibraryUiState.Content
            val allLabels = listOfNotNull(
                content.favorites,
                content.watchLater,
                content.continueWatching,
                content.availableOffline,
            )
                .flatMap { it }
                .map { it.title }
            assertThat(allLabels.none { it.contains("comment", ignoreCase = true) }).isTrue()
        }
    }
}
