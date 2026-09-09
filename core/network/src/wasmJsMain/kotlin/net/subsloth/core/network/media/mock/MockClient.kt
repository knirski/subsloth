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
          "id": 1379,
          "name": "Highlander",
          "imdb_id": "0091203",
          "imdb_rating": 7.0,
          "year": 1986,
          "desc": "An immortal Scottish swordsman must confront a brutal barbarian.",
          "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/1379/x.jpg",
          "array_genres": ["Action", "Adventure", "Fantasy"],
          "resolution": "HD",
          "length": 109,
          "updated_at": 1600000000,
          "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/1379/thumb_x.jpg"
        },
        {
          "id": 2628,
          "name": "Night of the Living Dead",
          "imdb_id": "0063990",
          "imdb_rating": 7.8,
          "year": 1968,
          "desc": "Strangers barricade themselves in a farmhouse against the undead.",
          "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/2628/x.jpg",
          "array_genres": ["Horror"],
          "resolution": "SD",
          "length": 96,
          "updated_at": 1500000000,
          "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/2628/thumb_x.jpg"
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

private val fixtureMovieDetailHighlander =
    """
    {
      "id": 1379,
      "name": "Highlander",
      "imdb_id": "0091203",
      "imdb_rating": 7.0,
      "year": 1986,
      "desc": "An immortal Scottish swordsman must confront a brutal barbarian.",
      "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/1379/x.jpg",
      "array_genres": ["Action", "Adventure", "Fantasy"],
      "resolution": "HD",
      "slug": "highlander",
      "length": 109,
      "updated_at": 1600000000,
      "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/1379/thumb_x.jpg",
      "info": {
        "smil": "/uploads/movie/file/1379/smil.smil",
        "original": "/uploads/movie/file/1379/video.mp4"
      },
      "versions": {
        "480p": "/uploads/movie/file/1379/video_480p.mp4",
        "720p": "/uploads/movie/file/1379/video_720p.mp4"
      },
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/1379/en.vtt", "format": "vtt"},
        {"lang": "Polski", "url": "/uploads/subtitle/1379/pl.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val fixtureMovieDetailLivingDead =
    """
    {
      "id": 2628,
      "name": "Night of the Living Dead",
      "imdb_id": "0063990",
      "imdb_rating": 7.8,
      "year": 1968,
      "desc": "Strangers barricade themselves in a farmhouse against the undead.",
      "backdrop_url": "https://media.subsloth.invalid/uploads/movie/backdrop/2628/x.jpg",
      "array_genres": ["Horror"],
      "resolution": "SD",
      "slug": "night-of-the-living-dead",
      "length": 96,
      "updated_at": 1500000000,
      "poster_thumb": "https://media.subsloth.invalid/uploads/movie/poster/2628/thumb_x.jpg",
      "info": {
        "smil": "/uploads/movie/file/2628/smil.smil",
        "original": "/uploads/movie/file/2628/video.mp4"
      },
      "versions": {
        "240p": "/uploads/movie/file/2628/video_240p.mp4"
      },
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/2628/en.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val movieDetailById = mapOf(
    1379 to fixtureMovieDetailHighlander,
    2628 to fixtureMovieDetailLivingDead,
)

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
        },
        {
          "id": 2,
          "name": "Better Call Saul",
          "imdb_id": "3032476",
          "imdb_rating": 9.0,
          "year": "2015",
          "array_genres": ["Crime", "Drama"],
          "status": "Ended",
          "ended": true,
          "duration": 46,
          "length": 46,
          "newest_video": 1700001000,
          "backdrop_url": "https://media.subsloth.invalid/uploads/show/backdrop/2/x.jpg",
          "poster_thumb": "https://media.subsloth.invalid/uploads/show/poster/2/thumb_x.jpg"
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
      "seasons": 1,
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

private val fixtureEpisode1 =
    """
    {
      "id": 1,
      "show_id": 1,
      "show_name": "Breaking Bad",
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
    }
    """.trimIndent()

private val fixtureEpisode2 =
    """
    {
      "id": 2,
      "show_id": 1,
      "show_name": "Breaking Bad",
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
    """.trimIndent()

private val fixtureShowDetailBetterCallSaul =
    """
    {
      "id": 2,
      "name": "Better Call Saul",
      "imdb_id": "3032476",
      "imdb_rating": 9.0,
      "year": "2015",
      "array_genres": ["Crime", "Drama"],
      "status": "Ended",
      "ended": true,
      "duration": 46,
      "length": 46,
      "backdrop_url": "https://media.subsloth.invalid/uploads/show/backdrop/2/x.jpg",
      "poster_thumb": "https://media.subsloth.invalid/uploads/show/poster/2/thumb_x.jpg",
      "slug": "better-call-saul",
      "seasons": 2,
      "episodes": [
        {
          "id": 3,
          "show_id": 2,
          "season": 1,
          "episode": 1,
          "title": "Uno",
          "plot": "Jimmy McGill works the courthouse as a struggling lawyer.",
          "available": true,
          "duration": 48,
          "resolution": "HD",
          "url": "https://media.subsloth.invalid/episode/3/playlist.m3u8",
          "subtitles": [
            {"lang": "English", "url": "/uploads/subtitle/ep3/en.vtt", "format": "vtt"},
            {"lang": "Polski", "url": "/uploads/subtitle/ep3/pl.vtt", "format": "vtt"}
          ]
        },
        {
          "id": 4,
          "show_id": 2,
          "season": 1,
          "episode": 2,
          "title": "Mijo",
          "plot": "The consequences of Jimmy's actions catch up with him.",
          "available": true,
          "duration": 47,
          "resolution": "HD",
          "url": "https://media.subsloth.invalid/episode/4/playlist.m3u8",
          "subtitles": [
            {"lang": "English", "url": "/uploads/subtitle/ep4/en.vtt", "format": "vtt"},
            {"lang": "Polski", "url": "/uploads/subtitle/ep4/pl.vtt", "format": "vtt"}
          ]
        },
        {
          "id": 5,
          "show_id": 2,
          "season": 2,
          "episode": 1,
          "title": "Switch",
          "plot": "Jimmy makes a choice about his future.",
          "available": true,
          "duration": 49,
          "resolution": "HD",
          "url": "https://media.subsloth.invalid/episode/5/playlist.m3u8",
          "subtitles": [
            {"lang": "English", "url": "/uploads/subtitle/ep5/en.vtt", "format": "vtt"},
            {"lang": "Polski", "url": "/uploads/subtitle/ep5/pl.vtt", "format": "vtt"}
          ]
        },
        {
          "id": 6,
          "show_id": 2,
          "season": 2,
          "episode": 2,
          "title": "Cobbler",
          "plot": "Jimmy takes on a new client with an unusual case.",
          "available": true,
          "duration": 47,
          "resolution": "HD",
          "url": "https://media.subsloth.invalid/episode/6/playlist.m3u8",
          "subtitles": [
            {"lang": "English", "url": "/uploads/subtitle/ep6/en.vtt", "format": "vtt"},
            {"lang": "Polski", "url": "/uploads/subtitle/ep6/pl.vtt", "format": "vtt"}
          ]
        }
      ]
    }
    """.trimIndent()

private val showDetailById = mapOf(
    2 to fixtureShowDetailBetterCallSaul,
)

private val movieDetailPath = Regex("/api/v2/movies/\\d+")
private val episodeDetailPath = Regex("/api/v2/episodes/\\d+")
private val fixtureEpisode3 =
    """
    {
      "id": 3,
      "show_id": 2,
      "show_name": "Better Call Saul",
      "season": 1,
      "episode": 1,
      "title": "Uno",
      "plot": "Jimmy McGill works the courthouse as a struggling lawyer.",
      "available": true,
      "duration": 48,
      "resolution": "HD",
      "url": "https://media.subsloth.invalid/episode/3/playlist.m3u8",
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/ep3/en.vtt", "format": "vtt"},
        {"lang": "Polski", "url": "/uploads/subtitle/ep3/pl.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val fixtureEpisode4 =
    """
    {
      "id": 4,
      "show_id": 2,
      "show_name": "Better Call Saul",
      "season": 1,
      "episode": 2,
      "title": "Mijo",
      "plot": "The consequences of Jimmy's actions catch up with him.",
      "available": true,
      "duration": 47,
      "resolution": "HD",
      "url": "https://media.subsloth.invalid/episode/4/playlist.m3u8",
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/ep4/en.vtt", "format": "vtt"},
        {"lang": "Polski", "url": "/uploads/subtitle/ep4/pl.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val fixtureEpisode5 =
    """
    {
      "id": 5,
      "show_id": 2,
      "show_name": "Better Call Saul",
      "season": 2,
      "episode": 1,
      "title": "Switch",
      "plot": "Jimmy makes a choice about his future.",
      "available": true,
      "duration": 49,
      "resolution": "HD",
      "url": "https://media.subsloth.invalid/episode/5/playlist.m3u8",
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/ep5/en.vtt", "format": "vtt"},
        {"lang": "Polski", "url": "/uploads/subtitle/ep5/pl.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val fixtureEpisode6 =
    """
    {
      "id": 6,
      "show_id": 2,
      "show_name": "Better Call Saul",
      "season": 2,
      "episode": 2,
      "title": "Cobbler",
      "plot": "Jimmy takes on a new client with an unusual case.",
      "available": true,
      "duration": 47,
      "resolution": "HD",
      "url": "https://media.subsloth.invalid/episode/6/playlist.m3u8",
      "subtitles": [
        {"lang": "English", "url": "/uploads/subtitle/ep6/en.vtt", "format": "vtt"},
        {"lang": "Polski", "url": "/uploads/subtitle/ep6/pl.vtt", "format": "vtt"}
      ]
    }
    """.trimIndent()

private val episodeById = mapOf(
    1 to fixtureEpisode1,
    2 to fixtureEpisode2,
    3 to fixtureEpisode3,
    4 to fixtureEpisode4,
    5 to fixtureEpisode5,
    6 to fixtureEpisode6,
)
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

                    path.matches(movieDetailPath) -> {
                        val movieId = path.substringAfterLast("/").toIntOrNull()
                        val body = movieDetailById[movieId] ?: fixtureMovieDetail
                        body to HttpStatusCode.OK
                    }

                    path == "/api/v2/shows" && request.method.value == "GET" ->
                        fixtureShows to HttpStatusCode.OK

                    path.matches(showDetailPath) -> {
                        val showId = path.substringAfterLast("/").toIntOrNull()
                        val body = showDetailById[showId] ?: fixtureShowDetail
                        body to HttpStatusCode.OK
                    }

                    path.matches(episodeDetailPath) -> {
                        val episodeId = path.substringAfterLast("/").toIntOrNull()
                        val body = episodeById[episodeId] ?: """{"error": "not found"}"""
                        body to HttpStatusCode.OK
                    }

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
