package net.subsloth.core.model.download

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SeasonDownloadConfirmationInvariantsTest {
    @Test
    fun `accepts valid confirmation with zero counts`() {
        val c = SeasonDownloadConfirmation(
            episodeCount = 0,
            alreadyAvailableCount = 0,
            fallbackQualityCount = 0,
            fallbackSubtitleToEnglishCount = 0,
            noSubtitleCount = 0,
            unavailableCount = 0,
            sizeEstimate = SizeEstimate.Unknown,
            transferPreference = TransferPreference.WifiOnly,
        )
        assertThat(c.episodeCount).isEqualTo(0)
    }

    @Test
    fun `accepts valid confirmation with non-zero counts`() {
        val c = SeasonDownloadConfirmation(
            episodeCount = 10,
            alreadyAvailableCount = 2,
            fallbackQualityCount = 1,
            fallbackSubtitleToEnglishCount = 1,
            noSubtitleCount = 0,
            unavailableCount = 1,
            sizeEstimate = SizeEstimate.Known(5_000_000_000),
            transferPreference = TransferPreference.MeteredAllowed,
        )
        assertThat(c.episodeCount).isEqualTo(10)
        assertThat(c.sizeEstimate).isInstanceOf(SizeEstimate.Known::class.java)
    }

    @Test
    fun `rejects negative episodeCount`() {
        assertThrows<IllegalArgumentException> { confirmation(episodeCount = -1) }
    }

    @Test
    fun `rejects negative alreadyAvailableCount`() {
        assertThrows<IllegalArgumentException> { confirmation(alreadyAvailableCount = -1) }
    }

    @Test
    fun `rejects negative fallbackQualityCount`() {
        assertThrows<IllegalArgumentException> { confirmation(fallbackQualityCount = -1) }
    }

    @Test
    fun `rejects negative fallbackSubtitleToEnglishCount`() {
        assertThrows<IllegalArgumentException> { confirmation(fallbackSubtitleToEnglishCount = -1) }
    }

    @Test
    fun `rejects negative noSubtitleCount`() {
        assertThrows<IllegalArgumentException> { confirmation(noSubtitleCount = -1) }
    }

    @Test
    fun `rejects negative unavailableCount`() {
        assertThrows<IllegalArgumentException> { confirmation(unavailableCount = -1) }
    }

    @Test
    fun `rejects alreadyAvailableCount exceeding episodeCount`() {
        assertThrows<IllegalArgumentException> { confirmation(episodeCount = 5, alreadyAvailableCount = 6) }
    }

    @Test
    fun `rejects fallbackQualityCount exceeding episodeCount`() {
        assertThrows<IllegalArgumentException> { confirmation(episodeCount = 3, fallbackQualityCount = 4) }
    }

    @Test
    fun `rejects fallbackSubtitleToEnglishCount exceeding episodeCount`() {
        assertThrows<IllegalArgumentException> { confirmation(episodeCount = 2, fallbackSubtitleToEnglishCount = 3) }
    }

    @Test
    fun `rejects noSubtitleCount exceeding episodeCount`() {
        assertThrows<IllegalArgumentException> { confirmation(episodeCount = 1, noSubtitleCount = 2) }
    }

    @Test
    fun `rejects unavailableCount exceeding episodeCount`() {
        assertThrows<IllegalArgumentException> { confirmation(episodeCount = 5, unavailableCount = 10) }
    }

    private fun confirmation(
        episodeCount: Int = 0,
        alreadyAvailableCount: Int = 0,
        fallbackQualityCount: Int = 0,
        fallbackSubtitleToEnglishCount: Int = 0,
        noSubtitleCount: Int = 0,
        unavailableCount: Int = 0,
    ) = SeasonDownloadConfirmation(
        episodeCount = episodeCount,
        alreadyAvailableCount = alreadyAvailableCount,
        fallbackQualityCount = fallbackQualityCount,
        fallbackSubtitleToEnglishCount = fallbackSubtitleToEnglishCount,
        noSubtitleCount = noSubtitleCount,
        unavailableCount = unavailableCount,
        sizeEstimate = SizeEstimate.Unknown,
        transferPreference = TransferPreference.WifiOnly,
    )
}
