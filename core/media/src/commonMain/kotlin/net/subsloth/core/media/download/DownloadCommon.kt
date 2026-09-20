package net.subsloth.core.media.download

import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution

internal fun parseLocalIdDownloadId(localId: LocalMediaIdentifier): Long? {
    val parts = localId.value.split("/")
    return parts.lastOrNull()?.toLongOrNull()
}

/**
 * Maps a quality label to a coarse [Resolution]; falls back to HD_720.
 *
 * `Resolution.label` produces "SD" for the SD tier, so the SD/360 labels must
 * round-trip: queue creation stores `Resolution.label`, and retries parse the
 * stored label back into a resolution.
 */
fun parseResolution(label: String?): Resolution = when {
    label == null -> Resolution.HD_720
    label.contains("4K") || label.contains("2160") || label.contains("UHD") -> Resolution.UHD_4K
    label.contains("1440") || label.contains("QHD") -> Resolution.QHD
    label.contains("1080") || label.contains("FHD") || label.contains("full", ignoreCase = true) -> Resolution.FULL_HD
    label.contains("720") || label.contains("HD") -> Resolution.HD_720
    label.contains("360") || label.contains("SD", ignoreCase = true) -> Resolution.SD
    else -> Resolution.HD_720
}
