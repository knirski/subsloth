package net.subsloth.core.media.download

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import net.subsloth.core.domain.policy.SeasonQueuePolicy
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.QueueId
import net.subsloth.core.model.download.SeasonDownloadConfirmation
import net.subsloth.core.model.download.SeasonDownloadQueue
import net.subsloth.core.model.download.SeasonDownloadQueueItem
import net.subsloth.core.model.download.SeasonQueueExecution
import net.subsloth.core.model.download.SeasonQueueItemExecution
import net.subsloth.core.model.download.SubtitleSelection
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Subtitle
import net.subsloth.database.dao.SeasonQueueDao
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity
import java.util.UUID

class SeasonQueueController(private val downloadsPort: DownloadsPort, private val seasonQueueDao: SeasonQueueDao) {
    suspend fun listQueues(): List<SeasonDownloadQueue> = seasonQueueDao.getAllQueues().first().map { entity ->
        val items = seasonQueueDao.getItemsForQueue(entity.id)
        entity.toDomain(items)
    }

    suspend fun createQueue(
        showId: ShowId,
        seasonNumber: Int,
        episodes: ImmutableList<Episode>,
        qualityPref: Resolution,
        subtitlePref: LanguageCode,
        transferPreference: TransferPreference,
        confirmation: SeasonDownloadConfirmation,
    ): SeasonDownloadQueue {
        val queueId = QueueId(UUID.randomUUID().toString())
        val queueEntity = SeasonQueueEntity(
            id = queueId.value,
            showId = showId.value.toString(),
            seasonNumber = seasonNumber,
            status = "pending_confirmation",
            createdAtEpochSeconds = java.lang.System.currentTimeMillis() / 1000,
        )
        seasonQueueDao.upsertQueue(queueEntity)

        val items = episodes.map { episode ->
            val subtitleSelection = SeasonQueuePolicy.selectInitialSubtitle(
                available = episode.subtitles,
                preferred = subtitlePref,
            )
            SeasonDownloadQueueItem(
                mediaId = Media.MediaId.Episode(episode.id),
                selectedQuality = qualityPref,
                preferredSubtitleLanguage = subtitlePref,
                subtitleSelection = subtitleSelection,
                execution = SeasonQueueItemExecution.Pending,
            )
        }

        val queue = SeasonDownloadQueue(
            queueId = queueId,
            showId = showId,
            seasonNumber = seasonNumber,
            items = persistentListOf(),
            execution = SeasonQueueExecution.PendingConfirmation,
            transferPreference = transferPreference,
        )
        items.forEach { item ->
            seasonQueueDao.upsertItem(
                QueueItemEntity(
                    queueId = queueId.value,
                    episodeId = item.mediaId.toEpisodeIdString(),
                    episodeTitle = "",
                    qualityLabel = qualityPref.label,
                    subtitleLanguages = when (val sel = item.subtitleSelection) {
                        is SubtitleSelection.Preferred -> sel.subtitle.language.value
                        is SubtitleSelection.EnglishFallback -> sel.subtitle.language.value
                        is SubtitleSelection.None -> null
                    },
                    sizeBytes = null,
                    status = "pending",
                ),
            )
        }
        return queue.copy(items = persistentListOf())
    }

    suspend fun confirmQueue(queueId: QueueId) {
        val entity = seasonQueueDao.getQueue(queueId.value) ?: return
        seasonQueueDao.upsertQueue(entity.copy(status = "queued"))
    }

