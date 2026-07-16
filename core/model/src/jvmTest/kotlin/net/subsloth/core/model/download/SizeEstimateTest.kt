package net.subsloth.core.model.download

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SizeEstimateTest {
    @Test
    fun `Known accepts zero bytes`() {
        val s = SizeEstimate.Known(0L)
        assertThat(s.bytes).isEqualTo(0L)
    }

    @Test
    fun `Known accepts positive bytes`() {
        val s = SizeEstimate.Known(1024L)
        assertThat(s.bytes).isEqualTo(1024L)
    }

    @Test
    fun `Known rejects negative bytes`() {
        assertThrows<IllegalArgumentException> { SizeEstimate.Known(-1L) }
    }

    @Test
    fun `Unknown is singleton`() {
        assertThat(SizeEstimate.Unknown).isEqualTo(SizeEstimate.Unknown)
    }
}
