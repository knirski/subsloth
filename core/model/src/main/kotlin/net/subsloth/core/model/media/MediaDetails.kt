package net.subsloth.core.model.media

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.ExternalId

/**
 * Full details for a media item, including extended metadata, available
 * qualities, and subtitle tracks.
 *
 * Uses [Media.MediaId] as its identifier type, sharing the same hierarchy
 * with [Media] summaries so that identifiers can be passed freely between
 * summary lists and detail screens.
 */
@Stable
sealed interface MediaDetails {
    val id: Media.MediaId
    val title: String
    val plot: String?
    val description: String?
    val availability: Availability
    val rating: Double?
    val year: Int?
    val genres: List<String>
    val durationMinutes: Int?
    val qualities: List<Quality>
    val subtitles: List<Subtitle>
}

/**
 * Full details for a movie.
 *
 * Poster and backdrop URLs are included for transient display use during an
 * active browsing session. They are not preserved in persistent storage.
 */
@Immutable
data class MovieDetails(
    override val id: Media.MediaId.Movie,
    override val title: String,
    override val plot: String?,
    override val description: String?,
    override val availability: Availability,
    override val rating: Double?,
    override val year: Int?,
    override val genres: List<String>,
    override val durationMinutes: Int?,
    override val qualities: List<Quality>,
    override val subtitles: List<Subtitle>,
    val slug: String?,
    val imdbId: ExternalId?,
    val tmdbId: ExternalId?,
    val countries: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
) : MediaDetails

/**
 * Full details for a show/series, including its seasons and episodes.
 */
@Immutable
data class ShowDetails(
    override val id: Media.MediaId.Show,
    override val title: String,
    override val plot: String?,
    override val description: String?,
    override val availability: Availability,
    override val rating: Double?,
    override val year: Int?,
    override val genres: List<String>,
    override val durationMinutes: Int?,
    override val qualities: List<Quality>,
    override val subtitles: List<Subtitle>,
    val slug: String?,
    val imdbId: ExternalId?,
    val tmdbId: ExternalId?,
    val countries: List<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
    val status: ShowStatus,
    val popularity: Int?,
    val seasons: List<Season>,
) : MediaDetails
