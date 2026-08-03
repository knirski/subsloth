package net.subsloth.core.network.media.mock

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.json.Json

private val fixtureMovies =
    """
    {
      "movies": [
        {
          "id": 137,
          "name": "Shooter",
          "imdb_id": "0822854",
          "imdb_rating": 7.1,
          "year": 2007,
          "desc": "A marksman living in exile is coaxed back into action.",
          "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/137/x.jpg",
          "array_genres": ["Action", "Crime", "Drama", "Thriller"],
          "resolution": "HD",
          "length": 125,
          "updated_at": 1776609394,
          "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/137/thumb_x.jpg"
        },
        {
          "id": 2464,
          "name": "Sleuth",
          "imdb_id": "0069281",
          "imdb_rating": 8.0,
          "year": 1972,
          "desc": "A man who loves games and theater invites his wife's lover.",
          "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/2464/x.jpg",
          "array_genres": ["Comedy", "Drama", "Mystery"],
          "resolution": "HD",
          "length": 138,
          "updated_at": 1527856925,
          "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/2464/thumb_x.jpg"
        }
      ]
    }
    """.trimIndent()

private val fixtureMovieDetail =
    """
    {
      "id": 137,
      "name": "Shooter",
      "imdb_id": "0822854",
      "imdb_rating": 7.1,
      "year": 2007,
      "desc": "A marksman living in exile is coaxed back into action.",
      "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/137/x.jpg",
      "array_genres": ["Action", "Crime", "Drama", "Thriller"],
      "resolution": "HD",
      "slug": "shooter",
      "trailer": "-6jgkXHdNi4",
      "length": 125,
      "updated_at": 1776609394,
      "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/137/thumb_x.jpg",
      "info": {
        "smil": "/uploads/movie/file/137/smil.smil",
        "thumbs": 1508,
        "original": "/uploads/movie/file/137/video.mp4"
      },
      "versions": {
        "240p": "/uploads/movie/file/137/video_240p.mp4",
        "480p": "/uploads/movie/file/137/video_480p.mp4",
        "720p": "/uploads/movie/file/137/video_720p.mp4"
      },
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/137/en.vtt", "format": "vtt"},
        {"lang": "Polski", "url": "/uploads/subtitle/137/pl.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val fixtureShows =
    """
    {
      "shows": [
        {
          "id": 1,
          "name": "Breaking Bad",
          "imdb_id": "0903747",
          "imdb_rating": 9.5,
          "year": "2008",
          "array_genres": ["Crime", "Drama", "Thriller"],
          "status": "Ended",
          "ended": true,
          "duration": 49,
          "length": 49,
          "newest_video": 1700000000,
          "backdrop_url": "https://media.subsloth.invalid/uploads/show/backdrop/1/x.jpg",
          "poster_thumb": "https://media.subsloth.invalid/uploads/show/poster/1/thumb_x.jpg"
        }
      ]
    }
    """.trimIndent()

private val fixtureShowDetail =
    """
    {
      "id": 1,
      "name": "Breaking Bad",
      "imdb_id": "0903747",
      "imdb_rating": 9.5,
      "year": "2008",
      "array_genres": ["Crime", "Drama", "Thriller"],
      "status": "Ended",
      "ended": true,
      "duration": 49,
      "length": 49,
      "backdrop_url": "https://media.subsloth.invalid/uploads/show/backdrop/1/x.jpg",
      "poster_thumb": "https://media.subsloth.invalid/uploads/show/poster/1/thumb_x.jpg",
      "slug": "breaking-bad",
      "seasons": [
        {
          "id": 1,
          "show_id": 1,
          "season_number": 1,
          "name": "Season 1",
          "episode_count": 7
        }
      ],
      "episodes": [
        {
          "id": 1,
          "show_id": 1,
          "season": 1,
          "episode": 1,
          "title": "Pilot",
          "plot": "Walter White turns to a life of crime.",
          "available": true,
          "duration": 58,
          "resolution": "HD",
          "url": "https://media.subsloth.invalid/episode/1/playlist.m3u8",
          "subtitles": [
            {"lang": "English", "url": "/uploads/subtitle/ep1/en.vtt", "format": "vtt"}
          ]
        },
        {
          "id": 2,
          "show_id": 1,
          "season": 1,
          "episode": 2,
          "title": "Cat's in the Bag...",
          "plot": "Walt and Jesse attempt to dispose of the bodies.",
          "available": true,
          "duration": 48,
          "resolution": "HD",
          "url": "https://media.subsloth.invalid/episode/2/playlist.m3u8",
          "subtitles": [
            {"lang": "English", "url": "/uploads/subtitle/ep2/en.vtt", "format": "vtt"}
          ]
        }
      ]
    }
    """.trimIndent()

private val movieDetailPath = Regex("/api/v2/movies/\\d+")
private val showDetailPath = Regex("/api/v2/shows/\\d+")

fun createMockClient(
    login: String? = null,
    password: String? = null,
    baseUrl: String = "http://localhost:8080/api/v2/",
    enableHttpLogging: Boolean = false,
): HttpClient = HttpClient(MockEngine) {
    defaultRequest {
        url(baseUrl)
    }
    engine {
        addHandler { request ->
            val path = request.url.encodedPath
            val (body, status) =
                when {
                    path == "/api/v2/movies" && request.method.value == "GET" ->
                        fixtureMovies to HttpStatusCode.OK

                    path.matches(movieDetailPath) ->
                        fixtureMovieDetail to HttpStatusCode.OK

                    path == "/api/v2/shows" && request.method.value == "GET" ->
                        fixtureShows to HttpStatusCode.OK

                    path.matches(showDetailPath) ->
                        fixtureShowDetail to HttpStatusCode.OK

                    else -> """{"error": "not found"}""" to HttpStatusCode.NotFound
                }
            respond(
                content = ByteReadChannel(body),
                status = status,
                headers =
                headersOf(
                    HttpHeaders.ContentType,
                    ContentType.Application.Json.toString(),
                ),
            )
        }
    }
    install(ContentNegotiation) {
        json(
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            },
        )
    }
}
