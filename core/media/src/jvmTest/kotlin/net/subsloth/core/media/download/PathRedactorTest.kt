package net.subsloth.core.media.download

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PathRedactorTest {
    @Test
    fun `redacts absolute path`() {
        assertThat(PathRedactor.redact("/data/data/com.app/files/downloads/video.mp4"))
            .isEqualTo("[redacted-local-path]")
    }

    @Test
    fun `returns relative path unchanged`() {
        assertThat(PathRedactor.redact("downloads/video.mp4")).isEqualTo("downloads/video.mp4")
    }

    @Test
    fun `returns empty string for null`() {
        assertThat(PathRedactor.redact(null)).isEqualTo("")
    }

    @Test
    fun `returns empty string for blank path`() {
        assertThat(PathRedactor.redact("  ")).isEqualTo("")
    }

    @Test
    fun `returns empty string for empty path`() {
        assertThat(PathRedactor.redact("")).isEqualTo("")
    }

    @Test
    fun `redacts single slash`() {
        assertThat(PathRedactor.redact("/")).isEqualTo("[redacted-local-path]")
    }

    @Test
    fun `redacts deep absolute path`() {
        assertThat(PathRedactor.redact("/storage/emulated/0/Android/data/net.subsloth/files/downloads/subtitle.srt"))
            .isEqualTo("[redacted-local-path]")
    }
}
