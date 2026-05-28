package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.media.QualityDescriptor
private const val RESERVE_CAP_BYTES = 2L * 1024 * 1024 * 1024
private const val RESERVE_FRACTION_DENOMINATOR = 10

object DownloadPolicy {
    fun requiredReserveBytes(totalBytes: Long): Long =
        minOf(RESERVE_CAP_BYTES, totalBytes / RESERVE_FRACTION_DENOMINATOR)

    fun canTransferOnNetwork(
        isMetered: Boolean,
        transferPreference: TransferPreference,
    ): Boolean =
        when (transferPreference) {
            TransferPreference.WifiOnly -> !isMetered
            TransferPreference.MeteredAllowed -> true
        }

    fun canReplaceQuality(
        existing: QualityDescriptor,
        candidate: QualityDescriptor,
    ): Boolean = candidate.resolution.pixelCount > existing.resolution.pixelCount

    fun hasSufficientStorage(
        availableBytes: Long,
        requiredBytes: Long,
        reserveBytes: Long,
    ): Boolean = availableBytes >= requiredBytes + reserveBytes
}
