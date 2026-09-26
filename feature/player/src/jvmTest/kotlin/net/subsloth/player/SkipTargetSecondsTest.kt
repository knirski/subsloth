package net.subsloth.player

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class SkipTargetSecondsTest {

    @Test
    fun `forward skip moves ten seconds ahead`() {
        val target = skipTargetSeconds(positionSeconds = 30.0, durationSeconds = 120.0, deltaSeconds = 10L)

        assertThat(target).isEqualTo(40.0)
    }

    @Test
    fun `backward skip moves ten seconds back`() {
        val target = skipTargetSeconds(positionSeconds = 30.0, durationSeconds = 120.0, deltaSeconds = -10L)

        assertThat(target).isEqualTo(20.0)
    }

    @Test
    fun `forward skip clamps to the duration`() {
        val target = skipTargetSeconds(positionSeconds = 115.0, durationSeconds = 120.0, deltaSeconds = 10L)

        assertThat(target).isEqualTo(120.0)
    }

    @Test
    fun `backward skip clamps to zero`() {
        val target = skipTargetSeconds(positionSeconds = 4.0, durationSeconds = 120.0, deltaSeconds = -10L)

        assertThat(target).isEqualTo(0.0)
    }

    @Test
    fun `unknown duration has no target`() {
        val target = skipTargetSeconds(positionSeconds = 30.0, durationSeconds = 0.0, deltaSeconds = 10L)

        assertThat(target).isNull()
    }

    @Test
    fun `NaN duration has no target`() {
        val target = skipTargetSeconds(positionSeconds = 30.0, durationSeconds = Double.NaN, deltaSeconds = 10L)

        assertThat(target).isNull()
    }

    @Test
    fun `NaN position has no target`() {
        val target = skipTargetSeconds(positionSeconds = Double.NaN, durationSeconds = 120.0, deltaSeconds = 10L)

        assertThat(target).isNull()
    }

    @Test
    fun `infinite duration has no target`() {
        val target =
            skipTargetSeconds(positionSeconds = 30.0, durationSeconds = Double.POSITIVE_INFINITY, deltaSeconds = 10L)

        assertThat(target).isNull()
    }
}
