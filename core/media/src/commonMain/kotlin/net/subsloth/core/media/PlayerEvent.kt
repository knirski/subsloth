package net.subsloth.core.media

sealed interface PlayerEvent {
    data class Snapshot(val value: PlayerSnapshot) : PlayerEvent
    data class Error(val message: String) : PlayerEvent
    data object PlaybackEnded : PlayerEvent

    /**
     * The surface created a new player instance — on first composition and
     * whenever the host composition is recreated (for example an Android
     * configuration change). The host uses this to re-open the current source
     * at its current position, because a new player starts empty.
     */
    data object Attached : PlayerEvent
}

data class PlayerSnapshot(
    val positionSeconds: Long,
    val durationSeconds: Long,
    val isPlaying: Boolean,
    val isLoading: Boolean,
)
