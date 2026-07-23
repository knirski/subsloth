package net.subsloth.core.model.download

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media

private const val MAX_PROGRESS_PERCENT = 100

/**
 * A queue of episodes in a TV season to be downloaded, with execution state.
 *
 * Tracks which episodes are pending, downloading, completed, or failed,
 * as well as the overall [execution] state of the season batch operation.
 */
data class SeasonDownloadQueue(
    val queueId: QueueId,
    val showId: ShowId,
    val seasonNumber: Int,
    val items: ImmutableList<SeasonDownloadQueueItem>,
    val execution: SeasonQueueExecution,
    val transferPreference: TransferPreference,
) {
    init {
        require(seasonNumber >= 0) { "seasonNumber must be non-negative" }
    }
}

/** Lifecycle state of a season-level batch download operation. */
sealed interface SeasonQueueExecution {
    data object PendingConfirmation : SeasonQueueExecution

    data object Queued : SeasonQueueExecution

    data class Running(val activeItem: Media.MediaId.Episode) : SeasonQueueExecution

    data class Paused(val reason: DownloadFailureReason) : SeasonQueueExecution

    data object Completed : SeasonQueueExecution

    data class Failed(val reason: DownloadFailureReason) : SeasonQueueExecution
}

/** A single episode within a [SeasonDownloadQueue], with its own execution state. */
data class SeasonDownloadQueueItem(
    val mediaId: Media.MediaId.Episode,
    val selectedQuality: Resolution,
    val preferredSubtitleLanguage: LanguageCode,
    val subtitleSelection: SubtitleSelection,
    val execution: SeasonQueueItemExecution,
)

/** Execution state of a single episode within a season download queue. */
sealed interface SeasonQueueItemExecution {
    data object Pending : SeasonQueueItemExecution

    data class Downloading(val progressPercent: Int) : SeasonQueueItemExecution {
        init {
            require(
                progressPercent in 0..MAX_PROGRESS_PERCENT,
            ) { "Progress percent must be between 0 and $MAX_PROGRESS_PERCENT" }
        }
    }

    data object Completed : SeasonQueueItemExecution

    data class Failed(val reason: DownloadFailureReason) : SeasonQueueItemExecution

    data object Cancelled : SeasonQueueItemExecution
}

/**
 * Summary of what will happen when a user confirms a season download.
 *
 * Counts how many episodes are already available, will require quality
 * fallback, will fall back to English subtitles, have no subtitles, or
 * are unavailable entirely.
 */
data class SeasonDownloadConfirmation(
    val episodeCount: Int,
    val alreadyAvailableCount: Int,
    val fallbackQualityCount: Int,
    val fallbackSubtitleToEnglishCount: Int,
    val noSubtitleCount: Int,
    val unavailableCount: Int,
    val sizeEstimate: SizeEstimate,
    val transferPreference: TransferPreference,
) {
    init {
        require(episodeCount >= 0) { "episodeCount must be non-negative" }
        require(alreadyAvailableCount >= 0) { "alreadyAvailableCount must be non-negative" }
        require(fallbackQualityCount >= 0) { "fallbackQualityCount must be non-negative" }
        require(fallbackSubtitleToEnglishCount >= 0) { "fallbackSubtitleToEnglishCount must be non-negative" }
        require(noSubtitleCount >= 0) { "noSubtitleCount must be non-negative" }
        require(unavailableCount >= 0) { "unavailableCount must be non-negative" }
        require(alreadyAvailableCount <= episodeCount) { "alreadyAvailableCount must be <= episodeCount" }
        require(fallbackQualityCount <= episodeCount) { "fallbackQualityCount must be <= episodeCount" }
        require(
            fallbackSubtitleToEnglishCount <= episodeCount,
        ) { "fallbackSubtitleToEnglishCount must be <= episodeCount" }
        require(noSubtitleCount <= episodeCount) { "noSubtitleCount must be <= episodeCount" }
        require(unavailableCount <= episodeCount) { "unavailableCount must be <= episodeCount" }
    }
}
