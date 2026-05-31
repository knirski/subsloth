package subsloth.core.model.progress

import androidx.compose.runtime.Immutable
import subsloth.core.model.media.Media
import kotlin.time.Instant

/**
 * Tracks a user's playback progress for a media item.
 *
 * This is a persistent record used to resume playback from the last known
 * position. It does not contain any stream URLs — those are ephemeral values
 * obtained at playback time.
 */
@Immutable
data class PlaybackProgress(
    val mediaId: Media.MediaId,
    /** Playback position in seconds. */
    val positionSeconds: Long,
    /** Total duration in seconds at the time this progress was recorded. */
    val durationSeconds: Long,
    /** Epoch seconds when this progress was last updated. */
    val lastUpdatedEpochSeconds: Instant,
    /**
     * Whether the media is considered fully watched.
     *
     * A media item is watched when [positionSeconds] reaches or exceeds
     * the completion threshold defined by watch policies.
     */
    val isWatched: Boolean,
) {
    init {
        require(positionSeconds >= 0) {
            "positionSeconds must be non-negative: $positionSeconds"
        }
        require(durationSeconds >= 0) {
            "durationSeconds must be non-negative: $durationSeconds"
        }
        // Allow positionSeconds > durationSeconds when duration is unknown (0).
        if (durationSeconds > 0L) {
            require(positionSeconds <= durationSeconds) {
                "positionSeconds ($positionSeconds) cannot exceed durationSeconds ($durationSeconds)"
            }
        }
    }

    /** Fractional progress (0.0–1.0). */
    val fraction: Double
        get() =
            if (durationSeconds > 0L) {
                positionSeconds.toDouble() / durationSeconds.toDouble()
            } else {
                0.0
            }
}
