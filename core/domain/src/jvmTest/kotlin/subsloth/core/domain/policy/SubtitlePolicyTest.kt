package subsloth.core.domain.policy

import org.junit.jupiter.api.Test
import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.media.Subtitle
import subsloth.core.model.media.SubtitleFormat
import subsloth.testing.assertions.assertThat

class SubtitlePolicyTest {
    // ── Default subtitle selection ────────────────────────────────────────

    @Test
    fun `default subtitle is enabled English`() {
        val subtitles =
            listOf(
                subtitle(LanguageCode("en"), "English"),
                subtitle(LanguageCode("fr"), "French"),
                subtitle(LanguageCode("de"), "German"),
            )

        val result = SubtitlePolicy.selectDefault(subtitles)

        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("en"))
    }

    @Test
    fun `default picks first available when English is absent`() {
        val subtitles =
            listOf(
                subtitle(LanguageCode("fr"), "French"),
                subtitle(LanguageCode("de"), "German"),
            )

        val result = SubtitlePolicy.selectDefault(subtitles)

        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("fr"))
    }

    @Test
    fun `default returns null for empty list`() {
        val result = SubtitlePolicy.selectDefault(emptyList())

        assertThat(result).isNull()
    }

    // ── Subtitle disabled state ─────────────────────────────────────────────

    @Test
    fun `subtitles disabled means none selected`() {
        val all =
            listOf(
                subtitle(LanguageCode("en"), "English"),
            )

        // Subtitle disabled is a policy choice, not an absence of tracks.
        assertThat(SubtitlePolicy.selectDisabled()).isNull()
    }

    // ── Subtitle fallback ───────────────────────────────────────────────────

    @Test
    fun `subtitle fallback prefers same language`() {
        val subtitles =
            listOf(
                subtitle(LanguageCode("en"), "English"),
                subtitle(LanguageCode("fr"), "French"),
            )

        val result = SubtitlePolicy.fallback(subtitles, LanguageCode("en"))

        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("en"))
    }

    @Test
    fun `subtitle fallback returns first when preferred language absent`() {
        val subtitles =
            listOf(
                subtitle(LanguageCode("fr"), "French"),
                subtitle(LanguageCode("de"), "German"),
            )

        val result = SubtitlePolicy.fallback(subtitles, LanguageCode("en"))

        assertThat(result).isNotNull()
        assertThat(result!!.language).isEqualTo(LanguageCode("fr"))
    }

    @Test
    fun `subtitle fallback returns null for empty list`() {
        val result = SubtitlePolicy.fallback(emptyList(), LanguageCode("en"))

        assertThat(result).isNull()
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun subtitle(language: LanguageCode, displayName: String): Subtitle = Subtitle(
        language = language,
        languageDisplayName = displayName,
        url = null,
        downloadUrl = null,
        format = SubtitleFormat.SRT,
    )
}
