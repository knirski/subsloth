package net.subsloth.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** A horizontal swipe across the full player width seeks this many seconds. */
internal const val SWIPE_SEEK_FULL_WIDTH_SECONDS = 90L

/** A vertical swipe across the full player height moves this many value steps. */
internal const val SWIPE_VALUE_STEPS = 40f

/** Playback speed while a touch long-press is held. */
internal const val TOUCH_FAST_FORWARD_SPEED = 2.5f

/** How long the transient gesture indicator stays on screen. */
internal const val GESTURE_INDICATOR_MS = 700L

/** Player value adjusted by a vertical swipe. */
enum class PlayerSwipeValue { Brightness, Volume }

/** What an in-flight drag is interpreted as, decided from its dominant direction. */
sealed interface PlayerDragMode {
    data object Undecided : PlayerDragMode
    data object Scrub : PlayerDragMode
    data class Value(val value: PlayerSwipeValue) : PlayerDragMode
    data object Ignored : PlayerDragMode
}

/**
 * Double-tap skip target: the left third rewinds, the right third fast-forwards,
 * and the middle third returns null so the caller can toggle playback instead.
 */
internal fun skipDeltaForTap(x: Float, width: Float): Long? = when {
    width <= 0f -> null
    x < width / 3f -> -SKIP_SECONDS
    x > width * 2f / 3f -> SKIP_SECONDS
    else -> null
}

/**
 * Vertical-swipe target: brightness on the left third, volume on the right
 * third, and null in the middle so vertical drags there are ignored.
 */
internal fun swipeValueForX(x: Float, width: Float): PlayerSwipeValue? = when {
    width <= 0f -> null
    x < width / 3f -> PlayerSwipeValue.Brightness
    x > width * 2f / 3f -> PlayerSwipeValue.Volume
    else -> null
}

/** Formats a scrub delta as `« 00:15` / `00:15 »`. */
internal fun formatSeekDelta(deltaSeconds: Long): String = if (deltaSeconds >= 0) {
    "${formatTime(deltaSeconds)} »"
} else {
    "« ${formatTime(-deltaSeconds)}"
}

/** Formats a skip delta for the transient indicator. */
internal fun formatSkipDelta(deltaSeconds: Long): String = formatSeekDelta(deltaSeconds)

/**
 * Routes a touch drag to the gesture it belongs to and tracks its progress:
 * a horizontal drag scrubs, a vertical drag adjusts brightness (left third) or
 * volume (right third), and a vertical drag in the middle is ignored.
 *
 * Pure and threshold-injected, so the routing and the cancel behaviour are
 * unit-testable without Compose.
 */
@Stable
class PlayerDragState(private val cancelDistancePx: Float) {
    var isActive by mutableStateOf(false)
        private set

    var mode by mutableStateOf<PlayerDragMode>(PlayerDragMode.Undecided)
        private set

    var isCancelling by mutableStateOf(false)
        private set

    /** Scrub delta in seconds while [mode] is [PlayerDragMode.Scrub]. */
    var seekDeltaSeconds by mutableLongStateOf(0L)
        private set

    /** 0..1 value while [mode] is [PlayerDragMode.Value]. */
    var value by mutableFloatStateOf(0f)
        private set

    private var startX = 0f
    private var widthPx = 1f
    private var heightPx = 1f
    private var accumulatedDx = 0f
    private var accumulatedDy = 0f
    private var volumeStart = 0f
    private var brightnessStart = 0f

    /**
     * Starts a drag at the pointer-down [offset]; [volume] and [brightness]
     * are the values the vertical swipe continues from.
     */
    fun start(offset: Offset, widthPx: Float, heightPx: Float, volume: Float, brightness: Float) {
        isActive = true
        mode = PlayerDragMode.Undecided
        isCancelling = false
        seekDeltaSeconds = 0L
        value = 0f
        startX = offset.x
        this.widthPx = widthPx.coerceAtLeast(1f)
        this.heightPx = heightPx.coerceAtLeast(1f)
        accumulatedDx = 0f
        accumulatedDy = 0f
        volumeStart = volume
        brightnessStart = brightness
    }

    /**
     * Feeds one drag delta. Returns the new 0..1 value when a vertical swipe is
     * active, or null for scrub/ignored/undecided drags.
     */
    fun drag(dragAmount: Offset): Float? {
        if (!isActive) return null
        accumulatedDx += dragAmount.x
        accumulatedDy += dragAmount.y

        if (mode == PlayerDragMode.Undecided) {
            mode = if (abs(accumulatedDx) >= abs(accumulatedDy)) {
                PlayerDragMode.Scrub
            } else {
                swipeValueForX(startX, widthPx)
                    ?.let { PlayerDragMode.Value(it) }
                    ?: PlayerDragMode.Ignored
            }
            if (mode is PlayerDragMode.Value) {
                value = startValueFor(mode)
            }
        }

        return when (mode) {
            PlayerDragMode.Scrub -> {
                isCancelling = accumulatedDy <= -cancelDistancePx
                seekDeltaSeconds =
                    (accumulatedDx / widthPx * SWIPE_SEEK_FULL_WIDTH_SECONDS).roundToLong()
                null
            }

            is PlayerDragMode.Value -> {
                val target = startValueFor(mode) - accumulatedDy / heightPx
                value = quantize(target.coerceIn(0f, 1f))
                value
            }

            else -> null
        }
    }

    /** Ends the drag; returns the scrub delta to commit, or null. */
    fun end(): Long? {
        val delta = if (mode == PlayerDragMode.Scrub && !isCancelling && seekDeltaSeconds != 0L) {
            seekDeltaSeconds
        } else {
            null
        }
        reset()
        return delta
    }

    /** Ends the drag without committing. */
    fun cancel() {
        reset()
    }

    private fun startValueFor(mode: PlayerDragMode): Float = when ((mode as? PlayerDragMode.Value)?.value) {
        PlayerSwipeValue.Volume -> volumeStart
        PlayerSwipeValue.Brightness -> brightnessStart
        null -> 0f
    }

    private fun quantize(value: Float): Float = (value * SWIPE_VALUE_STEPS).roundToInt() / SWIPE_VALUE_STEPS

    private fun reset() {
        isActive = false
        mode = PlayerDragMode.Undecided
        isCancelling = false
        seekDeltaSeconds = 0L
        value = 0f
        accumulatedDx = 0f
        accumulatedDy = 0f
    }
}

/**
 * One-shot text shown by the gesture indicator (for example a double-tap
 * skip). [shownAt] changes on every [show] so the auto-hide timer restarts
 * even when the same text is shown twice.
 */
@Stable
class PlayerTransientIndicatorState {
    var text by mutableStateOf<String?>(null)
        private set

    var shownAt by mutableIntStateOf(0)
        private set

    fun show(text: String) {
        this.text = text
        shownAt++
    }

    fun clear() {
        text = null
    }
}
