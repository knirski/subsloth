package subsloth.details

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import subsloth.core.model.Availability
import subsloth.core.model.identifier.EpisodeId
import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.identifier.ShowId
import subsloth.core.model.media.Episode
import subsloth.core.model.media.Media
import subsloth.core.model.media.Season
import subsloth.core.model.media.ShowDetails
import subsloth.core.model.media.ShowStatus
import subsloth.core.model.media.Subtitle
import subsloth.core.model.media.SubtitleFormat
import subsloth.testing.assertions.assertThat
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleEpisode1 =
        Episode(
            id = EpisodeId(101),
            showId = ShowId(1),
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Episode 1",
            plot = "First episode",
            durationSeconds = 2700L,
            availability = Availability.Available,
            imdbId = null,
            qualities = persistentListOf(),
            subtitles = persistentListOf(),
            airDateEpochSeconds = Instant.fromEpochSeconds(1_700_000_000L),
            premiereDateEpochSeconds = null,
        )

    private val sampleEpisode2 =
        Episode(
            id = EpisodeId(102),
            showId = ShowId(1),
            seasonNumber = 1,
            episodeNumber = 2,
            title = "Episode 2",
            plot = "Second episode",
            durationSeconds = 2700L,
            availability = Availability.Available,
            imdbId = null,
            qualities = persistentListOf(),
            subtitles = persistentListOf(),
            airDateEpochSeconds = Instant.fromEpochSeconds(1_700_008_640L),
            premiereDateEpochSeconds = null,
        )

    private val sampleSeason1 =
        Season(
            seasonNumber = 1,
            title = "Season 1",
            plot = null,
            episodes = persistentListOf(sampleEpisode1, sampleEpisode2),
        )

    private val sampleShowDetails =
        ShowDetails(
            id = Media.MediaId.Show(ShowId(1)),
            title = "Test Show",
            plot = "A test show plot",
            description = "Full description",
            availability = Availability.Available,
            rating = 8.0,
            year = 2023,
            genres = persistentListOf("Drama"),
            durationMinutes = 45,
            qualities = persistentListOf(),
            subtitles = persistentListOf(
                Subtitle(
                    language = LanguageCode("en"),
                    languageDisplayName = "English",
                    url = null,
                    downloadUrl = null,
                    format = SubtitleFormat.SRT,
                ),
            ),
            slug = "test-show",
            imdbId = null,
            tmdbId = null,
            countries = persistentListOf("US"),
            posterUrl = "https://example.com/poster.jpg",
            backdropUrl = "https://example.com/backdrop.jpg",
            status = ShowStatus.ONGOING,
            popularity = 100,
            seasons = persistentListOf(sampleSeason1),
        )

    @Test
    fun `loads show details on init`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.details.title).isEqualTo("Test Show")
        }
    }

    @Test
    fun `shows error when details fail to load`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.failure(Exception("Network error")) },
        )
        viewModel.uiState.test {
            val error = awaitItem() as ShowDetailUiState.Error
            assertThat(error.error.detail).isEqualTo("Network error")
        }
    }

    @Test
    fun `displays seasons grouped with episodes`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.details.seasons).isNotEmpty()
            assertThat(content.details.seasons.first().seasonNumber).isEqualTo(1)
            assertThat(content.details.seasons.first().episodes).hasSize(2)
        }
    }

    @Test
    fun `selects first season by default`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(1)
        }
    }

    @Test
    fun `switches selected season`() = runTest(testDispatcher) {
        val season2 =
            Season(
                seasonNumber = 2,
                title = "Season 2",
                plot = null,
                episodes = persistentListOf(),
            )
        val showWithTwoSeasons = sampleShowDetails.copy(seasons = persistentListOf(sampleSeason1, season2))
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(showWithTwoSeasons) },
        )
        viewModel.selectSeason(2)
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(2)
        }
    }

    @Test
    fun `restores selected season from saved state`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
            savedState = mapOf("selectedSeason" to "1"),
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(1)
        }
    }

    @Test
    fun `episodes are sorted by episode number`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            val episodes = content.details.seasons.first().episodes
            assertThat(episodes.first().episodeNumber).isEqualTo(1)
            assertThat(episodes.last().episodeNumber).isEqualTo(2)
        }
    }

    @Test
    fun `no comments UI data is present in show details`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.details.description?.contains("comment", ignoreCase = true) != true).isTrue()
        }
    }

    @Test
    fun `episode rows show episode number and title`() = runTest(testDispatcher) {
        val viewModel = ShowDetailViewModel(
            mediaId = Media.MediaId.Show(ShowId(1)),
            getDetails = { Result.success(sampleShowDetails) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            val episodes = content.details.seasons.first().episodes
            assertThat(episodes.first().title).isEqualTo("Episode 1")
            assertThat(episodes.first().episodeNumber).isEqualTo(1)
        }
    }
}
