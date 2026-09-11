package net.subsloth.details

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.LibraryError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.UiError
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class MovieDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleMovieDetails =
        MovieDetails(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Test Movie",
            plot = "A test movie plot",
            description = "Full description",
            availability = Availability.Available,
            rating = 8.5,
            year = 2024,
            genres = persistentListOf("Action", "Drama"),
            durationMinutes = 120,
            qualities = persistentListOf(
                Quality(
                    info = QualityDescriptor(
                        resolution = Resolution(1920, 1080),
                        label = "1080p",
                        bitrate = null,
                        mimeType = null,
                    ),
                    url = null,
                    downloadUrl = null,
                ),
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
            slug = "test-movie",
            imdbId = null,
            tmdbId = null,
            countries = persistentListOf("US"),
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = null,
        )

    private val sampleQuality = QualityDescriptor(
        resolution = Resolution(1920, 1080),
        label = "1080p",
        bitrate = null,
        mimeType = null,
    )

    @Test
    fun `loads movie details on init`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.title).isEqualTo("Test Movie")
        }
    }

    @Test
    fun `shows error when details fail to load`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Failure(DecodeError.SerializationFailed) },
        )
        viewModel.uiState.test {
            val error = awaitItem() as MovieDetailUiState.Error
            assertThat(error.error).isInstanceOf(UiError.ServiceError::class.java)
        }
    }

    @Test
    fun `displays movie title and plot`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.title).isEqualTo("Test Movie")
            assertThat(content.details.plot).isEqualTo("A test movie plot")
        }
    }

    @Test
    fun `displays movie rating year genres and duration`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.rating).isEqualTo(8.5)
            assertThat(content.details.year).isEqualTo(2024)
            assertThat(content.details.genres).containsExactly("Action", "Drama").inOrder()
            assertThat(content.details.durationMinutes).isEqualTo(120)
        }
    }

    @Test
    fun `displays subtitle languages`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.subtitles).isNotEmpty()
            assertThat(content.details.subtitles.first().languageDisplayName).isEqualTo("English")
        }
    }

    @Test
    fun `displays available qualities`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.qualities).isNotEmpty()
            assertThat(content.details.qualities.first().info.label).isEqualTo("1080p")
        }
    }

    @Test
    fun `no comments UI data is present in state`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.description?.contains("comment", ignoreCase = true) != true).isTrue()
        }
    }

    @Test
    fun `shows resume fraction from matching stored progress`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listProgress = { Result.success(listOf(progress(positionSeconds = 600, durationSeconds = 3600))) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.progressFraction).isEqualTo(600.0 / 3600.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores progress of other media`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listProgress = {
                Result.success(
                    listOf(
                        progress(
                            mediaId = Media.MediaId.Movie(MovieId(2)),
                            positionSeconds = 600,
                            durationSeconds = 3600,
                        ),
                    ),
                )
            },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.progressFraction).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores progress below resume threshold`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listProgress = { Result.success(listOf(progress(positionSeconds = 25, durationSeconds = 3600))) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.progressFraction).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores near-finished progress`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listProgress = { Result.success(listOf(progress(positionSeconds = 3500, durationSeconds = 3600))) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.progressFraction).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `progress failure still renders content without resume fraction`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listProgress = { Result.failure(IllegalStateException("progress unavailable")) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.progressFraction).isNull()
            assertThat(content.details.title).isEqualTo("Test Movie")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorite flag reflects favorites membership`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listLibrary = { Outcome.Success(listOf(libraryItem(LibraryCollection.FAVORITES))) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.isFavorite).isTrue()
            assertThat(content.isWatchLater).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `watch later flag reflects history membership`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listLibrary = { Outcome.Success(listOf(libraryItem(LibraryCollection.HISTORY))) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.isWatchLater).isTrue()
            assertThat(content.isFavorite).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `downloaded flag reflects completed download`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listDownloads = { Result.success(listOf(completedDownload())) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.isDownloaded).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `in-progress download does not set downloaded flag`() = runTest(testDispatcher) {
        val queued = DownloadState.Queued(
            localId = LocalMediaIdentifier("movie-1"),
            mediaId = Media.MediaId.Movie(MovieId(1)),
            quality = sampleQuality,
        )
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listDownloads = { Result.success(listOf(queued)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.isDownloaded).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library failure keeps content with flags false`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Outcome.Success(sampleMovieDetails) },
            listLibrary = { Outcome.Failure(LibraryError.NotSupported) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.isFavorite).isFalse()
            assertThat(content.isWatchLater).isFalse()
            assertThat(content.details.title).isEqualTo("Test Movie")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun libraryItem(collection: LibraryCollection): LibraryItem = LibraryItem(
        mediaId = Media.MediaId.Movie(MovieId(1)),
        collection = collection,
        addedAtEpochSeconds = Instant.fromEpochSeconds(0),
        sortOrder = 1,
    )

    private fun completedDownload(): DownloadState.Completed = DownloadState.Completed(
        localId = LocalMediaIdentifier("movie-1"),
        mediaId = Media.MediaId.Movie(MovieId(1)),
        quality = sampleQuality,
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(0),
        sizeBytes = 1024,
        videoPath = OfflineRelativePath.safe("videos/movie-1.mp4"),
    )

    private fun progress(
        mediaId: Media.MediaId = Media.MediaId.Movie(MovieId(1)),
        positionSeconds: Long,
        durationSeconds: Long,
    ): PlaybackProgress = PlaybackProgress(
        mediaId = mediaId,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        lastUpdatedEpochSeconds = Instant.fromEpochSeconds(0),
        isWatched = false,
    )
}
