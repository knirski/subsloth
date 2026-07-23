package net.subsloth.core.model.media

import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.ExternalId

sealed interface MediaDetails {
    val id: Media.MediaId
    val title: String
    val plot: String?
    val description: String?
    val availability: Availability
    val rating: Double?
    val year: Int?
    val genres: ImmutableList<String>
    val durationMinutes: Int?
    val qualities: ImmutableList<Quality>
    val subtitles: ImmutableList<Subtitle>
}

data class MovieDetails(
    override val id: Media.MediaId.Movie,
    override val title: String,
    override val plot: String?,
    override val description: String?,
    override val availability: Availability,
    override val rating: Double?,
    override val year: Int?,
    override val genres: ImmutableList<String>,
    override val durationMinutes: Int?,
    override val qualities: ImmutableList<Quality>,
    override val subtitles: ImmutableList<Subtitle>,
    val slug: String?,
    val imdbId: ExternalId?,
    val tmdbId: ExternalId?,
    val countries: ImmutableList<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
) : MediaDetails

data class ShowDetails(
    override val id: Media.MediaId.Show,
    override val title: String,
    override val plot: String?,
    override val description: String?,
    override val availability: Availability,
    override val rating: Double?,
    override val year: Int?,
    override val genres: ImmutableList<String>,
    override val durationMinutes: Int?,
    override val qualities: ImmutableList<Quality>,
    override val subtitles: ImmutableList<Subtitle>,
    val slug: String?,
    val imdbId: ExternalId?,
    val tmdbId: ExternalId?,
    val countries: ImmutableList<String>,
    val posterUrl: String?,
    val backdropUrl: String?,
    val status: ShowStatus,
    val popularity: Int?,
    val seasons: ImmutableList<Season>,
) : MediaDetails
