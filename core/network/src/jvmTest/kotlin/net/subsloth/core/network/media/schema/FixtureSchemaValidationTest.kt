package net.subsloth.core.network.media.schema

import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import net.subsloth.core.network.media.api.model.Episode
import net.subsloth.core.network.media.api.model.Movie
import net.subsloth.core.network.media.api.model.MovieListResponse
import net.subsloth.core.network.media.api.model.Show
import net.subsloth.core.network.media.api.model.ShowListResponse
import net.subsloth.testing.assertions.assertThat
import net.subsloth.testing.contract.Endpoint
import net.subsloth.testing.contract.FixtureLoader
import net.subsloth.testing.contract.ResponseKind
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Full API contract validation: every JSON fixture must deserialize into
 * its corresponding DTO type and produce a valid JSON schema.
 *
 * Schema generation uses [SerializationClassJsonSchemaGenerator] (zero-reflection,
 * reads kotlinx.serialization descriptors). Fixture validation uses
 * kotlinx.serialization itself — if a fixture deserializes successfully
 * into its DTO, the contract holds.
 *
 * Web-discovery JSON fixtures (which lack typed DTOs in :core:network)
 * are validated as parseable JSON with structural consistency checks.
 * Non-JSON fixtures are validated for existence and non-emptiness.
 */
class FixtureSchemaValidationTest {
    private val schemaGenerator = SerializationClassJsonSchemaGenerator()
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    companion object {
        /** All endpoints with JSON responses. */
        @JvmStatic
        fun jsonEndpoints(): List<Endpoint> = Endpoint.entries
            .filter { it.responseKind == ResponseKind.Json }
            .sortedBy { it.fixtureName }

        /** All endpoints with non-JSON responses. */
        @JvmStatic
        fun nonJsonEndpoints(): List<Endpoint> = Endpoint.entries
            .filter { it.responseKind != ResponseKind.Json }
            .sortedBy { it.fixtureName }
    }

    // ── Schema generation helpers ───────────────────────────────────────────

    private fun assertFixtureValid(descriptor: SerialDescriptor) {
        val schemaString = schemaGenerator.generateSchemaString(descriptor)
        assertThat(schemaString).isNotEmpty()
        val schema = Json.parseToJsonElement(schemaString)
        assertThat(schema).isInstanceOf(JsonObject::class.java)
        assertThat((schema as JsonObject)["type"]).isNotNull()
    }

    // ── Native contract endpoint tests ──────────────────────────────────────

    @Test
    fun `Movies fixture deserializes into MovieListResponse and schema validates`() {
        val fixture = FixtureLoader.loadFixtureJson(Endpoint.Movies)

        val parsed = json.decodeFromJsonElement(MovieListResponse.serializer(), fixture)
        assertThat(parsed.movies).isNotNull()
        assertThat(parsed.movies).isNotEmpty()

        assertFixtureValid(MovieListResponse.serializer().descriptor)
    }

    @Test
    fun `MovieDetail fixture deserializes into Movie and schema validates`() {
        val fixture = FixtureLoader.loadFixtureJson(Endpoint.MovieDetail)

        val parsed = json.decodeFromJsonElement(Movie.serializer(), fixture)
        assertThat(parsed.title ?: parsed.name).isNotNull()

        assertFixtureValid(Movie.serializer().descriptor)
    }

    @Test
    fun `Shows fixture deserializes into ShowListResponse and schema validates`() {
        val fixture = FixtureLoader.loadFixtureJson(Endpoint.Shows)

        val parsed = json.decodeFromJsonElement(ShowListResponse.serializer(), fixture)
        assertThat(parsed.shows).isNotNull()
        assertThat(parsed.shows).isNotEmpty()

        assertFixtureValid(ShowListResponse.serializer().descriptor)
    }

    @Test
    fun `ShowDetail fixture deserializes into Show and schema validates`() {
        val fixture = FixtureLoader.loadFixtureJson(Endpoint.ShowDetail)

        val parsed = json.decodeFromJsonElement(Show.serializer(), fixture)
        assertThat(parsed.title ?: parsed.name).isNotNull()

        assertFixtureValid(Show.serializer().descriptor)
    }

    @Test
    fun `EpisodeDetail fixture deserializes into Episode and schema validates`() {
        val fixture = FixtureLoader.loadFixtureJson(Endpoint.EpisodeDetail)

        val parsed = json.decodeFromJsonElement(Episode.serializer(), fixture)
        assertThat(parsed.title ?: parsed.name).isNotNull()

        assertFixtureValid(Episode.serializer().descriptor)
    }

    // ── All JSON endpoints (parameterized: native + web-discovery) ──────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonEndpoints")
    fun `JSON fixture parses as valid JsonElement`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val element = FixtureLoader.loadFixtureJson(endpoint, method)
            assertThat(element).isNotNull()
            // Every JSON endpoint fixture should be a non-empty object or array
            when (element) {
                is JsonObject -> assertThat(element).isNotEmpty()
                is JsonArray -> assertThat(element).isNotEmpty()
                else -> { /* JsonPrimitive at root is unusual but valid */ }
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonEndpoints")
    fun `JSON fixture round-trips through JsonElement serialization`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val element = FixtureLoader.loadFixtureJson(endpoint, method)
            val reSerialized = json.encodeToString(JsonElement.serializer(), element)
            val reParsed = json.parseToJsonElement(reSerialized)
            assertThat(reParsed).isEqualTo(element)
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsonEndpoints")
    fun `JSON fixture body has no structural null in place of real content`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val text = FixtureLoader.loadFixtureText(endpoint, method).trim()
            assertThat(text).isNotEqualTo("null")
            assertThat(text).isNotEqualTo("{}")
            assertThat(text).isNotEqualTo("[]")
        }
    }

    // ── Non-JSON endpoints (parameterized) ──────────────────────────────────

    @ParameterizedTest(name = "{0}")
    @MethodSource("nonJsonEndpoints")
    fun `non-JSON fixture is not empty`(endpoint: Endpoint) {
        for (method in endpoint.methods) {
            val text = FixtureLoader.loadFixtureText(endpoint, method)
            assertThat(text).isNotEmpty()
        }
    }

    // ── Schema round-trip (all DTO descriptors) ─────────────────────────────

    @Test
    fun `all generated schemas are valid JSON`() {
        val descriptors =
            listOf(
                Movie.serializer().descriptor,
                MovieListResponse.serializer().descriptor,
                Show.serializer().descriptor,
                ShowListResponse.serializer().descriptor,
                Episode.serializer().descriptor,
            )

        for (descriptor in descriptors) {
            val schema = schemaGenerator.generateSchemaString(descriptor)
            val parsed = Json.parseToJsonElement(schema)
            assertThat(parsed).isNotNull()
        }
    }
}
