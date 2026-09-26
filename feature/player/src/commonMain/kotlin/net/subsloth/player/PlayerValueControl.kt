package net.subsloth.player

/**
 * Host-provided control for a device-level player value, such as the system
 * media volume or the window brightness.
 *
 * Android hosts pass implementations backed by `AudioManager` and the
 * Activity window. Desktop and web hosts pass null: volume then falls back to
 * the player's own volume, and the brightness gesture is disabled because
 * there is no OS API.
 */
interface PlayerValueControl {
    val isSupported: Boolean

    /** Current value in `0f..1f`. */
    fun get(): Float

    /** Applies [value] (clamped to `0f..1f` by the implementation). */
    fun set(value: Float)
}
