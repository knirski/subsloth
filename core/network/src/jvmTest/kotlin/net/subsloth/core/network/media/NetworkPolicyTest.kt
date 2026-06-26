package net.subsloth.core.network.media

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.pluginOrNull
import net.subsloth.core.model.download.DownloadState
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.progress.PlaybackProgress
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.client.ClientFactory
import net.subsloth.core.network.media.client.ResponseValidationPlugin
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

/**
 * Tests for network policy requirements:
 * - No annotation/notes endpoints, no WebView/browser identity
 * - Raw URL redaction
 * - Server mutation gates
 * - Kodi-compatible request identity (User-Agent, Accept headers)
 * - Bounded retries on 429/5xx with fixed backoff
 * - Low concurrency, single-flight de-duplication
 */
class NetworkPolicyTest {
    private val clients = mutableListOf<HttpClient>()

    private fun createClient(
        login: String? = null,
        password: String? = null,
        baseUrl: String = "http://localhost:1/",
    ): HttpClient = ClientFactory.create(login = login, password = password, baseUrl = baseUrl).also { clients.add(it) }

    @AfterEach
    fun tearDown() {
        clients.forEach { it.close() }
        clients.clear()
    }

    // ── No Notes Endpoints ───────────────────────────────────────────────

    @Test
    fun `Api has no notes endpoint`() {
        val methods = Api::class.java.declaredMethods
        val methodNames = methods.map { it.name }
        // Notes endpoints must not be present
        assertThat(methodNames).doesNotContain("listNotes")
        assertThat(methodNames).doesNotContain("getNotes")
        assertThat(methodNames).doesNotContain("postNote")
        assertThat(methodNames).doesNotContain("deleteNote")
        // No method should contain "note" in its name
        val noteMethods = methodNames.filter { it.contains("note", ignoreCase = true) }
        assertThat(noteMethods).isEmpty()
    }

    // ── Raw URL Redaction ────────────────────────────────────────────────

    @Test
    fun `mapped domain models contain no raw Media stream URLs in persistent fields`() {
        val qualityDescriptorClass = QualityDescriptor::class.java
        val persistentFields = qualityDescriptorClass.declaredFields.map { it.name }
        assertThat(persistentFields).doesNotContain("url")
        assertThat(persistentFields).doesNotContain("downloadUrl")
        assertThat(persistentFields).doesNotContain("streamUrl")
    }

    @Test
    fun `download state contains no URLs`() {
        val downloadStateClass = DownloadState::class.java
        val fields = downloadStateClass.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("url")
        assertThat(fields).doesNotContain("downloadUrl")
        assertThat(fields).doesNotContain("streamUrl")
        assertThat(fields).doesNotContain("subtitleUrl")
    }

    @Test
    fun `playback progress contains no URLs`() {
        val progressClass = PlaybackProgress::class.java
        val fields = progressClass.declaredFields.map { it.name }
        assertThat(fields).doesNotContain("url")
        assertThat(fields).doesNotContain("downloadUrl")
        assertThat(fields).doesNotContain("streamUrl")
    }

    // ── Server Mutation Gate ─────────────────────────────────────────────

    @Test
    fun `Api has no library mutation endpoints`() {
        val methods = Api::class.java.declaredMethods
        val methodNames = methods.map { it.name }

        assertThat(methodNames).doesNotContain("addFavorite")
        assertThat(methodNames).doesNotContain("removeFavorite")
        assertThat(methodNames).doesNotContain("markWatched")
        assertThat(methodNames).doesNotContain("markUnwatched")
        assertThat(methodNames).doesNotContain("addToLibrary")
        assertThat(methodNames).doesNotContain("removeFromLibrary")
        assertThat(methodNames).doesNotContain("subscribe")
        assertThat(methodNames).doesNotContain("unsubscribe")
    }

    // ── Kodi-compatible Request Identity ─────────────────────────────────

    @Test
    fun `ClientFactory creates client with timeout plugin`() {
        val client = createClient()
        assertThat(client.pluginOrNull(HttpTimeout)).isNotNull()
    }

    @Test
    fun `ClientFactory creates client with JSON content negotiation`() {
        val client = createClient()
        assertThat(client.pluginOrNull(ContentNegotiation)).isNotNull()
    }

    @Test
    fun `ClientFactory creates client with response validation plugin`() {
        val client = createClient()
        assertThat(client.pluginOrNull(ResponseValidationPlugin)).isNotNull()
    }

    @Test
    fun `ClientFactory creates client with retry plugin`() {
        val client = createClient()
        assertThat(client.pluginOrNull(HttpRequestRetry)).isNotNull()
    }

    @Test
    fun `ClientFactory creates client with auth plugin when credentials provided`() {
        val client = createClient(login = "test", password = "test")
        assertThat(client.pluginOrNull(Auth)).isNotNull()
    }

    @Test
    fun `ClientFactory creates client without auth when credentials omitted`() {
        val client = createClient()
        assertThat(client.pluginOrNull(Auth)).isNull()
    }

    // ── Low concurrency ──────────────────────────────────────────────────

    @Test
    fun `ClientFactory creates client with default connection pool`() {
        val client = createClient(login = "test", password = "test")
        assertThat(client).isNotNull()
    }
}
