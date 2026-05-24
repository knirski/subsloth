package net.subsloth.core.model.media

import androidx.compose.runtime.Immutable
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ShowId

/**
 * A single episode of a show/series.
 */
@Immutable
data class Episode(
    val id: EpisodeId,
    val showId: ShowId,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String?,
    val durationSeconds: Long?,
    val availability: Availability,
    val imdbId: ExternalId?,
    val qualities: List<Quality>,
    val subtitles: List<Subtitle>,
    val airDateEpochSeconds: Long?,
    val premiereDateEpochSeconds: Long?,
) {
    /**
     * Whether this episode is scheduled for future release based on its
     * premiere date metadata. Upcoming episodes must not be playable or
     * downloadable.
     */
    val isUpcoming: Boolean
        get() = availability is Availability.Upcoming
}
