package net.subsloth.core.media.download

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import net.subsloth.core.domain.policy.DownloadPolicy
import net.subsloth.core.domain.port.ConnectivityPort
import net.subsloth.core.domain.port.DownloadCommandOutcome
import net.subsloth.core.domain.port.DownloadsPort
import net.subsloth.core.domain.port.StoragePort
import net.subsloth.core.domain.port.SubtitleEnqueueOutcome
import net.subsloth.core.model.download.DownloadFailureReason
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.database.dao.DownloadedMediaDao
import net.subsloth.database.dao.DownloadedSubtitleDao
import net.subsloth.database.dao.OfflineDisplayMetadataDao
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.DownloadedSubtitleEntity
import kotlin.time.Instant

class DownloadController(
    private val storageManager: DownloadStorageManager,
    private val storageProvider: StoragePort,
    private val connectivityChecker: ConnectivityPort,
    private val downloadedMediaDao: DownloadedMediaDao,
    private val downloadedSubtitleDao: DownloadedSubtitleDao,
    private val offlineDisplayMetadataDao: OfflineDisplayMetadataDao,
) : DownloadsPort {

    override suspend fun listDownloads(): Result<ImmutableList<DownloadState>> = runCatching {
        downloadedMediaDao.getAll().first().map { it.toDownloadState() }.toImmutableList()
    }

    override suspend fun listOfflineAssets(): Result<ImmutableList<OfflineAsset>> = runCatching {
        downloadedMediaDao.getCompleted().first().map { entity ->
            OfflineAsset(
                mediaId = parseMediaId(entity.contentId, entity.mediaType),
                localId = LocalMediaIdentifier("${entity.contentId}/${entity.id}"),
                videoRelativePath = OfflineRelativePath.safe(entity.localFilePath),
                subtitleLanguages = persistentSetOf(),
                effectiveQuality = QualityDescriptor(
                    resolution = parseResolution(entity.selectedQuality),
                    label = entity.selectedQuality,
                    bitrate = null,
                    mimeType = null,
                ),
                displayTitle = entity.contentId,
                isPlayable = entity.localFilePath.isNotBlank(),
            )
        }.toImmutableList()
    }

    override suspend fun enqueue(
        mediaId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long?,
        transferPreference: TransferPreference,
    ): Result<EnqueueOutcome> = runCatching {
        val existing = downloadedMediaDao.getAll().first().map { it.toDownloadState() }
        if (DownloadPolicy.isDuplicate(existing, mediaId, requestedResolution = requested)) {
            return@runCatching EnqueueOutcome.AlreadyAvailableHigherQuality
        }
        if (!DownloadPolicy.canStartNewDownload(existing, mediaId)) {
            error("A download for this media is already active or queued")
        }
        if (!DownloadPolicy.canTransferOnNetwork(
                isMetered = connectivityChecker.isMetered(),
                transferPreference = transferPreference,
            )
        ) {
            error("Download requires Wi-Fi")
        }
        val needBytes = requiredBytes ?: 0L
        if (!DownloadPolicy.hasSufficientStorage(
                availableBytes = storageProvider.availableBytes(),
                requiredBytes = needBytes,
                reserveBytes = storageProvider.reserveBytes(),
            )
        ) {
            error("Insufficient storage available")
        }
        val entity = DownloadedMediaEntity(
            contentId = mediaId.toContentId(),
            mediaType = mediaId.toMediaType(),
            localFilePath = "",
            sizeBytes = needBytes,
            status = DownloadStatus.QUEUED.name.lowercase(),
            selectedQuality = requested.label,
            downloadedAtEpochSeconds = null,
        )
        downloadedMediaDao.upsert(entity)
        EnqueueOutcome.Queued
    }

    override suspend fun enqueueSubtitle(
        localId: LocalMediaIdentifier,
        language: LanguageCode,
    ): Result<SubtitleEnqueueOutcome> = runCatching {
        val all = downloadedMediaDao.getAll().first()
        val downloadId = all.firstOrNull { "${it.contentId}/${it.id}" == localId.value }?.id
            ?: error("No download found for $localId")
        val existing = downloadedSubtitleDao.getForDownload(downloadId).first()
        if (existing.any { it.language == language.value }) {
            return@runCatching SubtitleEnqueueOutcome.AlreadyAvailable
        }
        downloadedSubtitleDao.upsert(
            DownloadedSubtitleEntity(
                downloadId = downloadId,
                language = language.value,
                source = null,
                format = null,
                localFilePath = "",
            ),
        )
        SubtitleEnqueueOutcome.Queued
    }

    override suspend fun pause(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome> = runCatching {
        updateStatus(localId, DownloadStatus.PAUSED)
        DownloadCommandOutcome.Applied
    }

    override suspend fun resume(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome> = runCatching {
        updateStatus(localId, DownloadStatus.QUEUED)
        DownloadCommandOutcome.Applied
    }

    override suspend fun cancel(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome> = runCatching {
        val entity = findByLocalId(localId) ?: error("Download not found: ${localId.value}")
        val path = entity.localFilePath
        if (path.isNotBlank()) {
            storageManager.deleteMedia(OfflineRelativePath.safe(path))
        }
        updateStatus(localId, DownloadStatus.REMOVED)
        DownloadCommandOutcome.Applied
    }

    override suspend fun remove(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome> = runCatching {
        val entity = findByLocalId(localId) ?: error("Download not found: ${localId.value}")
        val path = entity.localFilePath
        if (path.isNotBlank()) {
            storageManager.deleteMedia(OfflineRelativePath.safe(path))
        }
        downloadedMediaDao.delete(entity)
        val remainingForContent = downloadedMediaDao.getByContent(entity.contentId, entity.mediaType)
        if (remainingForContent == null) {
            val metadata = offlineDisplayMetadataDao.getByContentId(entity.contentId)
            if (metadata != null) offlineDisplayMetadataDao.delete(metadata)
        }
        DownloadCommandOutcome.Applied
    }

    private suspend fun updateStatus(localId: LocalMediaIdentifier, status: DownloadStatus) {
        val entity = findByLocalId(localId) ?: return
        downloadedMediaDao.upsert(entity.copy(status = status.name.lowercase()))
    }

    private suspend fun findByLocalId(localId: LocalMediaIdentifier): DownloadedMediaEntity? {
        val all = downloadedMediaDao.getAll().first()
        return all.firstOrNull { "${it.contentId}/${it.id}" == localId.value }
    }

    private fun DownloadedMediaEntity.toDownloadState(): DownloadState {
        val mediaId = parseMediaId(contentId, mediaType)
        val quality = QualityDescriptor(
            resolution = parseResolution(selectedQuality),
            label = selectedQuality,
            bitrate = null,
            mimeType = null,
        )
        val localId = LocalMediaIdentifier("$contentId/$id")
        return when (parseStatus(status)) {
            DownloadStatus.QUEUED -> DownloadState.Queued(
                localId = localId,
                mediaId = mediaId,
                quality = quality,
            )

            DownloadStatus.DOWNLOADING -> DownloadState.Active(
                localId = localId,
                mediaId = mediaId,
                quality = quality,
                progressPercent = 0,
            )

            DownloadStatus.COMPLETED -> DownloadState.Completed(
                localId = localId,
                mediaId = mediaId,
                quality = quality,
                downloadedAtEpochSeconds = downloadedAtEpochSeconds?.let { Instant.fromEpochSeconds(it) }
                    ?: Instant.fromEpochSeconds(0),
                sizeBytes = sizeBytes.takeIf { it > 0L },
                videoPath = OfflineRelativePath.safe(localFilePath.ifBlank { "unknown" }),
            )

            DownloadStatus.FAILED -> DownloadState.Failed(
                localId = localId,
                mediaId = mediaId,
                quality = quality,
                reason = DownloadFailureReason.DownloadFailed,
            )

            DownloadStatus.PAUSED -> DownloadState.Paused(
                localId = localId,
                mediaId = mediaId,
                quality = quality,
                reason = DownloadFailureReason.NeedsWifi,
            )

            DownloadStatus.REMOVED -> DownloadState.Removed(
                localId = localId,
                mediaId = mediaId,
                quality = quality,
            )
        }
    }

    private fun parseStatus(status: String): DownloadStatus = when (status.lowercase()) {
        "queued" -> DownloadStatus.QUEUED
        "downloading" -> DownloadStatus.DOWNLOADING
        "completed" -> DownloadStatus.COMPLETED
        "failed" -> DownloadStatus.FAILED
        "paused" -> DownloadStatus.PAUSED
        "removed" -> DownloadStatus.REMOVED
        else -> DownloadStatus.FAILED
    }
}

internal enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED,
    REMOVED,
}

internal fun parseResolution(label: String?): Resolution = when {
    label == null -> Resolution.HD_720
    label.contains("4K") || label.contains("2160") || label.contains("UHD") -> Resolution.UHD_4K
    label.contains("1440") || label.contains("QHD") -> Resolution.QHD
    label.contains("1080") || label.contains("FHD") || label.contains("full", ignoreCase = true) -> Resolution.FULL_HD
    label.contains("720") || label.contains("HD") -> Resolution.HD_720
    else -> Resolution.HD_720
}

private fun Media.MediaId.toContentId(): String = when (this) {
    is Media.MediaId.Movie -> value.value.toString()
    is Media.MediaId.Show -> value.value.toString()
    is Media.MediaId.Episode -> value.value.toString()
}

private fun Media.MediaId.toMediaType(): String = when (this) {
    is Media.MediaId.Movie -> "movie"
    is Media.MediaId.Show -> "show"
    is Media.MediaId.Episode -> "episode"
}

private fun parseMediaId(contentId: String, mediaType: String): Media.MediaId = when (mediaType) {
    "movie" -> Media.MediaId.Movie(net.subsloth.core.model.identifier.MovieId(contentId.toIntOrNull() ?: 0))
    "episode" -> Media.MediaId.Episode(net.subsloth.core.model.identifier.EpisodeId(contentId.toIntOrNull() ?: 0))
    "show" -> Media.MediaId.Show(net.subsloth.core.model.identifier.ShowId(contentId.toIntOrNull() ?: 0))
    else -> Media.MediaId.Movie(net.subsloth.core.model.identifier.MovieId(0))
}
