package net.subsloth.core.media

sealed interface PlayerEvent {
    data class Snapshot(val value: PlayerSnapshot) : PlayerEvent
    data class Error(val message: String) : PlayerEvent
    data object PlaybackEnded : PlayerEvent
}

data class PlayerSnapshot(
    val positionSeconds: Long,
    val durationSeconds: Long,
    val isPlaying: Boolean,
    val isLoading: Boolean,
)
