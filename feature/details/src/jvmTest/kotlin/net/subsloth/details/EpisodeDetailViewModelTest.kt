package net.subsloth.details

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.error.DecodeError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.EpisodeDetails
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EpisodeDetailViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val mediaId = Media.MediaId.Episode(EpisodeId(10))

    private val sampleQuality = Quality(
        info = QualityDescriptor(
            resolution = Resolution(1920, 1080),
            label = "1080p",
            bitrate = null,
            mimeType = null,
        ),
        url = null,
        downloadUrl = null,
    )

    private val episodeDetails = EpisodeDetails(
        id = mediaId,
        title = "Pilot",
        plot = "First episode",
        description = null,
        availability = Availability.Available,
        qualities = persistentListOf(sampleQuality),
        subtitles = persistentListOf(),
        showId = ShowId(1),
        seasonNumber = 1,
        episodeNumber = 1,
    )

    @Test
    fun `loads episode details and watched state`() = runTest(testDispatcher) {
        val viewModel = EpisodeDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(episodeDetails) },
            isWatched = { true },
        )

        val content = viewModel.uiState.value as EpisodeDetailUiState.Content
        assertThat(content.details.title).isEqualTo("Pilot")
        assertThat(content.isWatched).isTrue()
    }

    @Test
    fun `details failure shows error state`() = runTest(testDispatcher) {
        val viewModel = EpisodeDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Failure(DecodeError.SerializationFailed) },
        )

        assertThat(viewModel.uiState.value).isInstanceOf(EpisodeDetailUiState.Error::class.java)
    }

    @Test
    fun `downloaded flag reflects completed download`() = runTest(testDispatcher) {
        val viewModel = EpisodeDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(episodeDetails) },
            listDownloads = { Result.success(listOf(completedDownload())) },
        )

        val content = viewModel.uiState.value as EpisodeDetailUiState.Content
        assertThat(content.isDownloaded).isTrue()
    }

    @Test
    fun `toggleDownload enqueues default capped quality`() = runTest(testDispatcher) {
        val requested = mutableListOf<Resolution>()
        val viewModel = EpisodeDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(episodeDetails) },
            enqueueDownload = { _, resolution ->
                requested += resolution
                Result.success(EnqueueOutcome.Queued)
            },
        )

        viewModel.toggleDownload()

        assertThat(requested).containsExactly(Resolution(1920, 1080))
    }

    @Test
    fun `toggleDownload enqueues fallback resolution when qualities are empty`() = runTest(testDispatcher) {
        val requested = mutableListOf<Resolution>()
        val viewModel = EpisodeDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(episodeDetails.copy(qualities = persistentListOf())) },
            enqueueDownload = { _, resolution ->
                requested += resolution
                Result.success(EnqueueOutcome.Queued)
            },
        )

        viewModel.toggleDownload()

        assertThat(requested).containsExactly(Resolution.HD_720)
    }

    @Test
    fun `toggleDownload removes completed download`() = runTest(testDispatcher) {
        val removed = mutableListOf<LocalMediaIdentifier>()
        val download = completedDownload()
        val viewModel = EpisodeDetailViewModel(
            mediaId = mediaId,
            getDetails = { Outcome.Success(episodeDetails) },
            listDownloads = { Result.success(listOf(download)) },
            removeDownload = { localId ->
                removed += localId
                Result.success(DownloadCommandOutcome.Applied)
            },
        )

        viewModel.toggleDownload()

        assertThat(removed).containsExactly(download.localId)
    }

    private fun completedDownload(): DownloadState.Completed = DownloadState.Completed(
        localId = LocalMediaIdentifier("episode-10"),
        mediaId = mediaId,
        quality = sampleQuality.info,
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(0),
        sizeBytes = 1024,
        videoPath = OfflineRelativePath.safe("videos/episode-10.mp4"),
    )
}
