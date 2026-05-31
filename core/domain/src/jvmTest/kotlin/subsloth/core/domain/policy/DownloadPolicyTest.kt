package subsloth.core.domain.policy

import org.junit.jupiter.api.Test
import subsloth.core.model.download.TransferPreference
import subsloth.core.model.identifier.Resolution
import subsloth.core.model.media.QualityDescriptor
import subsloth.testing.assertions.assertThat

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
        val existing = qualityDescriptor(Resolution.HD_720, "720p")
        val candidate = qualityDescriptor(Resolution.FULL_HD, "1080p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isTrue()
    }

    @Test
    fun `same quality replacement is not allowed`() {
        val existing = qualityDescriptor(Resolution.FULL_HD, "1080p")
        val candidate = qualityDescriptor(Resolution.FULL_HD, "1080p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isFalse()
    }

    @Test
    fun `lower quality replacement is refused`() {
        val existing = qualityDescriptor(Resolution.FULL_HD, "1080p")
        val candidate = qualityDescriptor(Resolution.HD_720, "720p")

        assertThat(DownloadPolicy.canReplaceQuality(existing, candidate)).isFalse()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun qualityDescriptor(resolution: Resolution, label: String): QualityDescriptor = QualityDescriptor(
        resolution = resolution,
        label = label,
        bitrate = null,
        mimeType = null,
    )
}
