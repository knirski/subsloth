package net.subsloth.core.domain.policy

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.Availability
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.SeasonDownloadConfirmation
import net.subsloth.core.model.download.SizeEstimate
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle

private const val RESERVE_CAP_BYTES = 2L * 1024 * 1024 * 1024

/** Pure policies for download decisions: storage reserve, network transfer, and quality replacement. */
object DownloadPolicy {
    /** Reserve 10 % of total space, capped at 2 GiB. */
    @Suppress("MagicNumber")
    fun requiredReserveBytes(totalBytes: Long): Long = minOf(RESERVE_CAP_BYTES, totalBytes.coerceAtLeast(0) / 10)

    fun canTransferOnNetwork(isMetered: Boolean, transferPreference: TransferPreference): Boolean =
        when (transferPreference) {
            TransferPreference.WifiOnly -> !isMetered
            TransferPreference.MeteredAllowed -> true
        }

    fun canReplaceQuality(existing: QualityDescriptor, candidate: QualityDescriptor): Boolean =
        candidate.resolution.pixelCount > existing.resolution.pixelCount

    fun hasSufficientStorage(availableBytes: Long, requiredBytes: Long, reserveBytes: Long): Boolean =
        availableBytes >= requiredBytes + reserveBytes

    fun isDuplicate(
        existing: List<DownloadState>,
        mediaId: net.subsloth.core.model.media.Media.MediaId,
        requestedResolution: Resolution? = null,
    ): Boolean = existing.any { state ->
        if (state.mediaId != mediaId || state !is DownloadState.Completed) return@any false
        if (requestedResolution != null) {
            state.quality.resolution.pixelCount >= requestedResolution.pixelCount
        } else {
            true
        }
    }

    fun canStartNewDownload(
        existing: List<DownloadState>,
        mediaId: net.subsloth.core.model.media.Media.MediaId,
    ): Boolean = existing.none {
        it.mediaId == mediaId && (it is DownloadState.Active || it is DownloadState.Queued)
    }

    fun selectFallbackQuality(available: List<QualityDescriptor>, preferred: Resolution): QualityDescriptor? {
        if (available.isEmpty()) return null
        val exact = available.find { it.resolution == preferred }
        if (exact != null) return exact
        val lower = available.filter { it.resolution.pixelCount < preferred.pixelCount }
            .maxByOrNull { it.resolution.pixelCount }
        if (lower != null) return lower
        return available.minByOrNull { it.resolution.pixelCount }
    }

    fun selectFallbackSubtitle(
        available: List<Subtitle>,
        preferred: LanguageCode,
        fallbackToEnglish: Boolean,
    ): Subtitle? {
        if (available.isEmpty()) return null
        val exact = available.find { it.language == preferred }
        if (exact != null) return exact
        if (fallbackToEnglish) {
            val english = available.find { it.language == LanguageCode("en") }
            if (english != null) return english
        }
        return available.first()
    }

    fun hasPlayableDownloads(downloads: List<DownloadState>): Boolean = downloads.any { it is DownloadState.Completed }

    fun isPlayableLocally(download: DownloadState, fileExists: Boolean, fileNonEmpty: Boolean): Boolean =
        download is DownloadState.Completed && fileExists && fileNonEmpty

    fun fileIntegrityStatus(fileExists: Boolean, fileNonEmpty: Boolean): FileStatus = when {
        !fileExists -> FileStatus.MISSING
        !fileNonEmpty -> FileStatus.CORRUPT
        else -> FileStatus.PLAYABLE
    }

    fun prepareSeasonPreflight(
        episodes: ImmutableList<Episode>,
        qualityPref: Resolution,
        subtitlePref: LanguageCode,
        transferPreference: TransferPreference,
        alreadyDownloaded: Set<net.subsloth.core.model.media.Media.MediaId> = emptySet(),
    ): SeasonDownloadConfirmation {
        var fallbackQualityCount = 0
        var fallbackSubtitleCount = 0
        var noSubtitleCount = 0
        var unavailableCount = 0
        var alreadyAvailableCount = 0

        for (episode in episodes) {
            if (alreadyDownloaded.contains(
                    net.subsloth.core.model.media.Media.MediaId.Episode(episode.id),
                )
            ) {
                alreadyAvailableCount++
                continue
            }
            if (episode.availability !is Availability.Available) {
                unavailableCount++
                continue
            }
            val hasExactQuality = episode.qualities.any { it.info.resolution == qualityPref }
            if (!hasExactQuality) fallbackQualityCount++

            val hasPreferredSub = episode.subtitles.any { it.language == subtitlePref }
            if (!hasPreferredSub) {
                if (episode.subtitles.isEmpty()) {
                    noSubtitleCount++
                } else {
                    fallbackSubtitleCount++
                }
            }
        }

        return SeasonDownloadConfirmation(
            episodeCount = episodes.size,
            alreadyAvailableCount = alreadyAvailableCount,
            fallbackQualityCount = fallbackQualityCount,
            fallbackSubtitleToEnglishCount = fallbackSubtitleCount,
            noSubtitleCount = noSubtitleCount,
            unavailableCount = unavailableCount,
            sizeEstimate = SizeEstimate.Unknown,
            transferPreference = transferPreference,
        )
    }
}

sealed interface FileStatus {
    data object PLAYABLE : FileStatus
    data object MISSING : FileStatus
    data object CORRUPT : FileStatus
}
