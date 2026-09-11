package net.subsloth.core.media.download

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.domain.port.SubtitleEnqueueOutcome
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadConfirmation
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.download.SizeEstimate
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.database.dao.SeasonQueueDao
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock

class SeasonQueueControllerTest {
    private val queueId = QueueId("test-queue-1")
    private val showId = ShowId(42)

    @Test
    fun `listQueues returns all queues with items`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        val queues = controller.listQueues()
        assertThat(queues.size).isEqualTo(1)
        assertThat(queues.first().items.size).isEqualTo(1)
    }

    @Test
    fun `listQueues returns empty list when no queues exist`() = runTest {
        val controller = SeasonQueueController(fakeDownloadsPort(), InMemorySeasonQueueDao(), Clock.System)
        assertThat(controller.listQueues()).isEmpty()
    }

    @Test
    fun `confirmQueue sets status to queued`() = runTest {
        val dao = InMemorySeasonQueueDao()
        dao.upsertQueue(
            SeasonQueueEntity(
                id = queueId.value,
                showId = showId.value.toString(),
                seasonNumber = 1,
                status = "pending_confirmation",
                createdAtEpochSeconds = Clock.System.now().epochSeconds,
            ),
        )
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        controller.confirmQueue(queueId)
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("queued")
    }

    @Test
    fun `confirmQueue does nothing for nonexistent queue`() = runTest {
        val controller = SeasonQueueController(fakeDownloadsPort(), InMemorySeasonQueueDao(), Clock.System)
        controller.confirmQueue(queueId)
    }

    @Test
    fun `cancelQueue removes queue and items`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        controller.cancelQueue(queueId)
        assertThat(dao.getQueue(queueId.value)).isNull()
        assertThat(dao.getItemsForQueue(queueId.value)).isEmpty()
    }

    @Test
    fun `cancelQueue does nothing for nonexistent queue`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        controller.cancelQueue(QueueId("nonexistent"))
        assertThat(dao.getQueue(queueId.value)).isNotNull()
    }

    @Test
    fun `executeNext processes pending item`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        val result = controller.executeNext(queueId)
        val items = dao.getItemsForQueue(queueId.value)
        assertThat(items.first().status).isEqualTo("downloading")
        assertThat(result is SeasonQueueExecution.Running).isTrue()
    }

    @Test
    fun `executeNext returns completed when no items`() = runTest {
        val dao = createPopulatedDao()
        dao.clearItems()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        assertThat(controller.executeNext(queueId)).isEqualTo(SeasonQueueExecution.Completed)
    }

    @Test
    fun `executeNext skips downloading items and returns completed`() = runTest {
        val dao = createPopulatedDao()
        dao.clearItems()
        dao.upsertItem(
            QueueItemEntity(
                queueId = queueId.value,
                episodeId = "2",
                episodeTitle = "E2",
                qualityLabel = null,
                subtitleLanguages = null,
                sizeBytes = null,
                status = "downloading",
            ),
        )
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        assertThat(controller.executeNext(queueId)).isEqualTo(SeasonQueueExecution.Completed)
    }

    @Test
    fun `executeNext enqueues subtitle when available`() = runTest {
        val dao = createPopulatedDao()
        dao.clearItems()
        dao.upsertItem(
            QueueItemEntity(
                queueId = queueId.value,
                episodeId = "1",
                episodeTitle = "E1",
                qualityLabel = null,
                subtitleLanguages = "en",
                sizeBytes = null,
                status = "pending",
            ),
        )
        var subtitleEnqueued = false
        val controller = SeasonQueueController(
            fakeDownloadsPort(onSubtitleEnqueue = { subtitleEnqueued = true }),
            dao,
            Clock.System,
        )
        controller.executeNext(queueId)
        assertThat(subtitleEnqueued).isTrue()
    }

    @Test
    fun `pause and resume queue`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        controller.pauseQueue(queueId)
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("paused")
        controller.resumeQueue(queueId)
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("queued")
    }

    @Test
    fun `listQueues prunes old completed queues`() = runTest {
        val dao = InMemorySeasonQueueDao()
        dao.upsertQueue(
            SeasonQueueEntity(
                id = queueId.value,
                showId = showId.value.toString(),
                seasonNumber = 1,
                status = "completed",
                createdAtEpochSeconds = 0,
            ),
        )
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)

        assertThat(controller.listQueues()).isEmpty()
        assertThat(dao.getQueue(queueId.value)).isNull()
    }

    @Test
    fun `listQueues keeps a recent completed queue`() = runTest {
        val dao = InMemorySeasonQueueDao()
        dao.upsertQueue(
            SeasonQueueEntity(
                id = queueId.value,
                showId = showId.value.toString(),
                seasonNumber = 1,
                status = "completed",
                createdAtEpochSeconds = Clock.System.now().epochSeconds,
            ),
        )
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)

        assertThat(controller.listQueues().size).isEqualTo(1)
    }

    @Test
    fun `pauseQueue does nothing for nonexistent queue`() = runTest {
        val controller = SeasonQueueController(fakeDownloadsPort(), InMemorySeasonQueueDao(), Clock.System)
        controller.pauseQueue(queueId)
    }

    @Test
    fun `createQueue replaces previous items for the same queue id`() = runTest {
        val dao = InMemorySeasonQueueDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)
        val episode = episode()

        repeat(2) {
            controller.createQueue(
                queueId = queueId,
                showId = showId,
                seasonNumber = 1,
                episodes = persistentListOf(episode),
                qualityPref = Resolution.HD_720,
                subtitlePref = LanguageCode("en"),
                transferPreference = TransferPreference.WifiOnly,
                confirmation = confirmation(),
            )
        }

        assertThat(dao.getItemsForQueue(queueId.value).size).isEqualTo(1)
    }

    @Test
    fun `markItemCompleted completes the queue when every item is done`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)

        controller.markItemCompleted(queueId, Media.MediaId.Episode(EpisodeId(1)))

        assertThat(dao.getItemsForQueue(queueId.value).single().status).isEqualTo("completed")
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("completed")
    }

    @Test
    fun `markItemFailed fails the item and the queue`() = runTest {
        val dao = createPopulatedDao()
        val controller = SeasonQueueController(fakeDownloadsPort(), dao, Clock.System)

        controller.markItemFailed(queueId, Media.MediaId.Episode(EpisodeId(1)), DownloadFailureReason.DownloadFailed)

        assertThat(dao.getItemsForQueue(queueId.value).single().status).isEqualTo("failed")
        assertThat(dao.getQueue(queueId.value)?.status).isEqualTo("failed")
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun episode(id: Int = 1): Episode = Episode(
        id = EpisodeId(id),
        showId = showId,
        seasonNumber = 1,
        episodeNumber = id,
        title = "E$id",
        plot = null,
        durationSeconds = 1800,
        availability = Availability.Available,
        imdbId = null,
        qualities = persistentListOf(),
        subtitles = persistentListOf(),
        airDateEpochSeconds = null,
        premiereDateEpochSeconds = null,
    )

    private fun confirmation(count: Int = 1): SeasonDownloadConfirmation = SeasonDownloadConfirmation(
        episodeCount = count,
        alreadyAvailableCount = 0,
        fallbackQualityCount = 0,
        fallbackSubtitleToEnglishCount = 0,
        noSubtitleCount = 0,
        unavailableCount = 0,
        sizeEstimate = SizeEstimate.Unknown,
        transferPreference = TransferPreference.WifiOnly,
    )

    private suspend fun createPopulatedDao(): InMemorySeasonQueueDao {
        val dao = InMemorySeasonQueueDao()
        dao.upsertQueue(
            SeasonQueueEntity(
                id = queueId.value,
                showId = showId.value.toString(),
                seasonNumber = 1,
                status = "queued",
                createdAtEpochSeconds = Clock.System.now().epochSeconds,
            ),
        )
        dao.upsertItem(
            QueueItemEntity(
                queueId = queueId.value,
                episodeId = "1",
                episodeTitle = "E1",
                qualityLabel = null,
                subtitleLanguages = null,
                sizeBytes = null,
                status = "pending",
            ),
        )
        return dao
    }

    private class InMemorySeasonQueueDao : SeasonQueueDao {
        private val queues = mutableListOf<SeasonQueueEntity>()
        private val items = mutableListOf<QueueItemEntity>()

        fun clearItems() {
            items.clear()
        }

        override fun getAllQueues() = kotlinx.coroutines.flow.MutableStateFlow(queues.toList())
        override suspend fun getQueue(queueId: String) = queues.find { it.id == queueId }
        override suspend fun getItemsForQueue(queueId: String) = items.filter { it.queueId == queueId }
        override suspend fun upsertQueue(entity: SeasonQueueEntity) {
            queues.removeAll { it.id == entity.id }
            queues.add(entity)
        }
        override suspend fun upsertItem(entity: QueueItemEntity) {
            items.removeAll { it.queueId == entity.queueId && it.episodeId == entity.episodeId }
            items.add(entity)
        }
        override suspend fun deleteQueue(queueId: String) {
            queues.removeAll { it.id == queueId }
            items.removeAll { it.queueId == queueId }
        }
        override suspend fun deleteItemsForQueue(queueId: String) {
            items.removeAll { it.queueId == queueId }
        }
        override suspend fun deleteCompletedQueuesOlderThan(beforeEpochSeconds: Long) {
            val toRemove = queues.filter { it.status == "completed" && it.createdAtEpochSeconds < beforeEpochSeconds }
            toRemove.forEach { q ->
                queues.remove(q)
                items.removeAll { it.queueId == q.id }
            }
        }
    }

    private fun fakeDownloadsPort(onSubtitleEnqueue: () -> Unit = {}): DownloadsPort {
        val enqueued = mutableListOf<Media.MediaId>()
        return object : DownloadsPort {
            override suspend fun listDownloads(): Result<kotlinx.collections.immutable.ImmutableList<DownloadState>> =
                Result.success(
                    enqueued.map { mediaId ->
                        DownloadState.Queued(
                            localId = LocalMediaIdentifier(mediaId.toString()),
                            mediaId = mediaId,
                            quality = QualityDescriptor(
                                resolution = Resolution.HD_720,
                                label = "720p",
                                bitrate = null,
                                mimeType = null,
                            ),
                        )
                    }.toImmutableList(),
                )

            override suspend fun listOfflineAssets() = Result.success(persistentListOf<OfflineAsset>())

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
            ): Result<SubtitleEnqueueOutcome> {
                onSubtitleEnqueue()
                return Result.success(SubtitleEnqueueOutcome.Queued)
            }

            override suspend fun pause(localId: LocalMediaIdentifier) =
                Result.success(net.subsloth.core.domain.port.DownloadCommandOutcome.Applied)

            override suspend fun resume(localId: LocalMediaIdentifier) =
                Result.success(net.subsloth.core.domain.port.DownloadCommandOutcome.Applied)

            override suspend fun cancel(localId: LocalMediaIdentifier) =
                Result.success(net.subsloth.core.domain.port.DownloadCommandOutcome.Applied)

            override suspend fun remove(localId: LocalMediaIdentifier) =
                Result.success(net.subsloth.core.domain.port.DownloadCommandOutcome.Applied)
        }
    }
}
