package net.subsloth.core.media.download

import kotlinx.collections.immutable.ImmutableList
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
import kotlin.time.Clock

class SeasonQueueController(
    private val downloadsPort: DownloadsPort,
    private val seasonQueueDao: SeasonQueueDao,
    private val clock: Clock,
) {
    suspend fun listQueues(): List<SeasonDownloadQueue> {
        // Completed queues exist only for UI bookkeeping; prune them once
        // they are old enough that the show screen no longer needs the
        // "completed" affordance.
        seasonQueueDao.deleteCompletedQueuesOlderThan(
            clock.now().epochSeconds - COMPLETED_QUEUE_RETENTION_SECONDS,
        )
        return seasonQueueDao.getAllQueues().first().map { entity ->
            val items = seasonQueueDao.getItemsForQueue(entity.id)
            entity.toDomain(items)
        }
    }

    suspend fun createQueue(
        queueId: QueueId,
        showId: ShowId,
        seasonNumber: Int,
        episodes: ImmutableList<Episode>,
        qualityPref: Resolution,
        subtitlePref: LanguageCode,
        transferPreference: TransferPreference,
        confirmation: SeasonDownloadConfirmation,
    ): SeasonDownloadQueue {
        val queueEntity = SeasonQueueEntity(
            id = queueId.value,
            showId = showId.value.toString(),
            seasonNumber = seasonNumber,
            status = "pending_confirmation",
            createdAtEpochSeconds = clock.now().epochSeconds,
        )
        seasonQueueDao.upsertQueue(queueEntity)
        // Re-queueing the same season replaces any previous item rows so a
        // retried download does not duplicate them.
        seasonQueueDao.deleteItemsForQueue(queueId.value)

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
            items = items.toImmutableList(),
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
        return queue
    }

    suspend fun confirmQueue(queueId: QueueId) {
        val entity = seasonQueueDao.getQueue(queueId.value) ?: return
        seasonQueueDao.upsertQueue(entity.copy(status = "queued"))
    }

    suspend fun executeNext(queueId: QueueId): SeasonQueueExecution {
        val queue = seasonQueueDao.getQueue(queueId.value) ?: return SeasonQueueExecution.Completed
        if (queue.status != "queued" && queue.status != "running") {
            return when (queue.status) {
                "paused" -> SeasonQueueExecution.Paused(DownloadFailureReason.NeedsWifi)
                "completed" -> SeasonQueueExecution.Completed
                "failed" -> SeasonQueueExecution.Failed(DownloadFailureReason.DownloadFailed)
                else -> SeasonQueueExecution.PendingConfirmation
            }
        }
        val items = seasonQueueDao.getItemsForQueue(queueId.value)
        val nextPending = items.firstOrNull { it.status == "pending" }
        if (nextPending == null) {
            markQueueCompletedIfDone(queueId)
            return SeasonQueueExecution.Completed
        }

        seasonQueueDao.upsertItem(nextPending.copy(status = "downloading"))
        val queueEntity = seasonQueueDao.getQueue(queueId.value)
        if (queueEntity != null) {
            seasonQueueDao.upsertQueue(queueEntity.copy(status = "running"))
        }

        val mediaId = Media.MediaId.Episode(
            net.subsloth.core.model.identifier.EpisodeId(
                nextPending.episodeId.toIntOrNull()
                    ?: error("Invalid episodeId: ${nextPending.episodeId}"),
            ),
        )
        val result = downloadsPort.enqueue(
            mediaId = mediaId,
            requested = parseResolution(nextPending.qualityLabel),
            requiredBytes = nextPending.sizeBytes,
            transferPreference = TransferPreference.WifiOnly,
        )

        fun parseFailureReason(error: Throwable): DownloadFailureReason {
            val message = error.message ?: ""
            return when {
                message.contains("Wi-Fi", ignoreCase = true) ||
                    message.contains("wifi", ignoreCase = true) ||
                    message.contains("NeedsWifi", ignoreCase = true) -> DownloadFailureReason.NeedsWifi

                message.contains("storage", ignoreCase = true) -> DownloadFailureReason.InsufficientStorage

                message.contains("already", ignoreCase = true) -> DownloadFailureReason.DownloadFailed

                else -> DownloadFailureReason.DownloadFailed
            }
        }

        return result.fold(
            onSuccess = { outcome ->
                enqueueSubtitleFor(nextPending, mediaId)
                when (outcome) {
                    EnqueueOutcome.Queued -> SeasonQueueExecution.Running(mediaId)

                    EnqueueOutcome.AlreadyAvailableHigherQuality -> {
                        seasonQueueDao.upsertItem(nextPending.copy(status = "completed"))
                        executeNext(queueId)
                    }
                }
            },
            onFailure = { error ->
                val reason = parseFailureReason(error)
                seasonQueueDao.upsertItem(nextPending.copy(status = "failed", failureReason = reason.name))
                val queueEntity = seasonQueueDao.getQueue(queueId.value)
                if (queueEntity != null) {
                    seasonQueueDao.upsertQueue(queueEntity.copy(status = "failed", failureReason = reason.name))
                }
                SeasonQueueExecution.Failed(reason)
            },
        )
    }

    /**
     * Marks the queue item for [mediaId] as completed and completes the
     * queue when every item reached a terminal completed state. Called by
     * the queue driver once the transferred download is available.
     */
    suspend fun markItemCompleted(queueId: QueueId, mediaId: Media.MediaId) {
        updateItemStatus(queueId, mediaId, status = "completed")
        markQueueCompletedIfDone(queueId)
    }

    /** Marks the queue item for [mediaId] as failed and fails its queue. */
    suspend fun markItemFailed(queueId: QueueId, mediaId: Media.MediaId, reason: DownloadFailureReason) {
        val episodeId = (mediaId as? Media.MediaId.Episode)?.value?.value?.toString() ?: return
        val item = seasonQueueDao.getItemsForQueue(queueId.value).firstOrNull { it.episodeId == episodeId } ?: return
        seasonQueueDao.upsertItem(item.copy(status = "failed", failureReason = reason.name))
        seasonQueueDao.getQueue(queueId.value)?.let { queue ->
            seasonQueueDao.upsertQueue(queue.copy(status = "failed", failureReason = reason.name))
        }
    }

    suspend fun pauseQueue(queueId: QueueId, reason: DownloadFailureReason = DownloadFailureReason.NeedsWifi) {
        val entity = seasonQueueDao.getQueue(queueId.value) ?: return
        seasonQueueDao.upsertQueue(entity.copy(status = "paused", failureReason = reason.name))
    }

    suspend fun resumeQueue(queueId: QueueId) {
        val entity = seasonQueueDao.getQueue(queueId.value) ?: return
        seasonQueueDao.upsertQueue(entity.copy(status = "queued"))
    }

    suspend fun cancelQueue(queueId: QueueId) {
        seasonQueueDao.deleteQueue(queueId.value)
    }

    private suspend fun updateItemStatus(queueId: QueueId, mediaId: Media.MediaId, status: String) {
        val episodeId = (mediaId as? Media.MediaId.Episode)?.value?.value?.toString() ?: return
        val item = seasonQueueDao.getItemsForQueue(queueId.value).firstOrNull { it.episodeId == episodeId } ?: return
        seasonQueueDao.upsertItem(item.copy(status = status))
    }

    private suspend fun markQueueCompletedIfDone(queueId: QueueId) {
        val items = seasonQueueDao.getItemsForQueue(queueId.value)
        if (items.isNotEmpty() && items.all { it.status == "completed" }) {
            seasonQueueDao.getQueue(queueId.value)?.let { queue ->
                seasonQueueDao.upsertQueue(queue.copy(status = "completed"))
            }
        }
    }

    /**
     * Enqueues the queue item's subtitle against the actual download row
     * created for [mediaId] — the local id embeds the downloaded-entity id,
     * which differs from the queue-item id.
     */
    private suspend fun enqueueSubtitleFor(item: QueueItemEntity, mediaId: Media.MediaId) {
        val language = item.subtitleLanguages ?: return
        val created = downloadsPort.listDownloads().getOrNull()
            ?.firstOrNull { it.mediaId == mediaId }
            ?: return
        downloadsPort.enqueueSubtitle(created.localId, LanguageCode(language))
    }

    private fun SeasonQueueEntity.toDomain(items: List<QueueItemEntity>): SeasonDownloadQueue {
        fun domFailureReason(
            raw: String?,
            default: DownloadFailureReason = DownloadFailureReason.DownloadFailed,
        ): DownloadFailureReason {
            fun parseDownloadFailureReason(value: String): DownloadFailureReason = when (value) {
                "NeedsWifi" -> DownloadFailureReason.NeedsWifi
                "InsufficientStorage" -> DownloadFailureReason.InsufficientStorage
                "MissingLocalFile" -> DownloadFailureReason.MissingLocalFile
                "SubtitleUnavailable" -> DownloadFailureReason.SubtitleUnavailable
                "AmbiguousQuality" -> DownloadFailureReason.AmbiguousQuality
                "DownloadFailed" -> DownloadFailureReason.DownloadFailed
                "Unavailable" -> DownloadFailureReason.Unavailable
                else -> DownloadFailureReason.DownloadFailed
            }
            return raw?.let { parseDownloadFailureReason(it) } ?: default
        }

        val queueId = QueueId(id)
        val domainItems = items.map { item ->
            SeasonDownloadQueueItem(
                mediaId = Media.MediaId.Episode(
                    net.subsloth.core.model.identifier.EpisodeId(
                        item.episodeId.toIntOrNull()
                            ?: error("Invalid episodeId: ${item.episodeId}"),
                    ),
                ),
                selectedQuality = parseResolution(item.qualityLabel),
                preferredSubtitleLanguage = LanguageCode(item.subtitleLanguages ?: "en"),
                subtitleSelection = SubtitleSelection.None,
                execution = when (item.status) {
                    "pending" -> SeasonQueueItemExecution.Pending

                    "downloading" -> SeasonQueueItemExecution.Downloading(0)

                    "completed" -> SeasonQueueItemExecution.Completed

                    "failed" -> SeasonQueueItemExecution.Failed(
                        domFailureReason(item.failureReason),
                    )

                    "cancelled" -> SeasonQueueItemExecution.Cancelled

                    else -> SeasonQueueItemExecution.Pending
                },
            )
        }
        return SeasonDownloadQueue(
            queueId = queueId,
            showId = ShowId(showId.toIntOrNull() ?: error("Invalid showId: $showId")),
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

                "paused" -> SeasonQueueExecution.Paused(
                    domFailureReason(failureReason, DownloadFailureReason.NeedsWifi),
                )

                "completed" -> SeasonQueueExecution.Completed

                "failed" -> SeasonQueueExecution.Failed(domFailureReason(failureReason))

                else -> SeasonQueueExecution.PendingConfirmation
            },
            transferPreference = TransferPreference.WifiOnly,
        )
    }

    private companion object {
        /** How long completed queues are kept for the show screen's state. */
        const val COMPLETED_QUEUE_RETENTION_SECONDS = 7L * 24 * 60 * 60
    }
}

private fun Media.MediaId.toEpisodeIdString(): String = when (this) {
    is Media.MediaId.Episode -> value.value.toString()
    is Media.MediaId.Movie -> value.value.toString()
    is Media.MediaId.Show -> value.value.toString()
}
