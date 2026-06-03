package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

private const val GB = 1024 * 1024 * 1024

class DownloadPolicyTest {
    // ── Storage reserve ───────────────────────────────────────────────────

    @Test
    fun `reserve bytes uses smaller of two gigabytes and ten percent`() {
        assertThat(DownloadPolicy.requiredReserveBytes(totalBytes = 64L * GB)).isEqualTo(2L * GB)
        assertThat(DownloadPolicy.requiredReserveBytes(totalBytes = 8L * GB)).isEqualTo(8L * GB / 10)
    }

    @Test
    fun `download refused when free space is below minimum reserve`() {
        val result =
            DownloadPolicy.hasSufficientStorage(
                availableBytes = 100L,
                requiredBytes = 50L,
                reserveBytes = 200L,
            )
        assertThat(result).isFalse()
    }

    @Test
    fun `download allowed when free space exceeds reserve plus required`() {
        val result =
            DownloadPolicy.hasSufficientStorage(
                availableBytes = 500L,
                requiredBytes = 100L,
                reserveBytes = 200L,
            )
        assertThat(result).isTrue()
    }

    @Test
    fun `download refused when available meets reserve but not required`() {
        val result =
            DownloadPolicy.hasSufficientStorage(
                availableBytes = 250L,
                requiredBytes = 100L,
                reserveBytes = 200L,
            )
        assertThat(result).isFalse()
    }

    // ── Network transfer ──────────────────────────────────────────────────

    @Test
    fun `wifi only blocks metered network`() {
        assertThat(
            DownloadPolicy.canTransferOnNetwork(
                isMetered = true,
                transferPreference = TransferPreference.WifiOnly,
            ),
        ).isFalse()
    }

    @Test
    fun `wifi only allows non metered network`() {
        assertThat(
            DownloadPolicy.canTransferOnNetwork(
                isMetered = false,
                transferPreference = TransferPreference.WifiOnly,
            ),
        ).isTrue()
    }

    @Test
    fun `metered allowed works on any network`() {
        assertThat(
            DownloadPolicy.canTransferOnNetwork(
                isMetered = true,
                transferPreference = TransferPreference.MeteredAllowed,
            ),
        ).isTrue()
    }

    // ── Safe quality replacement ──────────────────────────────────────────

    @Test
    fun `higher quality replacement is allowed`() {
        val existing = qDesc(Resolution.HD_720, "720p")
        val candidate = qDesc(Resolution.FULL_HD, "1080p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isTrue()
    }

    @Test
    fun `same quality replacement is not allowed`() {
        val existing = qDesc(Resolution.FULL_HD, "1080p")
        val candidate = qDesc(Resolution.FULL_HD, "1080p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isFalse()
    }

    @Test
    fun `lower quality replacement is refused`() {
        val existing = qDesc(Resolution.FULL_HD, "1080p")
        val candidate = qDesc(Resolution.HD_720, "720p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isFalse()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    @Test
    fun `fallback quality uses exact match when available`() {
        val qualities = listOf(qDesc(Resolution.HD_720, "720p"), qDesc(Resolution.FULL_HD, "1080p"))
        assertThat(DownloadPolicy.selectFallbackQuality(qualities, Resolution.FULL_HD))
            .isEqualTo(qDesc(Resolution.FULL_HD, "1080p"))
    }

    @Test
    fun `fallback quality uses nearest lower when exact missing`() {
        val qualities = listOf(qDesc(Resolution.HD_720, "720p"), qDesc(Resolution.SD, "SD"))
        assertThat(DownloadPolicy.selectFallbackQuality(qualities, Resolution.FULL_HD))
            .isEqualTo(qDesc(Resolution.HD_720, "720p"))
    }

    @Test
    fun `fallback quality returns null on empty list`() {
        assertThat(DownloadPolicy.selectFallbackQuality(emptyList(), Resolution.FULL_HD)).isNull()
    }

    @Test
    fun `is duplicate detects completed download for same media`() {
        val existing = listOf(downloadCompleted())
        assertThat(DownloadPolicy.isDuplicate(existing, testMediaId)).isTrue()
    }

    @Test
    fun `is duplicate allows new download when none completed`() {
        val existing = listOf(downloadQueued())
        assertThat(DownloadPolicy.isDuplicate(existing, testMediaId)).isFalse()
    }

    @Test
    fun `can start new download when no active or queued exists`() {
        val existing = listOf(downloadCompleted())
        assertThat(DownloadPolicy.canStartNewDownload(existing)).isTrue()
    }

    @Test
    fun `cannot start new download when active exists`() {
        val existing = listOf(downloadActive())
        assertThat(DownloadPolicy.canStartNewDownload(existing)).isFalse()
    }

    @Test
    fun `has playable downloads when completed exists`() {
        assertThat(DownloadPolicy.hasPlayableDownloads(listOf(downloadCompleted()))).isTrue()
    }

    @Test
    fun `has playable downloads false when only queued`() {
        assertThat(DownloadPolicy.hasPlayableDownloads(listOf(downloadQueued()))).isFalse()
    }

    @Test
    fun `playable locally when completed and file exists`() {
        assertThat(
            DownloadPolicy.isPlayableLocally(downloadCompleted(), fileExists = true, fileNonEmpty = true),
        ).isTrue()
    }

    @Test
    fun `not playable locally when file missing`() {
        assertThat(
            DownloadPolicy.isPlayableLocally(downloadCompleted(), fileExists = false, fileNonEmpty = false),
        ).isFalse()
    }

    @Test
    fun `file integrity status playable`() {
        assertThat(DownloadPolicy.fileIntegrityStatus(fileExists = true, fileNonEmpty = true))
            .isEqualTo(FileStatus.PLAYABLE)
    }

    @Test
    fun `file integrity status missing`() {
        assertThat(DownloadPolicy.fileIntegrityStatus(fileExists = false, fileNonEmpty = false))
            .isEqualTo(FileStatus.MISSING)
    }

    @Test
    fun `file integrity status corrupt`() {
        assertThat(DownloadPolicy.fileIntegrityStatus(fileExists = true, fileNonEmpty = false))
            .isEqualTo(FileStatus.CORRUPT)
    }

    private fun qDesc(resolution: Resolution, label: String): QualityDescriptor = QualityDescriptor(
        resolution = resolution,
        label = label,
        bitrate = null,
        mimeType = null,
    )

    private val testMediaId = net.subsloth.core.model.media.Media.MediaId.Movie(
        net.subsloth.core.model.identifier.MovieId(1),
    )

    private val testLocalId = net.subsloth.core.model.identifier.LocalMediaIdentifier("test/1")

    private val testQuality = QualityDescriptor(
        resolution = Resolution.HD_720,
        label = "720p",
        bitrate = null,
        mimeType = null,
    )

    private fun downloadCompleted() = DownloadState.Completed(
        localId = testLocalId,
        mediaId = testMediaId,
        quality = testQuality,
        downloadedAtEpochSeconds = kotlin.time.Instant.fromEpochSeconds(1000),
        sizeBytes = 1024L,
        videoPath = net.subsloth.core.model.download.OfflineRelativePath.safe("test.mp4"),
    )

    private fun downloadQueued() = DownloadState.Queued(
        localId = testLocalId,
        mediaId = testMediaId,
        quality = testQuality,
    )

    private fun downloadActive() = DownloadState.Active(
        localId = testLocalId,
        mediaId = testMediaId,
        quality = testQuality,
        progressPercent = 50,
    )
}
