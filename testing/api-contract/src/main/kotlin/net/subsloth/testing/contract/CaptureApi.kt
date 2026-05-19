package net.subsloth.testing.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64

data class CaptureRequest(
    val endpoint: Endpoint,
    val path: String,
)

/**
 * Captures only the five native Kodi-compatible API fixtures directly from the
 * live API. Browser-only discovery fixtures come from HAR export.
 */
object CaptureApi {
    private const val API_BASE = "https://front.media-mirror.tv/api/v2"
    private const val USER_AGENT = "Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1"
    private const val HTTP_OK = 200

    fun capturePlan(): List<CaptureRequest> =
        listOf(
            CaptureRequest(Endpoint.Movies, "/movies"),
            CaptureRequest(Endpoint.Shows, "/shows"),
            CaptureRequest(Endpoint.MovieDetail, "/movies/{id}"),
            CaptureRequest(Endpoint.ShowDetail, "/shows/{id}"),
            CaptureRequest(Endpoint.EpisodeDetail, "/episodes/{id}"),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        val email =
            args.getOrNull(0) ?: error("Usage: CaptureApi <email> <password> [native-dir] [rules-file]")
        val password =
            args.getOrNull(1) ?: error("Usage: CaptureApi <email> <password> [native-dir] [rules-file]")
        val nativeDir = File(args.getOrNull(2) ?: "src/main/resources/media")
        val rulesFile = File(args.getOrNull(3) ?: "scripts/capture/sanitization-rules.json")

        val auth = "Basic " + Base64.getEncoder().encodeToString("$email:$password".toByteArray())
        val client =
            HttpClient
                .newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build()

        val rules = loadSanitizationRules(rulesFile)
        val requests = capturePlan()
        cleanupNativeFixtureOutputs(nativeDir)

        val moviesRequest = requests.first { it.endpoint == Endpoint.Movies }
        println("[capture-api] Fetching ${moviesRequest.path} ...")
        val moviesBody = fetch(client, auth, moviesRequest.path)
        writeFixture(moviesBody, rules, File(nativeDir, "Movies.json"))

        val showsRequest = requests.first { it.endpoint == Endpoint.Shows }
        println("[capture-api] Fetching ${showsRequest.path} ...")
        val showsBody = fetch(client, auth, showsRequest.path)
        writeFixture(showsBody, rules, File(nativeDir, "Shows.json"))

        val movieId = extractFirstId(moviesBody, "movies")
        val movieDetailRequest = requests.first { it.endpoint == Endpoint.MovieDetail }
        if (movieId != null) {
            val path = movieDetailRequest.path.replace("{id}", movieId.toString())
            println("[capture-api] Fetching $path ...")
            val movieDetailBody = fetch(client, auth, path)
            writeFixture(movieDetailBody, rules, File(nativeDir, "MovieDetail.json"))
        } else {
            println("[capture-api] WARNING: No movie IDs found, skipping details")
        }

        val showId = extractFirstId(showsBody, "shows")
        val showDetailRequest = requests.first { it.endpoint == Endpoint.ShowDetail }
        val episodeDetailRequest = requests.first { it.endpoint == Endpoint.EpisodeDetail }
        if (showId != null) {
            val showPath = showDetailRequest.path.replace("{id}", showId.toString())
            println("[capture-api] Fetching $showPath ...")
            val showDetailBody = fetch(client, auth, showPath)
            writeFixture(showDetailBody, rules, File(nativeDir, "ShowDetail.json"))

            val episodeId = extractFirstId(showDetailBody, "episodes")
            if (episodeId != null) {
                val episodePath = episodeDetailRequest.path.replace("{id}", episodeId.toString())
                println("[capture-api] Fetching $episodePath ...")
                val episodeDetailBody = fetch(client, auth, episodePath)
                writeFixture(episodeDetailBody, rules, File(nativeDir, "EpisodeDetail.json"))
            } else {
                println("[capture-api] WARNING: No episode IDs found, skipping episode details")
            }
        } else {
            println("[capture-api] WARNING: No show IDs found, skipping details")
        }

        println("[capture-api] Done - native: $nativeDir")
    }

    internal fun cleanupNativeFixtureOutputs(nativeDir: File) {
        nativeDir.mkdirs()
        Endpoint
            .kodiEndpoints()
            .map { endpoint ->
                File(
                    nativeDir,
                    endpoint.resourcePathFor(endpoint.methods.single()).substringAfterLast('/'),
                )
            }.forEach { file ->
                if (file.exists()) {
                    file.delete()
                }
            }
    }

    private fun fetch(
        client: HttpClient,
        auth: String,
        path: String,
    ): String {
        val request =
            HttpRequest
                .newBuilder()
                .uri(URI.create("$API_BASE$path"))
                .header("Authorization", auth)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, */*")
                .header("Accept-Language", "en-US,en;q=0.5")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != HTTP_OK) {
            throw HttpException(response.statusCode(), path)
        }
        return response.body()
    }

    private class HttpException(
        val statusCode: Int,
        path: String,
    ) : Exception("HTTP $statusCode for $path")

    private fun extractFirstId(
        jsonBody: String,
        arrayField: String,
    ): Int? =
        try {
            val obj = Json.parseToJsonElement(jsonBody).jsonObject
            val array = obj[arrayField]?.jsonArray
            array
                ?.firstOrNull()
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.intOrNull
        } catch (_: Exception) {
            null
        }

    private fun writeFixture(
        rawBody: String,
        rules: SanitizationRules,
        outFile: File,
    ) {
        val sanitized = HarProcessor.sanitizeStructuredBody(rawBody, rules)

        outFile.parentFile?.mkdirs()
        outFile.writeText(sanitized)
        println("[capture-api]   -> ${outFile.name} (${sanitized.length} chars)")
    }
}
