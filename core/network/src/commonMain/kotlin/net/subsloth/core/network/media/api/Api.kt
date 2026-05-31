package net.subsloth.core.network.media.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import net.subsloth.core.network.media.api.model.Episode
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse

/**
 * Typed API client for the Media REST API.
 *
 * All methods are suspending and use the provided [HttpClient] for transport.
 * Query parameters are omitted when null to keep URLs clean.
 * The base URL comes from [ClientFactory]'s [io.ktor.client.plugins.defaultRequest].
 */
class Api(private val client: HttpClient) {
    suspend fun listMovies(
        page: Int? = null,
        perPage: Int? = null,
        query: String? = null,
        sort: String? = null,
        genre: String? = null,
        country: String? = null,
        subtitles: String? = null,
        yearFrom: Int? = null,
        yearTo: Int? = null,
        ratingFrom: Double? = null,
        ratingTo: Double? = null,
    ): MovieListResponse = client
        .get("movies") {
            page?.let { parameter("page", it) }
            perPage?.let { parameter("per_page", it) }
            query?.let { parameter("q", it) }
            sort?.let { parameter("sort", it) }
            genre?.let { parameter("genre", it) }
            country?.let { parameter("country", it) }
            subtitles?.let { parameter("subtitles", it) }
            yearFrom?.let { parameter("year_from", it) }
            yearTo?.let { parameter("year_to", it) }
            ratingFrom?.let { parameter("rating_from", it) }
            ratingTo?.let { parameter("rating_to", it) }
        }.body()

    suspend fun listShows(
        page: Int? = null,
        perPage: Int? = null,
        query: String? = null,
        sort: String? = null,
        genre: String? = null,
        country: String? = null,
        subtitles: String? = null,
        yearFrom: Int? = null,
        yearTo: Int? = null,
        ratingFrom: Double? = null,
        ratingTo: Double? = null,
    ): ShowListResponse = client
        .get("shows") {
            page?.let { parameter("page", it) }
            perPage?.let { parameter("per_page", it) }
            query?.let { parameter("q", it) }
            sort?.let { parameter("sort", it) }
            genre?.let { parameter("genre", it) }
            country?.let { parameter("country", it) }
            subtitles?.let { parameter("subtitles", it) }
            yearFrom?.let { parameter("year_from", it) }
            yearTo?.let { parameter("year_to", it) }
            ratingFrom?.let { parameter("rating_from", it) }
            ratingTo?.let { parameter("rating_to", it) }
        }.body()

    suspend fun getMovie(id: Int): Movie = client.get("movies/$id").body()

    suspend fun getShow(id: Int): Show = client.get("shows/$id").body()

    suspend fun getEpisode(id: Int): Episode = client.get("episodes/$id").body()
}
