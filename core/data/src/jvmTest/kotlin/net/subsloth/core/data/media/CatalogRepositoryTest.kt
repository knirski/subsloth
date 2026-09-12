package net.subsloth.core.data.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import net.subsloth.core.domain.port.CachedCatalogItem
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.network.media.api.Api
import net.subsloth.database.SubSlothDatabase
import net.subsloth.database.SubSlothDatabaseCtor
import net.subsloth.database.dao.CachedCatalogDao
import net.subsloth.preferences.UserPreferences
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.test.fail
import kotlin.time.Clock
import kotlin.time.Instant

private const val FIXED_EPOCH_MS = 5_000_000_000L
private const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
private const val EMPTY_MOVIES = """{"movies":[]}"""
private const val EMPTY_SHOWS = """{"shows":[]}"""

private typealias MockRoute = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData

/**
 * Integration tests for [CatalogRepository] over a real in-memory Room
 * database and a Ktor [MockEngine] API.
 */
class CatalogRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `sync replaces stale cache and updates timestamp`() = runTest {
        val deps = createDeps(
            handler = routes(
                moviePages = mapOf(1 to """{"movies":[${movieJson(1, "Alpha", updatedAt = 100)}]}"""),
                showPages = mapOf(1 to """{"shows":[${showJson(2, "Beta", newestVideo = 200)}]}"""),
            ),
        )
        try {
            deps.repository.replaceCatalog(listOf(cachedItem(contentId = "99", title = "Stale")))

            val result = deps.repository.sync()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(deps.dao.count()).isEqualTo(2)
            assertThat(deps.preferences.globalCatalogCacheTimestamp().first()).isEqualTo(FIXED_EPOCH_MS)
            val movies = deps.repository.catalogItems("movie").first().filterIsInstance<MovieSummary>()
            val shows = deps.repository.catalogItems("show").first()
            assertThat(movies.map { it.title }).containsExactly("Alpha")
            assertThat(shows.filterIsInstance<ShowSummary>().map { it.title }).containsExactly("Beta")
            assertThat(movies.map { it.id.value.value }).doesNotContain(99)
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `sync paginates until an empty page`() = runTest {
        val deps = createDeps(
            handler = routes(
                moviePages = mapOf(
                    1 to """{"movies":[${movieJson(1, "Alpha", 1)},${movieJson(2, "Beta", 2)}]}""",
                    2 to """{"movies":[${movieJson(3, "Gamma", 3)}]}""",
                ),
            ),
        )
        try {
            val result = deps.repository.sync()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(deps.dao.count()).isEqualTo(3)
            assertThat(deps.dao.getAllByType("movie").first().map { it.title })
                .containsExactly("Alpha", "Beta", "Gamma")
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `sync stops when the backend repeats a page instead of paginating`() = runTest {
        val repeatedPage = """{"shows":[${showJson(1, "Alpha", 1)},${showJson(2, "Beta", 2)}]}"""
        val deps = createDeps(handler = routes(showAnyPage = repeatedPage))
        try {
            val result = deps.repository.sync()

            assertThat(result).isInstanceOf(Outcome.Success::class.java)
            assertThat(deps.dao.getAllByType("show").first().map { it.title })
                .containsExactly("Alpha", "Beta")
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `sync maps request timeout to SyncError Timeout`() = runTest {
        val deps = createDeps { request -> throw HttpRequestTimeoutException(request) }
        try {
            val result = deps.repository.sync()

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat((result as Outcome.Failure).error).isEqualTo(SyncError.Timeout)
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `sync maps io failure to SyncError NoConnectivity`() = runTest {
        val deps = createDeps { throw IOException("offline") }
        try {
            val result = deps.repository.sync()

            assertThat(result).isInstanceOf(Outcome.Failure::class.java)
            assertThat((result as Outcome.Failure).error).isEqualTo(SyncError.NoConnectivity)
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `sync rethrows CancellationException without wrapping it`() = runTest {
        val deps = createDeps { throw CancellationException("cancelled") }
        try {
            try {
                deps.repository.sync()
                fail("Expected CancellationException")
            } catch (expected: CancellationException) {
                assertThat(expected.message).isEqualTo("cancelled")
            }
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `catalogItems joins genres and countries from cache metadata`() = runTest {
        val deps = createDeps()
        try {
            deps.repository.replaceCatalog(
                listOf(
                    cachedItem(contentId = "1", title = "Alpha", genres = listOf("Action"), countries = listOf("US")),
                    cachedItem(contentId = "2", title = "Beta", genres = listOf("Drama"), countries = listOf("PL")),
                ),
            )

            val movies = deps.repository.catalogItems("movie").first().filterIsInstance<MovieSummary>()

            assertThat(movies.map { it.title }).containsExactly("Alpha", "Beta")
            assertThat(movies.first { it.id.value.value == 1 }.genres).containsExactly("Action")
            assertThat(movies.first { it.id.value.value == 2 }.genres).containsExactly("Drama")
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `replaceCatalog removes rows and metadata of previous catalog`() = runTest {
        val deps = createDeps()
        try {
            deps.repository.replaceCatalog(
                listOf(cachedItem(contentId = "1", title = "Old", genres = listOf("Action"), countries = listOf("US"))),
            )
            deps.repository.replaceCatalog(
                listOf(cachedItem(contentId = "2", title = "New", genres = listOf("Drama"), countries = listOf("PL"))),
            )

            val movies = deps.repository.catalogItems("movie").first().filterIsInstance<MovieSummary>()
            assertThat(movies.map { it.title }).containsExactly("New")
            assertThat(deps.dao.getAllGenres().first().map { it.contentId }).containsExactly("2")
            assertThat(deps.dao.getAllCountries().first().map { it.contentId }).containsExactly("2")
        } finally {
            deps.db.close()
        }
    }

    @Test
    fun `isStale reflects stored timestamp age`() = runTest {
        val deps = createDeps()
        try {
            assertThat(deps.repository.isStale()).isTrue()

            deps.preferences.setGlobalCatalogCacheTimestamp(FIXED_EPOCH_MS)
            assertThat(deps.repository.isStale()).isFalse()

            deps.preferences.setGlobalCatalogCacheTimestamp(FIXED_EPOCH_MS - TWO_HOURS_MS)
            assertThat(deps.repository.isStale()).isTrue()
        } finally {
            deps.db.close()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private class RepositoryDeps(
        val repository: CatalogRepository,
        val dao: CachedCatalogDao,
        val preferences: UserPreferences,
        val db: SubSlothDatabase,
    )

    private suspend fun createDeps(handler: MockRoute = routes()): RepositoryDeps {
        val db = Room.inMemoryDatabaseBuilder<SubSlothDatabase>(
            factory = SubSlothDatabaseCtor::initialize,
        ).setDriver(BundledSQLiteDriver()).build()
        val preferences = UserPreferences(tempDataStore())
        val client = HttpClient(MockEngine) {
            install(ContentNegotiation) { json(this@CatalogRepositoryTest.json) }
            engine { addHandler(handler) }
        }
        return RepositoryDeps(
            repository = CatalogRepository(
                api = Api(client),
                catalogDao = db.cachedCatalogDao(),
                userPreferences = preferences,
                clock = FixedClock,
            ),
            dao = db.cachedCatalogDao(),
            preferences = preferences,
            db = db,
        )
    }

    private fun tempDataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val file = File.createTempFile("catalog_repository_${UUID.randomUUID()}", ".preferences_pb")
        file.deleteOnExit()
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    private fun routes(
        moviePages: Map<Int, String> = emptyMap(),
        showPages: Map<Int, String> = emptyMap(),
        showAnyPage: String? = null,
    ): MockRoute = { request ->
        val page = request.url.parameters["page"]?.toIntOrNull() ?: 1
        val body = when (request.url.encodedPath) {
            "/movies" -> moviePages[page] ?: EMPTY_MOVIES
            "/shows" -> showPages[page] ?: showAnyPage ?: EMPTY_SHOWS
            else -> null
        }
        if (body == null) {
            respond(content = ByteReadChannel(""), status = HttpStatusCode.NotFound)
        } else {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
    }

    private fun movieJson(id: Int, title: String, updatedAt: Long): String =
        """{"id":$id,"title":"$title","updated_at":$updatedAt}"""

    private fun showJson(id: Int, title: String, newestVideo: Long): String =
        """{"id":$id,"title":"$title","newest_video":$newestVideo}"""

    private fun cachedItem(
        contentId: String,
        title: String,
        genres: List<String> = emptyList(),
        countries: List<String> = emptyList(),
    ): CachedCatalogItem = CachedCatalogItem(
        contentId = contentId,
        contentType = "movie",
        title = title,
        plot = null,
        posterUrl = null,
        backdropUrl = null,
        year = null,
        rating = null,
        durationMinutes = null,
        slug = null,
        imdbId = null,
        tmdbId = null,
        status = null,
        updatedAtEpochSeconds = 1,
        newestVideoEpochSeconds = null,
        genres = genres,
        countries = countries,
    )

    private object FixedClock : Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(FIXED_EPOCH_MS)
    }
}
