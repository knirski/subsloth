package net.subsloth.catalog

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads catalog and emits content with rows`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Test Movie",
                plot = "A test movie",
                availability = Availability.Available,
                rating = 8.0,
                year = 2024,
                genres = listOf("Action"),
                durationMinutes = 120,
                slug = "test-movie",
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.rows).isNotEmpty()
        }
    }

    @Test
    fun `includes movies row when movies exist`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Movie A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.rows.any { it is HomeRow.Movies }).isTrue()
        }
    }

    @Test
    fun `includes shows row when shows exist`() = runTest(testDispatcher) {
        val shows = listOf(
            ShowSummary(
                id = Media.MediaId.Show(ShowId(1)),
                title = "Show A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                countries = emptyList(),
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(shows) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.rows.any { it is HomeRow.Shows }).isTrue()
        }
    }

    @Test
    fun `shows recency row labeled Recently Added when updatedAt exists`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Movie A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                updatedAtEpochSeconds = Instant.fromEpochSeconds(1_000_000L),
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            val recencyRows = content.rows.filterIsInstance<HomeRow.Recency>()
            assertThat(recencyRows.any { it.label == "Recently Added" }).isTrue()
        }
    }

    @Test
    fun `shows recency row labeled Shows with recent episodes when newestVideo exists`() = runTest(testDispatcher) {
        val shows = listOf(
            ShowSummary(
                id = Media.MediaId.Show(ShowId(1)),
                title = "Show A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                countries = emptyList(),
                newestVideoEpochSeconds = Instant.fromEpochSeconds(1_000_000L),
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(shows) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            val recencyRows = content.rows.filterIsInstance<HomeRow.Recency>()
            assertThat(recencyRows.any { it.label == "Shows with recent episodes" }).isTrue()
        }
    }

    @Test
    fun `hides recency rows when no recency signal exists`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Movie A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                updatedAtEpochSeconds = null,
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            val recencyRows = content.rows.filterIsInstance<HomeRow.Recency>()
            assertThat(recencyRows).isEmpty()
        }
    }

    @Test
    fun `restores selected tab from saved state after process death`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(emptyList()) },
            savedState = mapOf("selectedTab" to "SHOWS", "searchQuery" to ""),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab.name).isEqualTo("SHOWS")
        }
    }

    @Test
    fun `defaults to movies tab when no saved state tab`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(emptyList()) },
            savedState = mapOf("selectedTab" to "", "searchQuery" to ""),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.MOVIES)
        }
    }

    @Test
    fun `shows cached data when offline`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Offline Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(movies) },
            isOnline = { false },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.rows).isNotEmpty()
        }
    }

    @Test
    fun `does not include comments-related data in any row`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Movie A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = emptyList(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            val allLabels = content.rows.mapNotNull { it.label }
            assertThat(allLabels.none { it.contains("comment", ignoreCase = true) }).isTrue()
        }
    }
}
