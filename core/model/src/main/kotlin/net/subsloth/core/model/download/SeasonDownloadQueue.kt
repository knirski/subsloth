package net.subsloth.core.model.download

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media

@Immutable
data class SeasonDownloadQueue(
    val queueId: QueueId,
    val showId: ShowId,
    val seasonNumber: Int,
    val items: ImmutableList<SeasonDownloadQueueItem>,
    val execution: SeasonQueueExecution,
    val transferPreference: TransferPreference,
)

sealed interface SeasonQueueExecution {
    data object PendingConfirmation : SeasonQueueExecution
    data object Queued : SeasonQueueExecution
    data class Running(val activeItem: Media.MediaId.Episode) : SeasonQueueExecution
    data class Paused(val reason: DownloadFailureReason) : SeasonQueueExecution
    data object Completed : SeasonQueueExecution
    data class Failed(val reason: DownloadFailureReason) : SeasonQueueExecution
}

@Immutable
data class SeasonDownloadQueueItem(
    val mediaId: Media.MediaId.Episode,
    val selectedQuality: Resolution,
    val preferredSubtitleLanguage: LanguageCode,
    val subtitleSelection: SubtitleSelection,
    val execution: SeasonQueueItemExecution,
)

sealed interface SeasonQueueItemExecution {
    data object Pending : SeasonQueueItemExecution
    data class Downloading(val progressPercent: Int) : SeasonQueueItemExecution
    data object Completed : SeasonQueueItemExecution
    data class Failed(val reason: DownloadFailureReason) : SeasonQueueItemExecution
    data object Cancelled : SeasonQueueItemExecution
}

@Immutable
data class SeasonDownloadConfirmation(
    val episodeCount: Int,
    val alreadyAvailableCount: Int,
    val fallbackQualityCount: Int,
    val fallbackSubtitleToEnglishCount: Int,
    val noSubtitleCount: Int,
    val unavailableCount: Int,
    val sizeEstimate: SizeEstimate,
    val transferPreference: TransferPreference,
)
