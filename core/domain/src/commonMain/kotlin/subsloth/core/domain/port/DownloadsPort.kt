package subsloth.core.domain.port

import kotlinx.collections.immutable.ImmutableList
import subsloth.core.model.download.DownloadState
import subsloth.core.model.download.EnqueueOutcome
import subsloth.core.model.download.OfflineAsset
import subsloth.core.model.download.TransferPreference
import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.identifier.LocalMediaIdentifier
import subsloth.core.model.identifier.Resolution
import subsloth.core.model.media.Media

/** Port for managing download state, offline assets, and download commands. */
interface DownloadsPort {
    /** Returns all download records across all states. */
    suspend fun listDownloads(): Result<ImmutableList<DownloadState>>

    /** Returns all offline assets available for playback. */
    suspend fun listOfflineAssets(): Result<ImmutableList<OfflineAsset>>

    /** Enqueues a new download for the given media at the requested resolution. */
    suspend fun enqueue(
        mediaId: Media.MediaId,
        requested: Resolution,
        requiredBytes: Long? = null,
        transferPreference: TransferPreference = TransferPreference.WifiOnly,
    ): Result<EnqueueOutcome>

    /** Enqueues a subtitle download for an existing offline asset. */
    suspend fun enqueueSubtitle(localId: LocalMediaIdentifier, language: LanguageCode): Result<SubtitleEnqueueOutcome>

    /** Pauses an active or queued download. */
    suspend fun pause(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>

    /** Resumes a paused download. */
    suspend fun resume(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>

    /** Cancels an in-progress download without removing local files. */
    suspend fun cancel(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>

    /** Removes a completed download and its local files from storage. */
    suspend fun remove(localId: LocalMediaIdentifier): Result<DownloadCommandOutcome>
}

/** Outcome of a download command (pause, resume, cancel, remove). */
sealed interface DownloadCommandOutcome {
    /** The command was successfully applied. */
    data object Applied : DownloadCommandOutcome

    /** The command was a no-op (e.g. pausing an already-paused download). */
    data object NoOp : DownloadCommandOutcome
}

/** Outcome of requesting a subtitle track download. */
sealed interface SubtitleEnqueueOutcome {
    /** Subtitle download was queued. */
    data object Queued : SubtitleEnqueueOutcome

    /** Subtitle is already available locally. */
    data object AlreadyAvailable : SubtitleEnqueueOutcome
}
