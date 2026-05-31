package subsloth.core.domain.policy

import org.junit.jupiter.api.Test
import subsloth.core.model.identifier.Resolution
import subsloth.core.model.media.Quality
import subsloth.core.model.media.QualityDescriptor
import subsloth.testing.assertions.assertThat

class QualityPolicyTest {
    // ── Default quality selection ─────────────────────────────────────────

    @Test
    fun `phone and tablet default quality caps at 1080p`() {
        val qualities =
            listOf(
                quality(Resolution.UHD_4K, "4K"),
                quality(Resolution.FULL_HD, "1080p"),
                quality(Resolution.HD_720, "720p"),
            )

        val result = QualityPolicy.selectDefault(qualities, isTvDevice = false)

        assertThat(result!!.info.resolution).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `phone default picks highest at or below 1080p`() {
        val qualities =
            listOf(
                quality(Resolution.FULL_HD, "1080p"),
                quality(Resolution.HD_720, "720p"),
                quality(Resolution.SD, "SD"),
            )

        val result = QualityPolicy.selectDefault(qualities, isTvDevice = false)

        assertThat(result!!.info.resolution).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `phone default falls back when no quality meets cap`() {
        val qualities =
            listOf(
                quality(Resolution.SD, "SD"),
            )

        val result = QualityPolicy.selectDefault(qualities, isTvDevice = false)

        assertThat(result!!.info.resolution).isEqualTo(Resolution.SD)
    }

    @Test
    fun `TV default quality selects the highest available`() {
        val qualities =
            listOf(
                quality(Resolution.UHD_4K, "4K"),
                quality(Resolution.FULL_HD, "1080p"),
                quality(Resolution.HD_720, "720p"),
            )

        val result = QualityPolicy.selectDefault(qualities, isTvDevice = true)

        assertThat(result!!.info.resolution).isEqualTo(Resolution.UHD_4K)
    }

    @Test
    fun `TV default picks only available quality`() {
        val qualities =
            listOf(
                quality(Resolution.FULL_HD, "1080p"),
            )

        val result = QualityPolicy.selectDefault(qualities, isTvDevice = true)

        assertThat(result!!.info.resolution).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `TV default with empty list returns null`() {
        val result = QualityPolicy.selectDefault(emptyList(), isTvDevice = true)

        assertThat(result).isNull()
    }

    @Test
    fun `phone default with empty list returns null`() {
        val result = QualityPolicy.selectDefault(emptyList(), isTvDevice = false)

        assertThat(result).isNull()
    }

    // ── Quality fallback ──────────────────────────────────────────────────

    @Test
    fun `quality fallback picks next available below requested`() {
        val qualities =
            listOf(
                // No exact UHD_4K match — fallback should pick next below
                quality(Resolution.FULL_HD, "1080p"),
                quality(Resolution.HD_720, "720p"),
                quality(Resolution.SD, "SD"),
            )

        val result = QualityPolicy.fallback(qualities, Resolution.UHD_4K)

        assertThat(result).isNotNull()
        assertThat(result!!.info.resolution).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `quality fallback returns same when exact match exists`() {
        val qualities =
            listOf(
                quality(Resolution.FULL_HD, "1080p"),
                quality(Resolution.HD_720, "720p"),
            )

        val result = QualityPolicy.fallback(qualities, Resolution.FULL_HD)

        assertThat(result).isNotNull()
        assertThat(result!!.info.resolution).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `quality fallback returns null when no lower quality`() {
        val qualities =
            listOf(
                quality(Resolution.SD, "SD"),
            )

        // Request a resolution lower than the lowest available.
        val lowRes = Resolution(320, 180)
        val result = QualityPolicy.fallback(qualities, lowRes)

        assertThat(result).isNull()
    }

    // ── Quality label normalization ───────────────────────────────────────

    @Test
    fun `normalizes common Media quality labels`() {
        assertThat(QualityPolicy.normalizeLabel("auto")).isEqualTo("Auto")
        assertThat(QualityPolicy.normalizeLabel("1080p")).isEqualTo("1080p")
        assertThat(QualityPolicy.normalizeLabel("720p")).isEqualTo("720p")
        assertThat(QualityPolicy.normalizeLabel("480p")).isEqualTo("480p")
        assertThat(QualityPolicy.normalizeLabel("360p")).isEqualTo("360p")
        assertThat(QualityPolicy.normalizeLabel("240p")).isEqualTo("240p")
    }

    @Test
    fun `normalizeLabel passes through unknown labels`() {
        assertThat(QualityPolicy.normalizeLabel("4K")).isEqualTo("4K")
        assertThat(QualityPolicy.normalizeLabel("1440p")).isEqualTo("1440p")
    }

    // ── In-player quality change scope ────────────────────────────────────

    @Test
    fun `manual quality change does not update account preference`() {
        val currentPreference = Resolution.FULL_HD
        val sessionQuality = Resolution.HD_720

        val updatedPreference =
            QualityPolicy.applySessionQualityChange(
                sessionQuality = sessionQuality,
                currentAccountPreference = currentPreference,
            )

        // The account preference should remain unchanged.
        assertThat(updatedPreference).isEqualTo(currentPreference)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun quality(resolution: Resolution, label: String): Quality = Quality(
        info =
        QualityDescriptor(
            resolution = resolution,
            label = label,
            bitrate = null,
            mimeType = null,
        ),
        url = null,
        downloadUrl = null,
    )
}
