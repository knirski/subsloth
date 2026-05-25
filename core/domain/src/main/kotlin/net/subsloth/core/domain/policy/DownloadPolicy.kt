package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.DownloadStatus
import net.subsloth.core.model.download.SeasonPreflight
import net.subsloth.core.model.download.SeasonQueue
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Episode
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle

/**
 * Pure policies for download decisions.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object DownloadPolicy {
    /** Download statuses that count as "active" for concurrency limiting. */
    private val ACTIVE_STATUSES: Set<DownloadStatus> =
        setOf(
            DownloadStatus.QUEUED,
            DownloadStatus.DOWNLOADING,
        )

    /**
     * Returns `true` when the available free space is sufficient for a
     * new download, considering the minimum [reserveBytes] that must be
     * kept free.
     *
     * @param availableBytes free space on the storage device.
     * @param requiredBytes space needed for the download.
     * @param reserveBytes minimum free space that must remain after download.
     */
    fun hasSufficientStorage(
        availableBytes: Long,
        requiredBytes: Long,
        reserveBytes: Long,
    ): Boolean = availableBytes >= requiredBytes + reserveBytes

    /**
     * Returns `true` when a new download can be started given the current
     * list of [existingDownloads].
     *
     * Only one active video download is allowed at a time. Active downloads
     * include [DownloadStatus.QUEUED] and [DownloadStatus.DOWNLOADING].
     */
    fun canStartNewDownload(existingDownloads: List<DownloadState>): Boolean =
        existingDownloads.none { it.status in ACTIVE_STATUSES }

    /**
     * Returns `true` when [candidateMediaId] is already present in
     * [existingDownloads].
     */
    fun isDuplicate(
        existingDownloads: List<DownloadState>,
        candidateMediaId: Media.MediaId,
    ): Boolean = existingDownloads.any { it.mediaId == candidateMediaId }

    /**
     * Returns `true` when replacing [existing] quality with [candidate]
     * quality is allowed.
     *
     * Only upgrades to a strictly higher quality are permitted.
     */
    fun canReplaceQuality(
        existing: QualityDescriptor,
        candidate: QualityDescriptor,
    ): Boolean = candidate.resolution.pixelCount > existing.resolution.pixelCount

    /**
     * Returns a new list with all incomplete downloads paused.
     *
     * Completed and already-removed downloads are not affected.
     */
    fun pauseOnLogout(downloads: List<DownloadState>): List<DownloadState> =
        downloads.map { download ->
            if (download.status in ACTIVE_STATUSES) {
                download.copy(status = DownloadStatus.PAUSED)
            } else {
                download
            }
        }

    /**
     * Returns `true` when downloading is allowed on the current network.
     */
    fun canDownloadOnMetered(
        isMetered: Boolean,
        userOptedIn: Boolean,
    ): Boolean = !isMetered || userOptedIn

    /**
     * Selects the best quality from [available] Qualities to fall back to
     * when [preferred] is unavailable.
     *
     * Strategy: exact match -> nearest lower -> nearest higher -> null.
     */
    fun selectFallbackQuality(
        available: List<QualityDescriptor>,
        preferred: Resolution,
    ): QualityDescriptor? {
        if (available.isEmpty()) return null
        val exact = available.find { it.resolution == preferred }
        if (exact != null) return exact
        val lower = available.filter { it.resolution.pixelCount < preferred.pixelCount }
            .maxByOrNull { it.resolution.pixelCount }
        if (lower != null) return lower
        return available.minByOrNull { it.resolution.pixelCount }
    }

    /**
     * Selects the best subtitle from [available] to fall back to when
     * [preferred] is unavailable.
     *
     * Strategy: preferred -> English (if [fallbackToEnglish]) -> first available -> null.
     */
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

    /**
     * Prepares a [SeasonPreflight] by inspecting [episodes] against the
     * user's quality preference [qualityPref] and subtitle preference
     * [subtitlePref].
     *
     * Counts fallbacks needed, unavailable episodes, and aggregates size info.
     */
    fun prepareSeasonPreflight(
        episodes: List<Episode>,
        qualityPref: Resolution,
        subtitlePref: LanguageCode,
    ): SeasonPreflight {
        var fallbackQualityCount = 0
        var fallbackSubtitleCount = 0
        var noSubtitleCount = 0
        var unavailableCount = 0

        for (episode in episodes) {
            if (episode.availability !is net.subsloth.core.model.Availability.Available) {
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

        return SeasonPreflight(
            episodeCount = episodes.size,
            knownSizeBytes = null,
            hasUnknownSizes = true,
            fallbackQualityCount = fallbackQualityCount,
            fallbackSubtitleCount = fallbackSubtitleCount,
            noSubtitleCount = noSubtitleCount,
            unavailableCount = unavailableCount,
        )
    }

    /**
     * Returns `true` when a [queue] can be resumed given the current device
     * conditions.
     */
    fun canResumeQueue(
        queue: SeasonQueue,
        isOnline: Boolean,
        hasStorage: Boolean,
        isMeteredOk: Boolean,
        isAuthOk: Boolean,
    ): Boolean = isOnline && hasStorage && isMeteredOk && isAuthOk

    /**
     * Returns `true` when any download in [downloads] is in [DownloadStatus.COMPLETED].
     */
    fun hasPlayableDownloads(downloads: List<DownloadState>): Boolean =
        downloads.any { it.status == DownloadStatus.COMPLETED }

    /**
     * Returns `true` when a [download] is playable from local storage.
     * The download must be [DownloadStatus.COMPLETED] and the file must
     * exist and be non-empty.
     */
    fun isPlayableLocally(
        download: DownloadState,
        fileExists: Boolean,
        fileNonEmpty: Boolean,
    ): Boolean = download.status == DownloadStatus.COMPLETED && fileExists && fileNonEmpty

    /**
     * Returns the [FileStatus] for a file given its existence and emptiness.
     */
    fun fileIntegrityStatus(
        fileExists: Boolean,
        fileNonEmpty: Boolean,
    ): FileStatus = when {
        !fileExists -> FileStatus.MISSING
        !fileNonEmpty -> FileStatus.CORRUPT
        else -> FileStatus.PLAYABLE
    }

    /**
     * Determines the [OfflineHomeMode] based on connectivity, download state,
     * and on-disk file verification.
     */
    fun offlineHomeMode(
        downloads: List<DownloadState>,
        connectivityAvailable: Boolean,
        fileVerificationFn: (LocalMediaIdentifier) -> Boolean,
    ): OfflineHomeMode {
        if (connectivityAvailable) return OfflineHomeMode.ONLINE
        val hasPlayable = downloads.any { download ->
            download.status == DownloadStatus.COMPLETED && fileVerificationFn(download.localId)
        }
        return if (hasPlayable) OfflineHomeMode.OFFLINE_WITH_DOWNLOADS
        else OfflineHomeMode.OFFLINE_NO_DOWNLOADS
    }

    /** Status of a downloaded media file on disk. */
    sealed interface FileStatus {
        data object PLAYABLE : FileStatus
        data object MISSING : FileStatus
        data object CORRUPT : FileStatus
    }

    /** Offline home screen mode. */
    sealed interface OfflineHomeMode {
        data object ONLINE : OfflineHomeMode
        data object OFFLINE_WITH_DOWNLOADS : OfflineHomeMode
        data object OFFLINE_NO_DOWNLOADS : OfflineHomeMode
    }
}
