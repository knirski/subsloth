package net.subsloth.core.media.download

import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution

internal fun parseLocalIdDownloadId(localId: LocalMediaIdentifier): Long? {
    val parts = localId.value.split("/")
    return parts.lastOrNull()?.toLongOrNull()
}

internal fun parseResolution(label: String?): Resolution = when {
    label == null -> Resolution.HD_720
    label.contains("4K") || label.contains("2160") || label.contains("UHD") -> Resolution.UHD_4K
    label.contains("1440") || label.contains("QHD") -> Resolution.QHD
    label.contains("1080") || label.contains("FHD") || label.contains("full", ignoreCase = true) -> Resolution.FULL_HD
    label.contains("720") || label.contains("HD") -> Resolution.HD_720
    else -> Resolution.HD_720
}
