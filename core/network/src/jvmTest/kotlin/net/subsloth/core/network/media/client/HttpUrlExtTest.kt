package net.subsloth.core.network.media.client

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [HttpUrlExt.toRedactedString].
 */
class HttpUrlExtTest {
    @Test
    fun `toRedactedString preserves scheme host and path`() {
        val url = Url("https://api.media.tv/api/v2/movies")
        assertEquals("https://api.media.tv/api/v2/movies", url.toRedactedString())
    }

    @Test
    fun `toRedactedString strips query parameters`() {
        val url = Url("https://api.media.tv/api/v2/movies?page=1&token=secret123")
        assertEquals("https://api.media.tv/api/v2/movies", url.toRedactedString())
    }

    @Test
    fun `toRedactedString strips fragment`() {
        val url = Url("https://api.media.tv/api/v2/movies#section")
        assertEquals("https://api.media.tv/api/v2/movies", url.toRedactedString())
    }

    @Test
    fun `toRedactedString includes non-default port`() {
        val url = Url("https://api.media.tv:8443/api/v2/movies")
        assertEquals("https://api.media.tv:8443/api/v2/movies", url.toRedactedString())
    }

    @Test
    fun `toRedactedString omits default port 443`() {
        val url = Url("https://api.media.tv:443/api/v2/movies")
        assertEquals("https://api.media.tv/api/v2/movies", url.toRedactedString())
    }

    @Test
    fun `toRedactedString omits default port 80 for http`() {
        val url = Url("http://api.media.tv:80/api/v2/movies")
        assertEquals("http://api.media.tv/api/v2/movies", url.toRedactedString())
    }

    @Test
    fun `toRedactedString preserves encoded path characters`() {
        val url = Url("https://api.media.tv/api/v2/movies/123%20test")
        assertEquals("https://api.media.tv/api/v2/movies/123%20test", url.toRedactedString())
    }

    @Test
    fun `toRedactedString handles root path`() {
        val url = Url("https://api.media.tv")
        assertEquals("https://api.media.tv", url.toRedactedString())
    }
}
