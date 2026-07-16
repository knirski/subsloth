package net.subsloth.core.media

import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class SubtitleMapperTest {
    @Test
    fun `maps SRT subtitle with all fields`() {
        val subtitle = Subtitle(
            language = LanguageCode("en"),
            languageDisplayName = "English",
            url = "https://example.com/sub.srt",
            downloadUrl = "https://example.com/sub.srt",
            format = SubtitleFormat.SRT,
        )
        val track = SubtitleMapper.toSubtitleTrack(subtitle)
        assertThat(track?.label).isEqualTo("English")
        assertThat(track?.language).isEqualTo("en")
        assertThat(track?.src).isEqualTo("https://example.com/sub.srt")
    }

    @Test
    fun `maps VTT subtitle with language code fallback`() {
        val subtitle = Subtitle(
            language = LanguageCode("fr"),
            languageDisplayName = null,
            url = "https://example.com/sub.vtt",
            downloadUrl = null,
            format = SubtitleFormat.VTT,
        )
        val track = SubtitleMapper.toSubtitleTrack(subtitle)
        assertThat(track?.label).isEqualTo("fr")
        assertThat(track?.language).isEqualTo("fr")
        assertThat(track?.src).isEqualTo("https://example.com/sub.vtt")
    }

    @Test
    fun `returns null for ASS format`() {
        val subtitle = Subtitle(
            language = LanguageCode("en"),
            languageDisplayName = "English",
            url = "https://example.com/sub.ass",
            downloadUrl = null,
            format = SubtitleFormat.ASS,
        )
        assertThat(SubtitleMapper.toSubtitleTrack(subtitle)).isNull()
    }

    @Test
    fun `returns null for SSA format`() {
        val subtitle = Subtitle(
            language = LanguageCode("en"),
            languageDisplayName = "English",
            url = "https://example.com/sub.ssa",
            downloadUrl = null,
            format = SubtitleFormat.SSA,
        )
        assertThat(SubtitleMapper.toSubtitleTrack(subtitle)).isNull()
    }

    @Test
    fun `returns null when url is null`() {
        val subtitle = Subtitle(
            language = LanguageCode("en"),
            languageDisplayName = null,
            url = null,
            downloadUrl = null,
            format = SubtitleFormat.VTT,
        )
        assertThat(SubtitleMapper.toSubtitleTrack(subtitle)).isNull()
    }

    @Test
    fun `isFormatSupported returns true for SRT`() {
        assertThat(SubtitleMapper.isFormatSupported(SubtitleFormat.SRT)).isTrue()
    }

    @Test
    fun `isFormatSupported returns true for VTT`() {
        assertThat(SubtitleMapper.isFormatSupported(SubtitleFormat.VTT)).isTrue()
    }

    @Test
    fun `isFormatSupported returns true for UNKNOWN`() {
        assertThat(SubtitleMapper.isFormatSupported(SubtitleFormat.UNKNOWN)).isTrue()
    }

    @Test
    fun `isFormatSupported returns false for ASS`() {
        assertThat(SubtitleMapper.isFormatSupported(SubtitleFormat.ASS)).isFalse()
    }

    @Test
    fun `isFormatSupported returns false for SSA`() {
        assertThat(SubtitleMapper.isFormatSupported(SubtitleFormat.SSA)).isFalse()
    }
}