    suspend fun executeNext(queueId: QueueId): SeasonQueueExecution {
        val items = seasonQueueDao.getItemsForQueue(queueId.value)
        val nextPending = items.firstOrNull { it.status == "pending" }
            ?: return SeasonQueueExecution.Completed

        seasonQueueDao.upsertItem(nextPending.copy(status = "downloading"))

        val mediaId = Media.MediaId.Episode(
            net.subsloth.core.model.identifier.EpisodeId(nextPending.episodeId.toIntOrNull() ?: 0),
        )
        val result = downloadsPort.enqueue(
            mediaId = mediaId,
            requested = parseResolution(nextPending.qualityLabel),
            requiredBytes = nextPending.sizeBytes,
            transferPreference = TransferPreference.WifiOnly,
        )

        return result.fold(
            onSuccess = { outcome ->
                when (outcome) {
                    is EnqueueOutcome.Queued -> SeasonQueueExecution.Running(mediaId)

                    is EnqueueOutcome.AlreadyAvailableHigherQuality -> {
                        seasonQueueDao.upsertItem(nextPending.copy(status = "completed"))
                        executeNext(queueId)
                    }
                }
            },
            onFailure = {
                seasonQueueDao.upsertItem(nextPending.copy(status = "failed"))
                SeasonQueueExecution.Failed(DownloadFailureReason.DownloadFailed)
            },
        )
    }

    suspend fun pauseQueue(queueId: QueueId) {
        val entity = seasonQueueDao.getQueue(queueId.value) ?: return
        seasonQueueDao.upsertQueue(entity.copy(status = "paused"))
    }

    suspend fun resumeQueue(queueId: QueueId) {
        val entity = seasonQueueDao.getQueue(queueId.value) ?: return
        seasonQueueDao.upsertQueue(entity.copy(status = "queued"))
    }

    suspend fun cancelQueue(queueId: QueueId) {
        seasonQueueDao.deleteQueue(queueId.value)
    }

    private fun SeasonQueueEntity.toDomain(items: List<QueueItemEntity>): SeasonDownloadQueue {
        val queueId = QueueId(id)
        val domainItems = items.map { item ->
            SeasonDownloadQueueItem(
                mediaId = Media.MediaId.Episode(
                    net.subsloth.core.model.identifier.EpisodeId(item.episodeId.toIntOrNull() ?: 0),
                ),
                selectedQuality = parseResolution(item.qualityLabel),
                preferredSubtitleLanguage = LanguageCode(item.subtitleLanguages ?: "en"),
                subtitleSelection = SubtitleSelection.None,
                execution = when (item.status) {
                    "pending" -> SeasonQueueItemExecution.Pending
                    "downloading" -> SeasonQueueItemExecution.Downloading(0)
                    "completed" -> SeasonQueueItemExecution.Completed
                    "failed" -> SeasonQueueItemExecution.Failed(DownloadFailureReason.DownloadFailed)
                    "cancelled" -> SeasonQueueItemExecution.Cancelled
                    else -> SeasonQueueItemExecution.Pending
                },
            )
        }
        return SeasonDownloadQueue(
            queueId = queueId,
            showId = ShowId(showId.toIntOrNull() ?: 0),
            seasonNumber = seasonNumber,
            items = domainItems.toImmutableList(),
            execution = when (status) {
                "pending_confirmation" -> SeasonQueueExecution.PendingConfirmation

                "queued" -> SeasonQueueExecution.Queued

                "running" -> SeasonQueueExecution.Running(
                    Media.MediaId.Episode(
                        net.subsloth.core.model.identifier.EpisodeId(
                            items.firstOrNull { it.status == "downloading" }?.episodeId?.toIntOrNull() ?: 0,
                        ),
                    ),
                )

                "paused" -> SeasonQueueExecution.Paused(DownloadFailureReason.NeedsWifi)

                "completed" -> SeasonQueueExecution.Completed

                "failed" -> SeasonQueueExecution.Failed(DownloadFailureReason.DownloadFailed)

                else -> SeasonQueueExecution.PendingConfirmation
            },
            transferPreference = TransferPreference.WifiOnly,
        )
    }
}

private fun Media.MediaId.toEpisodeIdString(): String = when (this) {
    is Media.MediaId.Episode -> value.value.toString()
    is Media.MediaId.Movie -> value.value.toString()
    is Media.MediaId.Show -> value.value.toString()
}
