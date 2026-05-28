package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.media.QualityDescriptor

private const val RESERVE_CAP_BYTES = 2L * 1024 * 1024 * 1024

object DownloadPolicy {
    /** Reserve 10 % of total space, capped at 2 GiB. */
    @Suppress("MagicNumber")
    fun requiredReserveBytes(totalBytes: Long): Long = minOf(RESERVE_CAP_BYTES, totalBytes / 10)

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
