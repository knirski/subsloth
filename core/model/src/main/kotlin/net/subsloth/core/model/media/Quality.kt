package net.subsloth.core.model.media

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.identifier.Resolution

/**
 * Stable quality metadata without ephemeral session URLs.
 *
 * This type is safe to persist in storage records such as [DownloadState]
 * because it contains no stream or download URLs. Use [Quality] when you
 * need the full model including ephemeral URLs for active playback or
 * download sessions.
 */
@Immutable
data class QualityDescriptor(
    val resolution: Resolution,
    val label: String?,
    val bitrate: Int?,
    val mimeType: String?,
)

/**
 * A playable quality/variant available for a media item.
 *
 * [url] and [downloadUrl] are ephemeral values tied to an active playback
 * or download session. They must not be persisted in storage records.
 * For persistent contexts, use [QualityDescriptor] instead.
 *
 * @property info stable metadata shared with persistent records.
 */
@Immutable
data class Quality(
    val info: QualityDescriptor,
    /** Ephemeral stream URL — must not be persisted. */
    val url: String?,
    /** Ephemeral download URL — must not be persisted. */
    val downloadUrl: String?,
)
