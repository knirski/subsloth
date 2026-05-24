package net.subsloth.core.model.media

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.Availability

/**
 * A season within a show/series, containing its episodes.
 */
@Immutable
data class Season(
    val seasonNumber: Int,
    val title: String?,
    val plot: String?,
    val episodes: List<Episode>,
) {
    /** Total number of episodes in this season. */
    val episodeCount: Int get() = episodes.size

    /** Whether every episode in this season is available for playback. */
    val isFullyAvailable: Boolean
        get() = episodes.all { it.availability is Availability.Available }
}
