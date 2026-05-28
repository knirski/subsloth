package net.subsloth.core.model.download

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle

@JvmInline
value class QueueId(
    val value: String,
)

@JvmInline
value class OfflineRelativePath(
    val value: String,
)

sealed interface TransferPreference {
    data object WifiOnly : TransferPreference

    data object MeteredAllowed : TransferPreference
}

sealed interface SizeEstimate {
    data class Known(
        val bytes: Long,
    ) : SizeEstimate

    data object Unknown : SizeEstimate
}

sealed interface SubtitleSelection {
    data class Preferred(
        val subtitle: Subtitle,
    ) : SubtitleSelection

    data class EnglishFallback(
        val subtitle: Subtitle,
    ) : SubtitleSelection

    data object None : SubtitleSelection
}

@Immutable
data class OfflineAsset(
    val mediaId: Media.MediaId,
    val localId: LocalMediaIdentifier,
    val videoRelativePath: OfflineRelativePath,
    val subtitleLanguages: ImmutableSet<LanguageCode>,
    val effectiveQuality: QualityDescriptor,
    val displayTitle: String,
    val isPlayable: Boolean,
)
