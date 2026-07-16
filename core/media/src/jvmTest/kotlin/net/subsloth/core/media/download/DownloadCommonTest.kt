package net.subsloth.core.media.download

import net.subsloth.core.model.identifier.LocalMediaIdentifier
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class DownloadCommonTest {

    // ── parseLocalIdDownloadId ───────────────────────────────────────────

    @Test
    fun `parseLocalIdDownloadId extracts numeric id from valid localId`() {
        val id = parseLocalIdDownloadId(LocalMediaIdentifier("movie123/456"))
        assertThat(id).isEqualTo(456L)
    }

    @Test
    fun `parseLocalIdDownloadId returns null for malformed localId`() {
        val id = parseLocalIdDownloadId(LocalMediaIdentifier("no-slash"))
        assertThat(id).isNull()
    }

    @Test
    fun `parseLocalIdDownloadId returns null for non-numeric suffix`() {
        val id = parseLocalIdDownloadId(LocalMediaIdentifier("abc/xyz"))
        assertThat(id).isNull()
    }

    @Test
    fun `parseLocalIdDownloadId handles multiple slashes`() {
        val id = parseLocalIdDownloadId(LocalMediaIdentifier("a/b/42"))
        assertThat(id).isEqualTo(42L)
    }

    // ── parseResolution ─────────────────────────────────────────────────

    @Test
    fun `parseResolution returns HD_720 for null label`() {
        assertThat(parseResolution(null)).isEqualTo(Resolution.HD_720)
    }

    @Test
    fun `parseResolution returns UHD_4K for 4K label`() {
        assertThat(parseResolution("4K")).isEqualTo(Resolution.UHD_4K)
    }

    @Test
    fun `parseResolution returns UHD_4K for 2160 label`() {
        assertThat(parseResolution("2160p")).isEqualTo(Resolution.UHD_4K)
    }

    @Test
    fun `parseResolution returns FULL_HD for 1080 label`() {
        assertThat(parseResolution("1080p")).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `parseResolution returns FULL_HD for FHD label`() {
        assertThat(parseResolution("FHD")).isEqualTo(Resolution.FULL_HD)
    }

    @Test
    fun `parseResolution returns HD_720 for 720 label`() {
        assertThat(parseResolution("720p")).isEqualTo(Resolution.HD_720)
    }

    @Test
    fun `parseResolution returns HD_720 for unknown label`() {
        assertThat(parseResolution("144p")).isEqualTo(Resolution.HD_720)
    }
}
