package subsloth.core.network.media.schema

import kotlinx.schema.generator.json.serialization.SerializationClassJsonSchemaGenerator
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.jupiter.api.Test
import subsloth.core.network.media.api.model.Episode
import subsloth.core.network.media.api.model.Movie
import subsloth.core.network.media.api.model.MovieListResponse
import subsloth.core.network.media.api.model.Show
import subsloth.core.network.media.api.model.ShowListResponse
import subsloth.testing.assertions.assertThat
import subsloth.testing.contract.Endpoint
import subsloth.testing.contract.FixtureLoader

/**
 * Full API contract validation: every JSON fixture must deserialize into
 * its corresponding DTO type and produce a valid JSON schema.
 *
 * Schema generation uses [SerializationClassJsonSchemaGenerator] (zero-reflection,
 * reads kotlinx.serialization descriptors). Fixture validation uses
 * kotlinx.serialization itself — if a fixture deserializes successfully
 * into its DTO, the contract holds.
 */
class FixtureSchemaValidationTest {
    private val schemaGenerator = SerializationClassJsonSchemaGenerator()
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private fun assertFixtureValid(descriptor: SerialDescriptor) {
        val schemaString = schemaGenerator.generateSchemaString(descriptor)
        assertThat(schemaString).isNotEmpty()
        val schema = Json.parseToJsonElement(schemaString)
        assertThat(schema).isInstanceOf(JsonObject::class.java)
        assertThat((schema as JsonObject)["type"]).isNotNull()
    }

    // ── Movies ─────────────────────────────────────────────────────────────

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

    // ── Shows ──────────────────────────────────────────────────────────────

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

    // ── Episodes ───────────────────────────────────────────────────────────

    @Test
    fun `EpisodeDetail fixture deserializes into Episode and schema validates`() {
        val fixture = FixtureLoader.loadFixtureJson(Endpoint.EpisodeDetail)

        val parsed = json.decodeFromJsonElement(Episode.serializer(), fixture)
        assertThat(parsed.title ?: parsed.name).isNotNull()

        assertFixtureValid(Episode.serializer().descriptor)
    }

    // ── Schema round-trip ──────────────────────────────────────────────────

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
