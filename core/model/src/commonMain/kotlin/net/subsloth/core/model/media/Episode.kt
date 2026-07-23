package net.subsloth.core.model.media

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ShowId
import kotlin.time.Instant

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
    val qualities: ImmutableList<Quality>,
    val subtitles: ImmutableList<Subtitle>,
    val airDateEpochSeconds: Instant?,
    val premiereDateEpochSeconds: Instant?,
) {
    val isUpcoming: Boolean
        get() = availability is Availability.Upcoming
}
