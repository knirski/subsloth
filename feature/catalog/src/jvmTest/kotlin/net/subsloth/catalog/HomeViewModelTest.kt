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
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.error.asFailure
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.model.progress.PlaybackProgress
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
    fun `marks movies unavailable when only shows exist`() = runTest(testDispatcher) {
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
            assertThat(content.moviesUnavailable).isTrue()
        }
    }

    @Test
    fun `does not mark movies unavailable when movies exist`() = runTest(testDispatcher) {
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
            assertThat(content.moviesUnavailable).isFalse()
        }
    }

    @Test
    fun `does not mark movies unavailable while catalog is empty`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.moviesUnavailable).isFalse()
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
            assertThat(content.selectedTab).isEqualTo(HomeTab.SHOWS)
        }
    }

    @Test
    fun `selecting a tab persists it for a recreated view model`() = runTest(testDispatcher) {
        val persisted = mutableListOf<String>()
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            onTabPersisted = persisted::add,
        )

        viewModel.selectTab(HomeTab.SHOWS)
        viewModel.selectTab(HomeTab.SHOWS)

        assertThat(persisted).containsExactly("SHOWS")
    }

    @Test
    fun `defaults to home tab when no saved state tab`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            savedState = mapOf("selectedTab" to "", "searchQuery" to ""),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.HOME)
        }
    }

    @Test
    fun `saved MOVIES tab is restored`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            savedState = mapOf("selectedTab" to "MOVIES"),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.MOVIES)
        }
    }

    @Test
    fun `legacy SEARCH saved tab restores home`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            savedState = mapOf("selectedTab" to "SEARCH"),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.HOME)
        }
    }

    @Test
    fun `invalid saved tab defaults to home`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            savedState = mapOf("selectedTab" to "INVALID"),
        )
        viewModel.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.HOME)
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
            syncCatalog = { SyncError.Unknown.asFailure() },
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
                Outcome.Success(Unit)
            },
            isCatalogStale = { false },
        )
        viewModel.uiState.test {
            var current = awaitItem()
            while (current !is HomeUiState.Content) current = awaitItem()
            assertThat(current.isSyncing).isFalse()
            viewModel.sync()
            current = awaitItem()
            while (current !is HomeUiState.Content) current = awaitItem()
            assertThat(current.isSyncing).isTrue()
            syncGate.complete(Unit)
            current = awaitItem()
            while (current !is HomeUiState.Content) current = awaitItem()
            assertThat(current.isSyncing).isFalse()
        }
    }

    @Test
    fun `retrySync calls sync`() = runTest(testDispatcher) {
        var syncCalled = false
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            syncCatalog = suspend {
                syncCalled = true
                Outcome.Success(Unit)
            },
            isCatalogStale = { false },
        )
        viewModel.retrySync()
        testDispatcher.scheduler.advanceUntilIdle()
        assertThat(syncCalled).isTrue()
    }

    @Test
    fun `buildHomeContent maps progress, downloads and personal collections`() {
        val movie = MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Favorite Movie",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
        )
        val show = ShowSummary(
            id = Media.MediaId.Show(ShowId(2)),
            title = "Watch Later Show",
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
        )

        val content = buildHomeContent(
            movies = listOf(movie),
            shows = listOf(show),
            library = listOf(
                libraryItem(movie.id, LibraryCollection.FAVORITES),
                libraryItem(show.id, LibraryCollection.WATCH_LATER),
            ),
            downloads = listOf(completedDownload(show.id)),
            progress = listOf(progress(movie.id, fraction = 0.5)),
        )

        assertThat(content.favorites).containsExactly(movie)
        assertThat(content.watchLater).containsExactly(show)
        assertThat(content.availableOffline).containsExactly(show)
        assertThat(content.continueWatching).containsExactly(movie)
    }

    @Test
    fun `buildHomeContent ignores progress outside the in-progress range`() {
        val movie = MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Barely Started",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
        )

        val content = buildHomeContent(
            movies = listOf(movie),
            shows = emptyList(),
            progress = listOf(
                progress(movie.id, fraction = 0.02),
                progress(movie.id, fraction = 0.95),
            ),
        )

        assertThat(content.continueWatching).isEmpty()
    }

    @Test
    fun `selectTab switches tab and reloads personal data`() = runTest(testDispatcher) {
        val movie = MovieSummary(
            id = Media.MediaId.Movie(MovieId(1)),
            title = "Favorite Movie",
            plot = null,
            availability = Availability.Available,
            rating = null,
            year = null,
            genres = persistentListOf(),
            durationMinutes = null,
            slug = null,
            imdbId = null,
            backdropUrl = null,
        )
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(listOf(movie)),
            listLibrary = { Outcome.Success(listOf(libraryItem(movie.id, LibraryCollection.FAVORITES))) },
        )
        viewModel.uiState.test {
            var content = awaitItem() as HomeUiState.Content
            while (content.favorites.isEmpty()) content = awaitItem() as HomeUiState.Content

            viewModel.selectTab(HomeTab.FAVORITES)
            content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.FAVORITES)
            assertThat(content.favorites).containsExactly(movie)
        }
    }

    @Test
    fun `episode progress resolves to its show in continue watching`() = runTest(testDispatcher) {
        val show = showSummary(2, "Watched Show")
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(listOf(show)),
            listProgress = {
                Result.success(listOf(progress(Media.MediaId.Episode(EpisodeId(21)), fraction = 0.5)))
            },
            resolveShowForEpisode = { episodeId -> if (episodeId == EpisodeId(21)) ShowId(2) else null },
        )

        viewModel.uiState.test {
            var content = awaitItem() as HomeUiState.Content
            while (content.continueWatching.isEmpty()) content = awaitItem() as HomeUiState.Content
            assertThat(content.continueWatching).containsExactly(show)
        }
    }

    @Test
    fun `episode resolution is deduplicated and cached across refreshes`() = runTest(testDispatcher) {
        val show = showSummary(2, "Watched Show")
        val resolutions = mutableMapOf<EpisodeId, Int>()
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(listOf(show)),
            listProgress = {
                Result.success(
                    listOf(
                        progress(Media.MediaId.Episode(EpisodeId(21)), fraction = 0.5),
                        progress(Media.MediaId.Episode(EpisodeId(21)), fraction = 0.6),
                    ),
                )
            },
            resolveShowForEpisode = { episodeId ->
                resolutions[episodeId] = (resolutions[episodeId] ?: 0) + 1
                ShowId(2)
            },
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value as HomeUiState.Content
        assertThat(content.continueWatching).containsExactly(show)
        assertThat(resolutions[EpisodeId(21)]).isEqualTo(1)

        viewModel.selectTab(HomeTab.FAVORITES)
        testDispatcher.scheduler.advanceUntilIdle()

        assertThat(resolutions[EpisodeId(21)]).isEqualTo(1)
    }

    @Test
    fun `multiple episodes of one show collapse to a single continue watching entry`() = runTest(testDispatcher) {
        val show = showSummary(2, "Watched Show")
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(listOf(show)),
            listProgress = {
                Result.success(
                    listOf(
                        progress(Media.MediaId.Episode(EpisodeId(21)), fraction = 0.5),
                        progress(Media.MediaId.Episode(EpisodeId(22)), fraction = 0.2),
                    ),
                )
            },
            resolveShowForEpisode = { ShowId(2) },
        )

        viewModel.uiState.test {
            var content = awaitItem() as HomeUiState.Content
            while (content.continueWatching.isEmpty()) content = awaitItem() as HomeUiState.Content
            assertThat(content.continueWatching).containsExactly(show)
        }
    }

    @Test
    fun `unresolved episode progress is ignored`() = runTest(testDispatcher) {
        val viewModel = HomeViewModel(
            catalogItems = catalogItemsFor(emptyList()),
            listProgress = {
                Result.success(listOf(progress(Media.MediaId.Episode(EpisodeId(21)), fraction = 0.5)))
            },
            resolveShowForEpisode = { null },
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value as HomeUiState.Content
        assertThat(content.continueWatching).isEmpty()
    }

    private fun libraryItem(mediaId: Media.MediaId, collection: LibraryCollection) = LibraryItem(
        mediaId = mediaId,
        collection = collection,
        addedAtEpochSeconds = Instant.fromEpochSeconds(1),
        sortOrder = 0,
    )

    private fun showSummary(id: Int, title: String) = ShowSummary(
        id = Media.MediaId.Show(ShowId(id)),
        title = title,
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
    )

    private fun completedDownload(mediaId: Media.MediaId) = DownloadState.Completed(
        localId = LocalMediaIdentifier("local-1"),
        mediaId = mediaId,
        quality = QualityDescriptor(
            resolution = Resolution(1920, 1080),
            label = "1080p",
            bitrate = 5_000,
            mimeType = "video/mp4",
        ),
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(1),
        sizeBytes = null,
        videoPath = OfflineRelativePath("local-1.mp4"),
    )

    private fun progress(mediaId: Media.MediaId, fraction: Double) = PlaybackProgress(
        mediaId = mediaId,
        positionSeconds = (fraction * 1_000).toLong(),
        durationSeconds = 1_000,
        lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1),
        isWatched = false,
    )
}
