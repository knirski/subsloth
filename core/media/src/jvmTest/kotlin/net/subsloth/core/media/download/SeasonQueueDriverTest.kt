package net.subsloth.core.media.download

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.domain.port.SubtitleEnqueueOutcome
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.database.dao.SeasonQueueDao
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

private val queueId = QueueId("driver-queue")
private val showId = ShowId(42)

private val sampleQuality = QualityDescriptor(
    resolution = Resolution.HD_720,
    label = "720p",
    bitrate = null,
    mimeType = null,
)

private class DriverFakeQueueDao : SeasonQueueDao {
    val queues = mutableListOf<SeasonQueueEntity>()
    val items = mutableListOf<QueueItemEntity>()

    override fun getAllQueues() = MutableStateFlow(queues.toList())

    override suspend fun getQueue(queueId: String) = queues.find { it.id == queueId }

    override suspend fun getItemsForQueue(queueId: String) = items.filter { it.queueId == queueId }

    override suspend fun upsertQueue(entity: SeasonQueueEntity) {
        queues.removeAll { it.id == entity.id }
        queues += entity
    }

    override suspend fun upsertItem(entity: QueueItemEntity) {
        items.removeAll { it.queueId == entity.queueId && it.episodeId == entity.episodeId }
        items += entity
    }

    override suspend fun deleteQueue(queueId: String) {
        queues.removeAll { it.id == queueId }
        items.removeAll { it.queueId == queueId }
    }

    override suspend fun deleteItemsForQueue(queueId: String) {
        items.removeAll { it.queueId == queueId }
    }

    override suspend fun deleteCompletedQueuesOlderThan(beforeEpochSeconds: Long) = Unit
}

private class DriverFakeDownloadsPort(private val stateForMedia: (Media.MediaId) -> DownloadState?) : DownloadsPort {
    val enqueued = mutableListOf<Media.MediaId>()

    override suspend fun listDownloads(): Result<ImmutableList<DownloadState>> =
        Result.success(enqueued.mapNotNull(stateForMedia).toImmutableList())

    override suspend fun listOfflineAssets(): Result<ImmutableList<OfflineAsset>> = Result.success(persistentListOf())

    override suspend fun enqueue(
        mediaId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long?,
        transferPreference: TransferPreference,
    ): Result<EnqueueOutcome> {
        enqueued += mediaId
        return Result.success(EnqueueOutcome.Queued)
    }

    override suspend fun enqueueSubtitle(
        localId: LocalMediaIdentifier,
        language: LanguageCode,
    ): Result<SubtitleEnqueueOutcome> = Result.success(SubtitleEnqueueOutcome.Queued)

    override suspend fun pause(localId: LocalMediaIdentifier) = applied()

    override suspend fun resume(localId: LocalMediaIdentifier) = applied()

    override suspend fun cancel(localId: LocalMediaIdentifier) = applied()

    override suspend fun remove(localId: LocalMediaIdentifier) = applied()

    private fun applied() = Result.success(DownloadCommandOutcome.Applied)
}

class SeasonQueueDriverTest {
    @Test
    fun `drive completes every item and the queue`() = runTest {
        val dao = populatedDao()
        val port = DriverFakeDownloadsPort { mediaId -> completed(mediaId) }
        val controller = SeasonQueueController(port, dao, Clock.System)

        SeasonQueueDriver(controller, port).drive(queueId)

        assertThat(port.enqueued.size).isEqualTo(2)
        assertThat(dao.items.map { it.status }).containsExactly("completed", "completed")
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("completed")
    }

    @Test
    fun `drive pauses the queue when a download pauses`() = runTest {
        val dao = populatedDao()
        val port = DriverFakeDownloadsPort { mediaId -> paused(mediaId) }
        val controller = SeasonQueueController(port, dao, Clock.System)

        SeasonQueueDriver(controller, port).drive(queueId)

        assertThat(port.enqueued.size).isEqualTo(1)
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("paused")
    }

    @Test
    fun `drive fails the queue when a download fails`() = runTest {
        val dao = populatedDao()
        val port = DriverFakeDownloadsPort { mediaId -> failed(mediaId) }
        val controller = SeasonQueueController(port, dao, Clock.System)

        SeasonQueueDriver(controller, port).drive(queueId)

        assertThat(port.enqueued.size).isEqualTo(1)
        assertThat(dao.getItemsForQueue(queueId.value).first { it.episodeId == "1" }.status).isEqualTo("failed")
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("failed")
    }

    @Test
    fun `drive does not execute an unconfirmed queue`() = runTest {
        val dao = populatedDao(status = "pending_confirmation")
        val port = DriverFakeDownloadsPort { mediaId -> completed(mediaId) }
        val controller = SeasonQueueController(port, dao, Clock.System)

        SeasonQueueDriver(controller, port).drive(queueId)

        assertThat(port.enqueued).isEmpty()
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("pending_confirmation")
    }

    private fun populatedDao(status: String = "queued"): DriverFakeQueueDao = DriverFakeQueueDao().apply {
        queues += SeasonQueueEntity(
            id = queueId.value,
            showId = showId.value.toString(),
            seasonNumber = 1,
            status = status,
            createdAtEpochSeconds = 0,
        )
        items += queueItem(episodeId = "1")
        items += queueItem(episodeId = "2")
    }

    private fun queueItem(episodeId: String) = QueueItemEntity(
        queueId = queueId.value,
        episodeId = episodeId,
        episodeTitle = "E$episodeId",
        qualityLabel = "720p",
        subtitleLanguages = null,
        sizeBytes = null,
        status = "pending",
    )

    private fun completed(mediaId: Media.MediaId) = DownloadState.Completed(
        localId = LocalMediaIdentifier(mediaId.toString()),
        mediaId = mediaId,
        quality = sampleQuality,
        downloadedAtEpochSeconds = Instant.fromEpochSeconds(0),
        sizeBytes = 1024,
        videoPath = OfflineRelativePath.safe("videos/test.mp4"),
    )

    private fun paused(mediaId: Media.MediaId) = DownloadState.Paused(
        localId = LocalMediaIdentifier(mediaId.toString()),
        mediaId = mediaId,
        quality = sampleQuality,
        reason = DownloadFailureReason.NeedsWifi,
    )

    private fun failed(mediaId: Media.MediaId) = DownloadState.Failed(
        localId = LocalMediaIdentifier(mediaId.toString()),
        mediaId = mediaId,
        quality = sampleQuality,
        reason = DownloadFailureReason.DownloadFailed,
    )
}
