package net.subsloth.testing.contract

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Utility for loading fixture JSON files from resources.
 *
 * Intended for use in contract-verification tests and WireMock mapping generation.
 */
object FixtureLoader {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load the raw text content of a fixture file from the classpath.
     */
    fun loadFixtureText(resourcePath: String): String {
        val url =
            FixtureLoader::class.java.getResource(resourcePath)
                ?: error("Fixture resource not found: $resourcePath")
        return url.readText()
    }

    fun loadFixtureText(endpoint: Endpoint): String = loadFixtureText(endpoint.resourcePath)

    fun loadFixtureText(
        endpoint: Endpoint,
        method: HttpMethod,
    ): String = loadFixtureText(endpoint.resourcePathFor(method))

    /**
     * Load a fixture file and parse it into a [JsonElement].
     */
    fun loadFixtureJson(resourcePath: String): JsonElement = json.parseToJsonElement(loadFixtureText(resourcePath))

    fun loadFixtureJson(endpoint: Endpoint): JsonElement = loadFixtureJson(endpoint.resourcePath)

    fun loadFixtureJson(
        endpoint: Endpoint,
        method: HttpMethod,
    ): JsonElement = loadFixtureJson(endpoint.resourcePathFor(method))

    /**
     * Recursively collect all string values from a parsed [JsonElement] tree.
     *
     * Useful for URL/host checks and sensitive-value scanning.
     */
    fun collectStrings(element: JsonElement): List<String> =
        when (element) {
            is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
            is JsonArray -> element.flatMap { collectStrings(it) }
            is JsonObject -> element.values.flatMap { collectStrings(it) }
        }

    /**
     * Convenience overload that loads a fixture and returns all string values.
     */
    fun fixtureStrings(resourcePath: String): List<String> = collectStrings(loadFixtureJson(resourcePath))
}
