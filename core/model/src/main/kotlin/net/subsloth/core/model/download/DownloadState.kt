package net.subsloth.core.model.download

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import kotlin.time.Instant

sealed interface DownloadState {
    val localId: LocalMediaIdentifier
    val mediaId: Media.MediaId
    val quality: QualityDescriptor
    val subtitleLanguages: ImmutableSet<LanguageCode>

    @Immutable
    data class Queued(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Active(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val progressPercent: Int,
        val queueId: QueueId? = null,
    ) : DownloadState {
        init {
            require(progressPercent in 0..100) { "Progress percent must be between 0 and 100" }
        }
    }

    @Immutable
    data class Partial(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val stagedPath: OfflineRelativePath,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Completed(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        val downloadedAtEpochSeconds: Instant,
        val sizeBytes: Long?,
        val videoPath: OfflineRelativePath,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
    ) : DownloadState

    @Immutable
    data class Failed(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val reason: DownloadFailureReason,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Paused(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val reason: DownloadFailureReason,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Unavailable(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
        val reason: DownloadFailureReason,
        val queueId: QueueId? = null,
    ) : DownloadState

    @Immutable
    data class Removed(
        override val localId: LocalMediaIdentifier,
        override val mediaId: Media.MediaId,
        override val quality: QualityDescriptor,
        override val subtitleLanguages: ImmutableSet<LanguageCode> = persistentSetOf(),
    ) : DownloadState
}
