package subsloth.core.model.media

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import subsloth.core.model.Availability
import subsloth.core.model.identifier.EpisodeId
import subsloth.core.model.identifier.ExternalId
import subsloth.core.model.identifier.ShowId
import kotlin.time.Instant

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
    val qualities: ImmutableList<Quality>,
    val subtitles: ImmutableList<Subtitle>,
    val airDateEpochSeconds: Instant?,
    val premiereDateEpochSeconds: Instant?,
) {
    val isUpcoming: Boolean
        get() = availability is Availability.Upcoming
}
