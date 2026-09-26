package net.subsloth.player

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PlayerControllerStateTest {

    @Test
    fun `starts visible`() {
        assertThat(PlayerControllerState().isVisible).isTrue()
    }

    @Test
    fun `starts hidden when requested`() {
        assertThat(PlayerControllerState(initiallyVisible = false).isVisible).isFalse()
    }

    @Test
    fun `hide and toggle flip full visibility`() {
        val state = PlayerControllerState()

        state.hide()
        assertThat(state.isVisible).isFalse()

        state.toggle()
        assertThat(state.isVisible).isTrue()

        state.toggle()
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `always-on requester keeps the chrome visible after hide`() {
        val state = PlayerControllerState()

        state.setAlwaysOn(requester = "picker", alwaysOn = true)
        state.hide()

        assertThat(state.alwaysOn).isTrue()
        assertThat(state.isVisible).isTrue()
    }

    @Test
    fun `releasing the last requester restores hiding`() {
        val state = PlayerControllerState()

        state.setAlwaysOn("picker", true)
        state.setAlwaysOn("picker", false)
        state.hide()

        assertThat(state.alwaysOn).isFalse()
        assertThat(state.isVisible).isFalse()
    }

    @Test
    fun `registering the same requester twice is idempotent`() {
        val state = PlayerControllerState()

        state.setAlwaysOn("picker", true)
        state.setAlwaysOn("picker", true)
        state.setAlwaysOn("picker", false)

        assertThat(state.alwaysOn).isFalse()
    }

    @Test
    fun `show restarts the auto-hide timer`() {
        val state = PlayerControllerState()
        val before = state.lastInteraction

        state.show()

        assertThat(state.lastInteraction).isEqualTo(before + 1)
        assertThat(state.isVisible).isTrue()
    }
}
