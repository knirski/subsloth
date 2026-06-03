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
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonDownloadQueueItem
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.download.SeasonQueueItemExecution
import net.subsloth.core.model.download.SubtitleSelection
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val sampleQuality = QualityDescriptor(
        resolution = Resolution(1920, 1080),
        label = "1080p",
        bitrate = null,
        mimeType = null,
    )
    private val movieId = Media.MediaId.Movie(MovieId(1))
    private val episodeId = Media.MediaId.Episode(EpisodeId(1))
    private val localId = LocalMediaIdentifier("local-1")
    private val samplePath = OfflineRelativePath.safe("videos/test.mp4")

    private val activeDownload = DownloadState.Active(
        localId = localId,
        mediaId = movieId,
        quality = sampleQuality,
        progressPercent = 50,
    )
    private val queuedDownload = DownloadState.Queued(
        localId = LocalMediaIdentifier("local-2"),
        mediaId = movieId,
        quality = sampleQuality,
    )
    private val pausedDownload = DownloadState.Paused(
        localId = LocalMediaIdentifier("local-3"),
        mediaId = movieId,
        quality = sampleQuality,
        reason = DownloadFailureReason.NeedsWifi,
    )
    private val failedDownload = DownloadState.Failed(
        localId = LocalMediaIdentifier("local-4"),
        mediaId = movieId,
        quality = sampleQuality,
        reason = DownloadFailureReason.DownloadFailed,
    )
    private val completedDownload = DownloadState.Completed(
        localId = LocalMediaIdentifier("local-5"),
        mediaId = movieId,
        quality = sampleQuality,
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(1000),
        sizeBytes = 1024L * 1024L * 500L,
        videoPath = samplePath,
    )
    private val unavailableDownload = DownloadState.Unavailable(
        localId = LocalMediaIdentifier("local-6"),
        mediaId = movieId,
        quality = sampleQuality,
        reason = DownloadFailureReason.MissingLocalFile,
    )

    @Test
    fun `loads downloads and groups by state`() = runTest(testDispatcher) {
        val downloads =
            persistentListOf(
                activeDownload,
                queuedDownload,
                pausedDownload,
                failedDownload,
                completedDownload,
                unavailableDownload,
            )
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(downloads) },
            listSeasonQueues = { Result.success(persistentListOf()) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as DownloadsUiState.Content
            assertThat(content.active).isNotEmpty()
            assertThat(content.queuedOrPaused).isNotEmpty()
            assertThat(content.failedOrUnavailable).isNotEmpty()
            assertThat(content.completed).isNotEmpty()
        }
    }

    @Test
    fun `loads and emits content after init`() = runTest(testDispatcher) {
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf()) },
            listSeasonQueues = { Result.success(persistentListOf()) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as DownloadsUiState.Content
            assertThat(content).isNotNull()
        }
    }

    @Test
    fun `shows empty state when no downloads`() = runTest(testDispatcher) {
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf()) },
            listSeasonQueues = { Result.success(persistentListOf()) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as DownloadsUiState.Content
            assertThat(content.active).isEmpty()
            assertThat(content.queuedOrPaused).isEmpty()
            assertThat(content.failedOrUnavailable).isEmpty()
            assertThat(content.completed).isEmpty()
        }
    }

    @Test
    fun `pause action calls pause on port`() = runTest(testDispatcher) {
        var paused = false
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(activeDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            pauseDownload = {
                paused = true
                DownloadCommandOutcome.Applied
            },
        )
        viewModel.pause(localId.value)
        assertThat(paused).isTrue()
    }

    @Test
    fun `resume action calls resume on port`() = runTest(testDispatcher) {
        var resumed = false
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(pausedDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            resumeDownload = {
                resumed = true
                DownloadCommandOutcome.Applied
            },
        )
        viewModel.resume(localId.value)
        assertThat(resumed).isTrue()
    }

    @Test
    fun `cancel action calls cancel on port`() = runTest(testDispatcher) {
        var cancelled = false
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(activeDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            cancelDownload = {
                cancelled = true
                DownloadCommandOutcome.Applied
            },
        )
        viewModel.cancel(localId.value)
        assertThat(cancelled).isTrue()
    }

    @Test
    fun `retry action re-enqueues failed download`() = runTest(testDispatcher) {
        var retried = false
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(failedDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            retryDownload = {
                retried = true
                EnqueueOutcome.Queued
            },
        )
        viewModel.retry(localId.value)
        assertThat(retried).isTrue()
    }

    @Test
    fun `remove action calls remove on port`() = runTest(testDispatcher) {
        var removed = false
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(completedDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            removeDownload = {
                removed = true
                DownloadCommandOutcome.Applied
            },
        )
        viewModel.remove(localId.value)
        assertThat(removed).isTrue()
    }

    @Test
    fun `includes season queues in download groups`() = runTest(testDispatcher) {
        val seasonQueue = SeasonDownloadQueue(
            queueId = QueueId("sq-1"),
            showId = ShowId(1),
            seasonNumber = 1,
            items = persistentListOf(
                SeasonDownloadQueueItem(
                    mediaId = episodeId,
                    selectedQuality = Resolution(1920, 1080),
                    preferredSubtitleLanguage = LanguageCode("en"),
                    subtitleSelection = SubtitleSelection.None,
                    execution = SeasonQueueItemExecution.Completed,
                ),
            ),
            execution = SeasonQueueExecution.Completed,
            transferPreference = TransferPreference.WifiOnly,
        )
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf()) },
            listSeasonQueues = { Result.success(persistentListOf(seasonQueue)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as DownloadsUiState.Content
            assertThat(content.seasonQueues).isNotEmpty()
            assertThat(content.seasonQueues[0].queueId.value).isEqualTo("sq-1")
        }
    }

    @Test
    fun `season queue shows per-episode status`() = runTest(testDispatcher) {
        val seasonQueue = SeasonDownloadQueue(
            queueId = QueueId("sq-1"),
            showId = ShowId(1),
            seasonNumber = 1,
            items = persistentListOf(
                SeasonDownloadQueueItem(
                    mediaId = episodeId,
                    selectedQuality = Resolution(1920, 1080),
                    preferredSubtitleLanguage = LanguageCode("en"),
                    subtitleSelection = SubtitleSelection.None,
                    execution = SeasonQueueItemExecution.Downloading(50),
                ),
            ),
            execution = SeasonQueueExecution.Running(episodeId),
            transferPreference = TransferPreference.WifiOnly,
        )
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf()) },
            listSeasonQueues = { Result.success(persistentListOf(seasonQueue)) },
        )
        viewModel.uiState.test {
            val content = awaitItem() as DownloadsUiState.Content
            val sq = content.seasonQueues.first()
            assertThat(sq.items.first().execution).isEqualTo(SeasonQueueItemExecution.Downloading(50))
        }
    }

    @Test
    fun `delete all downloads calls remove for each completed`() = runTest(testDispatcher) {
        var removedCount = 0
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(completedDownload, activeDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            removeDownload = {
                removedCount++
                DownloadCommandOutcome.Applied
            },
        )
        viewModel.deleteAllCompleted()
        assertThat(removedCount).isEqualTo(1)
    }

    @Test
    fun `delete watched completed removes only completed with watch progress`() = runTest(testDispatcher) {
        var removedId: String? = null
        val viewModel = DownloadsViewModel(
            listDownloads = { Result.success(persistentListOf(completedDownload)) },
            listSeasonQueues = { Result.success(persistentListOf()) },
            listProgress = {
                Result.success(
                    listOf(
                        net.subsloth.core.model.progress.PlaybackProgress(
                            mediaId = movieId,
                            positionSeconds = 1100L,
                            durationSeconds = 1200L,
                            lastUpdatedEpochSeconds = Instant.fromEpochSeconds(1000),
                            isWatched = true,
                        ),
                    ),
                )
            },
            removeDownload = {
                removedId = it
                DownloadCommandOutcome.Applied
            },
        )
        viewModel.deleteWatchedCompleted()
        assertThat(removedId).isEqualTo(completedDownload.localId.value)
    }
}
