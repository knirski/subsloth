package net.subsloth.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Seek-slider interaction state: preview while dragging, commit once on
 * release, and a cancel path for gestures that move away from the slider.
 *
 * The state is pure and [cancelDistancePx] is injected, so the threshold
 * behaviour is unit-testable without Compose.
 */
@Stable
class PlayerSeekState(private val cancelDistancePx: Float) {
    /** Slider value (0..1000) being previewed, or null when not dragging. */
    var preview by mutableStateOf<Float?>(null)
        private set

    /** True while the pointer is far enough above the drag origin to cancel. */
    var isCancelling by mutableStateOf(false)
        private set

    private var dragStartY: Float? = null

    /** Records a slider position change during a drag. */
    fun onPreview(value: Float) {
        preview = value
    }

    /**
     * Records the pointer-down position that later moves are measured
     * against. Called before the slider reports any preview, so a gesture that
     * crosses the threshold and releases without another move still cancels.
     */
    fun onDragStart(y: Float) {
        dragStartY = y
        isCancelling = false
    }

    /**
     * Tracks the pointer's vertical position while dragging. Moving
     * [cancelDistancePx] above the drag origin arms cancellation; moving back
     * disarms it again.
     */
    fun onDragPosition(y: Float) {
        val startY = dragStartY
        if (startY == null) {
            dragStartY = y
            return
        }
        isCancelling = y - startY <= -cancelDistancePx
    }

    /**
     * Ends the drag: returns the value to commit, or null when the gesture was
     * cancelled or no drag is in progress.
     */
    fun commit(): Float? {
        val value = preview?.takeUnless { isCancelling }
        reset()
        return value
    }

    /** Ends the drag without committing. */
    fun cancel() {
        reset()
    }

    private fun reset() {
        preview = null
        isCancelling = false
        dragStartY = null
    }
}
