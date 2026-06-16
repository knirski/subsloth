package net.subsloth.core.model.download

/** Reasons why a download may fail, used by [DownloadState.Failed], [DownloadState.Paused], and [DownloadState.Unavailable]. */
enum class DownloadFailureReason {
    NeedsWifi,
    InsufficientStorage,
    MissingLocalFile,
    SubtitleUnavailable,
    AmbiguousQuality,
    DownloadFailed,
    Unavailable,
}
