package net.subsloth.details

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.error.LibraryError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Season
import net.subsloth.core.model.media.ShowDetails
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

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

    private val episode =
        Episode(
            id = EpisodeId(10),
            showId = ShowId(1),
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Pilot",
            plot = null,
            durationSeconds = 3600,
            availability = Availability.Available,
            imdbId = null,
            qualities = persistentListOf(),
            subtitles = persistentListOf(),
            airDateEpochSeconds = null,
            premiereDateEpochSeconds = null,
        )

    private val showWithEpisodes = showDetails.copy(
        seasons = persistentListOf(
            Season(seasonNumber = 1, title = "Season 1", plot = null, episodes = persistentListOf(episode)),
        ),
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

    @Test
    fun `shows resume fraction from latest episode progress`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listProgress = {
                Result.success(
                    listOf(
                        progress(mediaId, positionSeconds = 300, durationSeconds = 3600, updatedAt = 5),
                        progress(Media.MediaId.Episode(EpisodeId(10)), 600, 3600, updatedAt = 10),
                    ),
                )
            },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.progressFraction).isEqualTo(600.0 / 3600.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `shows resume fraction from show progress when newer`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listProgress = {
                Result.success(
                    listOf(
                        progress(mediaId, positionSeconds = 1200, durationSeconds = 3600, updatedAt = 20),
                        progress(Media.MediaId.Episode(EpisodeId(10)), 600, 3600, updatedAt = 10),
                    ),
                )
            },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.progressFraction).isEqualTo(1200.0 / 3600.0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores progress of episodes from other shows`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listProgress = {
                Result.success(
                    listOf(progress(Media.MediaId.Episode(EpisodeId(99)), 600, 3600, updatedAt = 10)),
                )
            },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.progressFraction).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores non-resumable episode progress`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listProgress = {
                Result.success(
                    listOf(progress(Media.MediaId.Episode(EpisodeId(10)), 20, 3600, updatedAt = 10)),
                )
            },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.progressFraction).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `favorite flag reflects favorites membership`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            listLibrary = { Outcome.Success(listOf(libraryItem(LibraryCollection.FAVORITES))) },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.isFavorite).isTrue()
            assertThat(content.isWatchLater).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `watch later flag reflects watch later membership`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            listLibrary = { Outcome.Success(listOf(libraryItem(LibraryCollection.WATCH_LATER))) },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.isWatchLater).isTrue()
            assertThat(content.isFavorite).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `library failure keeps content with flags false`() = runTest(testDispatcher) {
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            listLibrary = { Outcome.Failure(LibraryError.NotSupported) },
        )
        vm.uiState.test {
            val content = awaitItem() as ShowDetailUiState.Content
            assertThat(content.isFavorite).isFalse()
            assertThat(content.isWatchLater).isFalse()
            assertThat(content.details.title).isEqualTo("Test Show")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggleFavorite adds favorite and updates state`() = runTest(testDispatcher) {
        val library = mutableListOf<LibraryItem>()
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            addToLibrary = { item ->
                library += item
                Outcome.Success(Unit)
            },
            listLibrary = { Outcome.Success(library.toList()) },
        )

        vm.toggleFavorite()

        assertThat(library.map { it.collection }).containsExactly(LibraryCollection.FAVORITES)
        val content = vm.uiState.value as ShowDetailUiState.Content
        assertThat(content.isFavorite).isTrue()
    }

    @Test
    fun `toggleFavorite removes favorite and updates state`() = runTest(testDispatcher) {
        val library = mutableListOf(libraryItem(LibraryCollection.FAVORITES))
        val removed = mutableListOf<Media.MediaId>()
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            listLibrary = { Outcome.Success(library.toList()) },
            removeFromLibrary = { id ->
                removed += id
                library.removeAll { it.mediaId == id }
                Outcome.Success(Unit)
            },
        )

        vm.toggleFavorite()

        assertThat(removed).containsExactly(mediaId)
        val content = vm.uiState.value as ShowDetailUiState.Content
        assertThat(content.isFavorite).isFalse()
    }

    @Test
    fun `toggleWatchLater adds watch later and updates state`() = runTest(testDispatcher) {
        val library = mutableListOf<LibraryItem>()
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showDetails) },
            addToLibrary = { item ->
                library += item
                Outcome.Success(Unit)
            },
            listLibrary = { Outcome.Success(library.toList()) },
        )

        vm.toggleWatchLater()

        assertThat(library.map { it.collection }).containsExactly(LibraryCollection.WATCH_LATER)
        val content = vm.uiState.value as ShowDetailUiState.Content
        assertThat(content.isWatchLater).isTrue()
    }

    @Test
    fun `downloadSeason starts the selected season download`() = runTest(testDispatcher) {
        val started = mutableListOf<Pair<Int, Int>>()
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            startSeasonDownload = { season, episodes -> started += season to episodes.size },
        )

        vm.downloadSeason()

        assertThat(started).containsExactly(1 to 1)
    }

    @Test
    fun `downloadSeason is a no-op while a queue is active`() = runTest(testDispatcher) {
        var called = false
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listSeasonQueues = { Result.success(listOf(seasonQueue(activeExecution()))) },
            startSeasonDownload = { _, _ -> called = true },
        )

        vm.downloadSeason()

        assertThat(called).isFalse()
    }

    @Test
    fun `downloadSeason re-queues a completed queue`() = runTest(testDispatcher) {
        var called = false
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listSeasonQueues = { Result.success(listOf(seasonQueue(SeasonQueueExecution.Completed))) },
            startSeasonDownload = { _, _ -> called = true },
        )

        vm.downloadSeason()

        assertThat(called).isTrue()
    }

    @Test
    fun `downloadSeason retries a failed queue`() = runTest(testDispatcher) {
        var called = false
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listSeasonQueues = {
                Result.success(listOf(seasonQueue(SeasonQueueExecution.Failed(DownloadFailureReason.DownloadFailed))))
            },
            startSeasonDownload = { _, _ -> called = true },
        )

        vm.downloadSeason()

        assertThat(called).isTrue()
    }

    @Test
    fun `season queue state refreshes while the queue is active`() = runTest(testDispatcher) {
        var response: List<SeasonDownloadQueue> = emptyList()
        val vm = ShowDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(showWithEpisodes) },
            listSeasonQueues = { Result.success(response) },
            startSeasonDownload = { _, _ ->
                response = listOf(seasonQueue(activeExecution()))
            },
        )

        vm.downloadSeason()
        assertThat(currentSeasonDownloadState(vm)).isEqualTo(SeasonDownloadState.Downloading)

        response = listOf(seasonQueue(SeasonQueueExecution.Completed))
        advanceTimeBy(2_500)
        runCurrent()

        assertThat(currentSeasonDownloadState(vm)).isEqualTo(SeasonDownloadState.Completed)
    }

    private fun activeExecution() = SeasonQueueExecution.Running(Media.MediaId.Episode(EpisodeId(10)))

    private fun seasonQueue(execution: SeasonQueueExecution) = SeasonDownloadQueue(
        queueId = QueueId("1-1"),
        showId = ShowId(1),
        seasonNumber = 1,
        items = persistentListOf(),
        execution = execution,
        transferPreference = TransferPreference.WifiOnly,
    )

    private fun currentSeasonDownloadState(vm: ShowDetailViewModel): SeasonDownloadState {
        val content = vm.uiState.value as ShowDetailUiState.Content
        return seasonDownloadState(content.seasonQueues, content.selectedSeason)
    }

    private fun libraryItem(collection: LibraryCollection): LibraryItem = LibraryItem(
        mediaId = mediaId,
        collection = collection,
        addedAtEpochSeconds = Instant.fromEpochSeconds(0),
        sortOrder = 1,
    )

    private fun progress(
        mediaId: Media.MediaId,
        positionSeconds: Long,
        durationSeconds: Long,
        updatedAt: Long,
    ): PlaybackProgress = PlaybackProgress(
        mediaId = mediaId,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        lastUpdatedEpochSeconds = Instant.fromEpochSeconds(updatedAt),
        isWatched = false,
    )
}
