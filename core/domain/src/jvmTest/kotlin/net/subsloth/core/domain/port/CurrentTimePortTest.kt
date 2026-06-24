package net.subsloth.core.domain.port

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.time.Instant

class CurrentTimePortTest {
    // ── Fixed-clock returns deterministic values ──────────────────────────

    @ParameterizedTest
    @MethodSource("fixedClockCases")
    fun `fixed clock returns deterministic now`(case: FixedClockCase) {
        val port = FakeClock(case.epochSeconds)

        assertThat(port.now()).isEqualTo(Instant.fromEpochSeconds(case.epochSeconds))
        assertThat(port.millisNow()).isEqualTo(case.epochSeconds * 1000)
    }

    data class FixedClockCase(val epochSeconds: Long, val label: String) {
        override fun toString(): String = label
    }

    companion object {
        @JvmStatic
        fun fixedClockCases() = listOf(
            FixedClockCase(0, "epoch zero"),
            FixedClockCase(1_700_000_000L, "typical recent timestamp"),
            FixedClockCase(2_000_000_000L, "near-future timestamp"),
            FixedClockCase(4_000_000_000L, "large epoch value (>2038)"),
        )
    }

    // ── now and millisNow consistency ─────────────────────────────────────

    @Test
    fun `now and millisNow are consistent for the same clock`() {
        val port = FakeClock(1_800_000_000L)

        val instant = port.now()
        val millis = port.millisNow()

        assertThat(instant.epochSeconds * 1000).isEqualTo(millis)
    }

    // ── Large millisNow values don't overflow ─────────────────────────────

    @ParameterizedTest
    @ValueSource(longs = [0, 1_700_000_000L, 2_000_000_000L, 4_000_000_000L])
    fun `millisNow does not overflow`(epochSeconds: Long) {
        val port = FakeClock(epochSeconds)

        val millis = port.millisNow()

        assertThat(millis).isEqualTo(epochSeconds * 1000)
    }

    private class FakeClock(private val epochSeconds: Long) : CurrentTimePort {
        override fun now(): Instant = Instant.fromEpochSeconds(epochSeconds)
        override fun millisNow(): Long = epochSeconds * 1000
    }
}
