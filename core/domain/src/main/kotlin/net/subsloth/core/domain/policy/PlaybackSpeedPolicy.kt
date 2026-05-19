package net.subsloth.core.domain.policy

/**
 * Standard playback speeds available in the player.
 *
 * The numeric values are the speed definitions themselves, not obscure magic numbers.
 */
@Suppress("MagicNumber") // Speed values are the definition, not obscure constants.
enum class PlaybackSpeed(
    val value: Float,
) {
    SPEED_0_50(0.50f),
    SPEED_0_60(0.60f),
    SPEED_0_70(0.70f),
    SPEED_0_80(0.80f),
    SPEED_0_90(0.90f),
    SPEED_1_00(1.00f),
    SPEED_1_25(1.25f),
    SPEED_1_50(1.50f),
    SPEED_2_00(2.00f),
    ;

    companion object {
        /** The default playback speed (1×). */
        val DEFAULT: PlaybackSpeed = SPEED_1_00
    }
}

/**
 * Pure policies for playback speed selection.
 *
 * All functions have no side effects and no Android framework dependencies.
 */
object PlaybackSpeedPolicy {
    /**
     * Returns `true` when [speed] is one of the accepted playback speeds.
     */
    fun isValid(speed: Float): Boolean = PlaybackSpeed.entries.any { it.value == speed }

    /** Returns the default playback speed (1×). */
    fun defaultSpeed(): Float = PlaybackSpeed.DEFAULT.value

    /**
     * Returns all valid speeds in ascending order.
     */
    fun availableSpeeds(): List<Float> = PlaybackSpeed.entries.map { it.value }

    /**
     * Clamps [speed] to the nearest valid playback speed.
     *
     * If [speed] is already valid, returns it unchanged.
     */
    fun clamp(speed: Float): Float {
        if (isValid(speed)) return speed
        return PlaybackSpeed.entries
            .minByOrNull { kotlin.math.abs(it.value - speed) }
            ?.value ?: defaultSpeed()
    }
}
