package subsloth.core.model.download

/** Reasons why a download may fail, used by [DownloadState.Failed], [DownloadState.Paused], and [DownloadState.Unavailable]. */
sealed interface DownloadFailureReason {
    data object NeedsWifi : DownloadFailureReason

    data object InsufficientStorage : DownloadFailureReason

    data object MissingLocalFile : DownloadFailureReason

    data object SubtitleUnavailable : DownloadFailureReason

    data object AmbiguousQuality : DownloadFailureReason

    data object DownloadFailed : DownloadFailureReason

    data object Unavailable : DownloadFailureReason
}
