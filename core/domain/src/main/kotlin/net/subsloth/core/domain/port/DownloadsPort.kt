package net.subsloth.core.domain.port

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.EnqueueOutcome
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media

interface DownloadsPort {
    suspend fun listDownloads(): Result<ImmutableList<DownloadState>>

    suspend fun listOfflineAssets(): Result<ImmutableList<OfflineAsset>>

    suspend fun enqueue(
        mediaId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long? = null,
        transferPreference: TransferPreference = TransferPreference.WifiOnly,
    ): Result<EnqueueOutcome>

    suspend fun enqueueSubtitle(
        localId: LocalMediaIdentifier,
        language: LanguageCode,
    ): Result<SubtitleEnqueueOutcome>

    suspend fun pause(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>

    suspend fun resume(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>

    suspend fun cancel(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>

    suspend fun remove(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
}

sealed interface DownloadCommandOutcome {
    data object Applied : DownloadCommandOutcome

    data object NoOp : DownloadCommandOutcome
}

sealed interface SubtitleEnqueueOutcome {
    data object Queued : SubtitleEnqueueOutcome

    data object AlreadyAvailable : SubtitleEnqueueOutcome
}
