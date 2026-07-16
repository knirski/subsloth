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
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShowDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val mediaId = Media.MediaId.Show(ShowId(1))

    private val seasons = persistentListOf(
        Season(seasonNumber = 1, title = "Season 1", plot = null, episodes = persistentListOf()),
        Season(seasonNumber = 2, title = "Season 2", plot = null, episodes = persistentListOf()),
        Season(seasonNumber = 3, title = "Season 3", plot = null, episodes = persistentListOf()),
    )

    private val showDetails = ShowDetails(
        id = mediaId,
        title = "Test Show",
        plot = null,
        description = null,
        availability = Availability.Available,
        rating = null,
        year = null,
        genres = persistentListOf(),
        durationMinutes = null,
        qualities = persistentListOf(),
        subtitles = persistentListOf(),
        slug = null,
        imdbId = null,
        tmdbId = null,
        countries = persistentListOf(),
        posterUrl = null,
        backdropUrl = null,
        status = ShowStatus.ONGOING,
        popularity = null,
        seasons = seasons,
    )

    @Test
    fun `default season is first season when no saved state`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `saved selected season is restored`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            savedState = mapOf("selectedSeason" to "2"),
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `restores season 3 when that was the last viewed`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            savedState = mapOf("selectedSeason" to "3"),
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `invalid saved season falls back to first season`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            savedState = mapOf("selectedSeason" to "999"),
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `non-numeric saved season falls back to first`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            savedState = mapOf("selectedSeason" to "abc"),
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `season with single season defaults to that season`() = runTest(testDispatcher) {
        val singleSeason = ShowDetails(
            id = mediaId,
            title = "Mini Series",
            plot = null,
            description = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            qualities = persistentListOf(),
            subtitles = persistentListOf(),
            slug = null,
            imdbId = null,
            tmdbId = null,
            countries = persistentListOf(),
            posterUrl = null,
            backdropUrl = null,
            status = ShowStatus.ONGOING,
            popularity = null,
            seasons = persistentListOf(
                Season(seasonNumber = 1, title = "Only Season", plot = null, episodes = persistentListOf()),
            ),
        )
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(singleSeason) },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.selectedSeason).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
