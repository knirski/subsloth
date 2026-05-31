package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.SubtitleSelection
import net.subsloth.core.model.download.TransferPreference
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

    // ── Queue resume ─────────────────────────────────────────────────────

    @Test
    fun `queue resumes when all conditions are met`() {
        assertThat(
            SeasonQueuePolicy.canResumeQueue(
                isOnline = true,
                hasStorage = true,
                transferPreference = TransferPreference.WifiOnly,
                isMetered = false,
                authValid = true,
            ),
        ).isTrue()
    }

    @Test
    fun `queue does not resume when device is offline`() {
        assertThat(
            SeasonQueuePolicy.canResumeQueue(
                isOnline = false,
                hasStorage = true,
                transferPreference = TransferPreference.WifiOnly,
                isMetered = false,
                authValid = true,
            ),
        ).isFalse()
    }

    @Test
    fun `queue does not resume when storage is insufficient`() {
        assertThat(
            SeasonQueuePolicy.canResumeQueue(
                isOnline = true,
                hasStorage = false,
                transferPreference = TransferPreference.WifiOnly,
                isMetered = false,
                authValid = true,
            ),
        ).isFalse()
    }

    @Test
    fun `queue does not resume when auth is invalid`() {
        assertThat(
            SeasonQueuePolicy.canResumeQueue(
                isOnline = true,
                hasStorage = true,
                transferPreference = TransferPreference.WifiOnly,
                isMetered = false,
                authValid = false,
            ),
        ).isFalse()
    }

    @Test
    fun `queue does not resume on metered network with wifi only preference`() {
        assertThat(
            SeasonQueuePolicy.canResumeQueue(
                isOnline = true,
                hasStorage = true,
                transferPreference = TransferPreference.WifiOnly,
                isMetered = true,
                authValid = true,
            ),
        ).isFalse()
    }

    @Test
    fun `queue resumes on metered network when metered allowed`() {
        assertThat(
            SeasonQueuePolicy.canResumeQueue(
                isOnline = true,
                hasStorage = true,
                transferPreference = TransferPreference.MeteredAllowed,
                isMetered = true,
                authValid = true,
            ),
        ).isTrue()
    }

    private fun subtitle(language: LanguageCode, displayName: String): Subtitle = Subtitle(
        language = language,
        languageDisplayName = displayName,
        url = null,
        downloadUrl = null,
        format = SubtitleFormat.SRT,
    )
}
