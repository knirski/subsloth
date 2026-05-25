package net.subsloth.core.model.download

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.QualityDescriptor
import kotlin.time.Instant

@Immutable
@JvmInline
value class QueueId(val value: String)

@Immutable
enum class QueueItemStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    SKIPPED,
    CANCELLED,
}

@Immutable
enum class QueueStatus {
    CONFIRMING,
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
}

@Immutable
data class QueueItem(
    val episodeId: EpisodeId,
    val episodeTitle: String,
    val quality: QualityDescriptor?,
    val subtitleLanguages: List<LanguageCode>?,
    val sizeBytes: Long?,
    val status: QueueItemStatus,
)

@Immutable
data class SeasonQueue(
    val id: QueueId,
    val showId: ShowId,
    val seasonNumber: Int,
    val items: List<QueueItem>,
    val status: QueueStatus,
    val createdAtEpochSeconds: Instant,
)

@Immutable
data class SeasonPreflight(
    val episodeCount: Int,
    val knownSizeBytes: Long?,
    val hasUnknownSizes: Boolean,
    val fallbackQualityCount: Int,
    val fallbackSubtitleCount: Int,
    val noSubtitleCount: Int,
    val unavailableCount: Int,
)
