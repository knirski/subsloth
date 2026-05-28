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
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieDetails
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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

    @Test
    fun `loads movie details on init`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Result.success(sampleMovieDetails) },
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
            getDetails = { Result.failure(Exception("Network error")) },
        )
        viewModel.uiState.test {
            val error = awaitItem() as MovieDetailUiState.Error
            assertThat(error.error.detail).isEqualTo("Network error")
        }
    }

    @Test
    fun `displays movie title and plot`() = runTest(testDispatcher) {
        val viewModel = MovieDetailViewModel(
            mediaId = Media.MediaId.Movie(MovieId(1)),
            getDetails = { Result.success(sampleMovieDetails) },
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
            getDetails = { Result.success(sampleMovieDetails) },
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
            getDetails = { Result.success(sampleMovieDetails) },
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
            getDetails = { Result.success(sampleMovieDetails) },
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
            getDetails = { Result.success(sampleMovieDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as MovieDetailUiState.Content
            assertThat(content.details.description?.contains("comment", ignoreCase = true) != true).isTrue()
        }
    }
}
