package net.subsloth.core.model.media

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.collections.immutable.ImmutableList
import net.subsloth.core.model.Availability
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import kotlin.time.Instant

/**
 * A summary representation of a media item shown in catalog lists and search
 * results. Contains enough information for browsing without fetching details.
 */
@Stable
sealed interface Media {
    /** Unique identifier for this media item. */
    val id: MediaId

    /** Display title or name. */
    val title: String

    /** Short plot synopsis. */
    val plot: String?

    /** Availability state (available, upcoming, expired, etc.). */
    val availability: Availability

    /** Average user rating (0–10 scale). */
    val rating: Double?

    /** Year of release. */
    val year: Int?

    /** List of genre descriptors. */
    val genres: ImmutableList<String>

    /** Duration in minutes. */
    val durationMinutes: Int?

    /**
     * A sealed union of all possible media identifiers.
     *
     * This includes an [Episode] variant so that progress, download, and
     * library records can reference individual episodes as well as movies
     * and entire shows.
     */
    @Stable
    sealed interface MediaId {
        @Immutable
        data class Movie(
            val value: MovieId,
        ) : MediaId

        @Immutable
        data class Show(
            val value: ShowId,
        ) : MediaId

        @Immutable
        data class Episode(
            val value: EpisodeId,
        ) : MediaId
    }
}

/**
 * A movie summary for catalog lists and search results.
 */
@Immutable
data class MovieSummary(
    override val id: Media.MediaId.Movie,
    override val title: String,
    override val plot: String?,
    override val availability: Availability,
    override val rating: Double?,
    override val year: Int?,
    override val genres: ImmutableList<String>,
    override val durationMinutes: Int?,
    val slug: String?,
    val imdbId: ExternalId?,
    val backdropUrl: String?,
    val updatedAtEpochSeconds: Instant? = null,
) : Media

/**
 * A show/series summary for catalog lists and search results.
 */
@Immutable
data class ShowSummary(
    override val id: Media.MediaId.Show,
    override val title: String,
    override val plot: String?,
    override val availability: Availability,
    override val rating: Double?,
    override val year: Int?,
    override val genres: ImmutableList<String>,
    override val durationMinutes: Int?,
    val slug: String?,
    val imdbId: ExternalId?,
    val backdropUrl: String?,
    val status: ShowStatus,
    val countries: ImmutableList<String>,
    val newestVideoEpochSeconds: Instant? = null,
) : Media

/** Production status of a show/series. */
@Immutable
enum class ShowStatus {
    ONGOING,
    ENDED,
    UPCOMING,
    UNKNOWN,
}
