package subsloth.core.model.download

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.identifier.LocalMediaIdentifier
import subsloth.core.model.media.Media
import subsloth.core.model.media.QualityDescriptor
import kotlin.time.Instant

private const val MAX_PROGRESS_PERCENT = 100

/**
 * Represents the state of a download in the offline lifecycle.
 *
 * Each variant carries only the fields relevant to that state, eliminating
 * the nullable baggage of the previous single-data-class design.
 *
 * ## Variants
 * - [Queued] — awaiting execution
 * - [Active] — download in progress with progress tracking
 * - [Partial] — partially downloaded with a staged file
 * - [Completed] — fully downloaded and available offline
 * - [Failed] — terminated with a [Failed.reason]
 * - [Paused] — suspended with a [Paused.reason]
 * - [Unavailable] — local file no longer accessible
 * - [Removed] — explicitly deleted from local storage
 */
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
            require(
                progressPercent in 0..MAX_PROGRESS_PERCENT,
            ) { "Progress percent must be between 0 and $MAX_PROGRESS_PERCENT" }
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
    ) : DownloadState {
        init {
            require(sizeBytes == null || sizeBytes >= 0) { "sizeBytes must be non-negative when provided" }
        }
    }

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
