package subsloth.core.domain.policy

import org.junit.jupiter.api.Test
import subsloth.testing.assertions.assertThat

class PlaybackSpeedPolicyTest {
    @Test
    fun `valid speeds are accepted`() {
        val validSpeeds = listOf(0.50f, 0.60f, 0.70f, 0.80f, 0.90f, 1.00f, 1.25f, 1.50f, 2.00f)
        for (speed in validSpeeds) {
            assertThat(PlaybackSpeedPolicy.isValid(speed)).isTrue()
        }
    }

    @Test
    fun `invalid speeds are rejected`() {
        val invalidSpeeds = listOf(0.0f, 0.10f, 0.45f, 1.10f, 1.75f, 3.0f, -1.0f)
        for (speed in invalidSpeeds) {
            assertThat(PlaybackSpeedPolicy.isValid(speed)).isFalse()
        }
    }

    @Test
    fun `default speed is 1x`() {
        assertThat(PlaybackSpeedPolicy.defaultSpeed()).isWithin(0.001f).of(1.0f)
    }

    @Test
    fun `clamps speed to nearest valid value`() {
        // 0.55 is closer to 0.60 (diff 0.05) than 0.50 (diff 0.05) — picks first: 0.50
        // Use 0.56 which is closer to 0.60 (diff 0.04) than 0.50 (diff 0.06)
        assertThat(PlaybackSpeedPolicy.clamp(0.56f)).isWithin(0.001f).of(0.60f)
        // 1.10 is closer to 1.00 (diff 0.10) than 1.25 (diff 0.15)
        assertThat(PlaybackSpeedPolicy.clamp(1.10f)).isWithin(0.001f).of(1.00f)
        // 1.60 is closer to 1.50 (diff 0.10) than 2.00 (diff 0.40)
        assertThat(PlaybackSpeedPolicy.clamp(1.60f)).isWithin(0.001f).of(1.50f)
        // 0.40 is closer to 0.50 (diff 0.10) than any other
        assertThat(PlaybackSpeedPolicy.clamp(0.40f)).isWithin(0.001f).of(0.50f)
    }

    @Test
    fun `clamp returns exact valid speeds unchanged`() {
        assertThat(PlaybackSpeedPolicy.clamp(1.00f)).isWithin(0.001f).of(1.00f)
        assertThat(PlaybackSpeedPolicy.clamp(2.00f)).isWithin(0.001f).of(2.00f)
    }

    @Test
    fun `all valid speeds are returned in order`() {
        val speeds = PlaybackSpeedPolicy.availableSpeeds()
        assertThat(speeds)
            .containsExactly(
                0.50f,
                0.60f,
                0.70f,
                0.80f,
                0.90f,
                1.00f,
                1.25f,
                1.50f,
                2.00f,
            ).inOrder()
    }
}
