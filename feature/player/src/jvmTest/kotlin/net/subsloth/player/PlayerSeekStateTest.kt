package net.subsloth.player

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PlayerSeekStateTest {

    private val state = PlayerSeekState(cancelDistancePx = 100f)

    @Test
    fun `commit returns the previewed value`() {
        state.onPreview(400f)

        assertThat(state.commit()).isEqualTo(400f)
        assertThat(state.preview).isNull()
    }

    @Test
    fun `no drag commits nothing`() {
        assertThat(state.commit()).isNull()
    }

    @Test
    fun `moving above the threshold cancels the seek`() {
        state.onPreview(400f)
        state.onDragStart(500f)
        state.onDragPosition(350f)

        assertThat(state.isCancelling).isTrue()
        assertThat(state.commit()).isNull()
    }

    @Test
    fun `a single move above the threshold cancels`() {
        state.onPreview(400f)
        state.onDragStart(500f)
        state.onDragPosition(300f)

        assertThat(state.isCancelling).isTrue()
        assertThat(state.commit()).isNull()
    }

    @Test
    fun `moving back below the threshold commits again`() {
        state.onPreview(400f)
        state.onDragStart(500f)
        state.onDragPosition(350f)
        state.onDragPosition(500f)

        assertThat(state.isCancelling).isFalse()
        assertThat(state.commit()).isEqualTo(400f)
    }

    @Test
    fun `cancel clears the preview`() {
        state.onPreview(400f)

        state.cancel()

        assertThat(state.preview).isNull()
        assertThat(state.commit()).isNull()
    }
}
