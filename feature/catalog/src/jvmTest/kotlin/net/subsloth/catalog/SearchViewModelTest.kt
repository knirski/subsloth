package net.subsloth.catalog

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
    fun `starts in idle state`() = runTest(testDispatcher) {
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(emptyList()) },
        )
        assertThat(viewModel.uiState.value).isInstanceOf(SearchUiState.Idle::class.java)
    }

    @Test
    fun `search returns matching movies`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "The Dark Knight",
                plot = "A Batman movie",
                availability = Availability.Available,
                rating = 9.0,
                year = 2008,
                genres = persistentListOf("Action"),
                durationMinutes = 152,
                slug = "dark-knight",
                imdbId = null,
                backdropUrl = null,
            ),
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(2)),
                title = "Inception",
                plot = "A dream heist movie",
                availability = Availability.Available,
                rating = 8.8,
                year = 2010,
                genres = persistentListOf("Thriller"),
                durationMinutes = 148,
                slug = "inception",
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.search("dark")
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).hasSize(1)
            assertThat(result.items.first().title).isEqualTo("The Dark Knight")
        }
    }

    @Test
    fun `search returns empty when no match`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Inception",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.search("nonexistent")
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).isEmpty()
        }
    }

    @Test
    fun `search filters by movie type`() = runTest(testDispatcher) {
        val items: List<Media> = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Movie A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
            ShowSummary(
                id = Media.MediaId.Show(ShowId(1)),
                title = "Show A",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                countries = persistentListOf(),
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(items) },
        )
        viewModel.updateFilters(SearchFilters(type = MediaTypeFilter.MOVIES))
        viewModel.search("A")
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).hasSize(1)
            assertThat(result.items.first()).isInstanceOf(MovieSummary::class.java)
        }
    }

    @Test
    fun `restores query from saved state on init`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Saved Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
            savedState = mapOf("searchQuery" to "Saved"),
        )
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.query).isEqualTo("Saved")
        }
    }

    @Test
    fun `filters movies by genre`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Action Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf("Action"),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(2)),
                title = "Comedy Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf("Comedy"),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.updateFilters(SearchFilters(genre = "Action"))
        viewModel.search("Movie")
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).hasSize(1)
            assertThat(result.items.first().title).isEqualTo("Action Movie")
        }
    }

    @Test
    fun `search completes with results`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Test Result",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.search("Test")
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).isNotEmpty()
        }
    }

    @Test
    fun `updateFilters retriggers search with current query`() = runTest(testDispatcher) {
        val items = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Action Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf("Action"),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(2)),
                title = "Comedy Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf("Comedy"),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(items) },
        )
        viewModel.search("Movie")
        viewModel.updateFilters(SearchFilters(genre = "Action"))
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).hasSize(1)
            assertThat(result.items.first().title).isEqualTo("Action Movie")
        }
    }

    @Test
    fun `empty search query returns no results`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Any Movie",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.search("")
        viewModel.uiState.test {
            val result = awaitItem() as SearchUiState.Results
            assertThat(result.items).isEmpty()
        }
    }

    @Test
    fun `search emits loading state before results`() = runTest(testDispatcher) {
        val movies = listOf(
            MovieSummary(
                id = Media.MediaId.Movie(MovieId(1)),
                title = "Test",
                plot = null,
                availability = Availability.Available,
                rating = null,
                year = null,
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = SearchViewModel(
            listCatalog = { Result.success(movies) },
        )
        viewModel.uiState.test {
            assertThat(awaitItem()).isInstanceOf(SearchUiState.Idle::class.java)
            viewModel.search("Test")
            // Turbine may conflate Loading + Results on a synchronous
            // dispatcher; instead we assert the *first* non-Idle
            // emission is Loading and the final emission is Results.
            var sawLoading = false
            var sawResults = false
            while (!sawResults) {
                val current = awaitItem()
                if (current is SearchUiState.Loading) {
                    sawLoading = true
                    assertThat(current.query).isEqualTo("Test")
                }
                if (current is SearchUiState.Results) {
                    sawResults = true
                    assertThat(current.query).isEqualTo("Test")
                    assertThat(current.items).hasSize(1)
                }
            }
            assertThat(sawLoading).isTrue()
        }
    }
}
