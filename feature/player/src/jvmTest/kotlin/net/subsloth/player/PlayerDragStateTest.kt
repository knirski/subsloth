package net.subsloth.player

import androidx.compose.ui.geometry.Offset
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PlayerDragStateTest {

    private val state = PlayerDragState(cancelDistancePx = 100f)

    @Test
    fun `horizontal drag scrubs and commits a delta`() {
        start(volume = 0.5f)
        state.drag(Offset(200f, 0f))

        assertThat(state.mode).isEqualTo(PlayerDragMode.Scrub)
        assertThat(state.seekDeltaSeconds).isEqualTo(18L)
        assertThat(state.end()).isEqualTo(18L)
        assertThat(state.isActive).isFalse()
    }

    @Test
    fun `vertical drag on the right third adjusts volume`() {
        start(x = 900f, volume = 0.5f)
        val value = state.drag(Offset(0f, -100f))

        assertThat(state.mode).isEqualTo(PlayerDragMode.Value(PlayerSwipeValue.Volume))
        assertThat(value!!).isWithin(0.001f).of(0.7f)
        assertThat(state.end()).isNull()
    }

    @Test
    fun `vertical drag on the left third adjusts brightness`() {
        start(x = 100f, brightness = 0.5f)
        val value = state.drag(Offset(0f, 100f))

        assertThat(state.mode).isEqualTo(PlayerDragMode.Value(PlayerSwipeValue.Brightness))
        assertThat(value!!).isWithin(0.001f).of(0.3f)
    }

    @Test
    fun `vertical drag in the middle is ignored`() {
        start(x = 500f)
        val value = state.drag(Offset(0f, -100f))

        assertThat(state.mode).isEqualTo(PlayerDragMode.Ignored)
        assertThat(value).isNull()
        assertThat(state.end()).isNull()
    }

    @Test
    fun `value is clamped to zero and one`() {
        start(x = 900f, volume = 0.9f)

        state.drag(Offset(0f, -500f))
        assertThat(state.value).isEqualTo(1f)

        state.drag(Offset(0f, 2000f))
        assertThat(state.value).isEqualTo(0f)
    }

    @Test
    fun `scrub is cancelled by moving above the threshold`() {
        start()
        state.drag(Offset(100f, 0f))
        state.drag(Offset(0f, -150f))

        assertThat(state.isCancelling).isTrue()
        assertThat(state.end()).isNull()
    }

    @Test
    fun `moving back down disarms the scrub cancel`() {
        start()
        state.drag(Offset(100f, 0f))
        state.drag(Offset(0f, -150f))
        state.drag(Offset(0f, 100f))

        assertThat(state.isCancelling).isFalse()
        assertThat(state.end()).isEqualTo(9L)
    }

    @Test
    fun `cancel discards the drag`() {
        start()
        state.drag(Offset(200f, 0f))

        state.cancel()

        assertThat(state.isActive).isFalse()
        assertThat(state.end()).isNull()
    }

    private fun start(x: Float = 500f, volume: Float = 0.5f, brightness: Float = 0.5f) {
        state.start(
            offset = Offset(x, 250f),
            widthPx = 1000f,
            heightPx = 500f,
            volume = volume,
            brightness = brightness,
        )
    }
}

class PlayerGestureTargetsTest {

    @Test
    fun `double-tap thirds choose skip or playback`() {
        assertThat(skipDeltaForTap(x = 100f, width = 1000f)).isEqualTo(-SKIP_SECONDS)
        assertThat(skipDeltaForTap(x = 500f, width = 1000f)).isNull()
        assertThat(skipDeltaForTap(x = 900f, width = 1000f)).isEqualTo(SKIP_SECONDS)
    }

    @Test
    fun `double-tap is ignored without a width`() {
        assertThat(skipDeltaForTap(x = 100f, width = 0f)).isNull()
    }

    @Test
    fun `vertical swipe thirds choose brightness or volume`() {
        assertThat(swipeValueForX(100f, 1000f)).isEqualTo(PlayerSwipeValue.Brightness)
        assertThat(swipeValueForX(500f, 1000f)).isNull()
        assertThat(swipeValueForX(900f, 1000f)).isEqualTo(PlayerSwipeValue.Volume)
    }

    @Test
    fun `seek delta is formatted with its direction`() {
        assertThat(formatSeekDelta(75L)).isEqualTo("01:15 »")
        assertThat(formatSeekDelta(-75L)).isEqualTo("« 01:15")
    }
}

class PlayerTransientIndicatorStateTest {

    @Test
    fun `show sets the text and bumps the ticket`() {
        val state = PlayerTransientIndicatorState()

        state.show("-10s")
        val firstTicket = state.shownAt
        state.show("-10s")

        assertThat(state.text).isEqualTo("-10s")
        assertThat(state.shownAt).isEqualTo(firstTicket + 1)
    }

    @Test
    fun `clear removes the text`() {
        val state = PlayerTransientIndicatorState()
        state.show("2.5x")

        state.clear()

        assertThat(state.text).isNull()
    }
}
