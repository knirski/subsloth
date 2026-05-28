package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.SubtitleSelection
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class SeasonQueuePolicyTest {
    @Test
    fun `initial subtitle fallback uses preferred non english then english then none`() {
        val subtitles =
            listOf(
                subtitle(LanguageCode("en"), "English"),
                subtitle(LanguageCode("pl"), "Polski"),
            )
        val selection =
            SeasonQueuePolicy.selectInitialSubtitle(
                available = subtitles,
                preferred = LanguageCode("pl"),
            )
        assertThat(selection)
            .isEqualTo(
                SubtitleSelection.Preferred(
                    subtitle(LanguageCode("pl"), "Polski"),
                ),
            )
    }

    @Test
    fun `preferred english returns preferred not english fallback`() {
        val subtitles = listOf(subtitle(LanguageCode("en"), "English"))
        val selection =
            SeasonQueuePolicy.selectInitialSubtitle(
                available = subtitles,
                preferred = LanguageCode("en"),
            )
        assertThat(selection)
            .isEqualTo(
                SubtitleSelection.Preferred(
                    subtitle(LanguageCode("en"), "English"),
                ),
            )
    }

    @Test
    fun `subtitle fallback emits explicit english fallback decision`() {
        val subtitles = listOf(subtitle(LanguageCode("en"), "English"))
        val selection =
            SeasonQueuePolicy.selectInitialSubtitle(
                available = subtitles,
                preferred = LanguageCode("es"),
            )
        assertThat(selection)
            .isEqualTo(
                SubtitleSelection.EnglishFallback(
                    subtitle(LanguageCode("en"), "English"),
                ),
            )
    }

    private fun subtitle(
        language: LanguageCode,
        displayName: String,
    ): Subtitle =
        Subtitle(
            language = language,
            languageDisplayName = displayName,
            url = null,
            downloadUrl = null,
            format = SubtitleFormat.SRT,
        )
}
