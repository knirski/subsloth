package net.subsloth.catalog

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.SyncError
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

    private fun catalogItemsFor(media: List<Media>) = { type: String ->
        flowOf(
            media.filter {
                when (type) {
                    "movie" -> it is MovieSummary
                    "show" -> it is ShowSummary
                    else -> false
                }
            },
        )
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
                genres = persistentListOf("Action"),
                durationMinutes = 120,
                slug = "test-movie",
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(movies),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(movies),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                countries = persistentListOf(),
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(shows),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                updatedAtEpochSeconds = Instant.fromEpochSeconds(1_000_000L),
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(movies),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                status = ShowStatus.ONGOING,
                countries = persistentListOf(),
                newestVideoEpochSeconds = Instant.fromEpochSeconds(1_000_000L),
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(shows),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
                updatedAtEpochSeconds = null,
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(movies),
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
            catalogItems = catalogItemsFor(emptyList()),
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
            catalogItems = catalogItemsFor(emptyList()),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(movies),
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
                genres = persistentListOf(),
                durationMinutes = null,
                slug = null,
                imdbId = null,
                backdropUrl = null,
            ),
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(movies),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            val allLabels = content.rows.mapNotNull { it.label }
            assertThat(allLabels.none { it.contains("comment", ignoreCase = true) }).isTrue()
        }
    }

    @Test
    fun `emits sync error on manual sync failure`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            syncCatalog = { Result.failure(IllegalStateException("boom")) },
            isCatalogStale = { false },
        )
        viewModel.syncErrors.test {
            viewModel.sync()
            val error = awaitItem()
            assertThat(error).isInstanceOf(SyncError.Unknown::class.java)
        }
    }

    @Test
    fun `isSyncing transitions true during sync then false after`() = runTest(testDispatcher) {
        val syncGate = CompletableDeferred<Unit>(parent = coroutineContext[Job])
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            syncCatalog = suspend {
                syncGate.await()
                Result.success(Unit)
            },
            isCatalogStale = { false },
        )
        viewModel.isSyncing.test {
            assertThat(awaitItem()).isFalse()
            viewModel.sync()
            assertThat(awaitItem()).isTrue()
            syncGate.complete(Unit)
            assertThat(awaitItem()).isFalse()
        }
    }

    @Test
    fun `retrySync calls sync`() = runTest(testDispatcher) {
        var syncCalled = false
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            syncCatalog = suspend {
                syncCalled = true
                Result.success(Unit)
            },
            isCatalogStale = { false },
        )
        viewModel.retrySync()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(syncCalled).isTrue()
    }
}
