package net.subsloth.player

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PlaybackSpeedLabelTest {

    @Test
    fun `whole speeds drop the decimal`() {
        assertThat(formatSpeedLabel(1f)).isEqualTo("1x")
        assertThat(formatSpeedLabel(2f)).isEqualTo("2x")
    }

    @Test
    fun `fractional speeds keep their value`() {
        assertThat(formatSpeedLabel(1.5f)).isEqualTo("1.5x")
        assertThat(formatSpeedLabel(0.5f)).isEqualTo("0.5x")
    }
}
