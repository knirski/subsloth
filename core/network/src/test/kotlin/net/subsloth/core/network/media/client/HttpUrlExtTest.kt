package net.subsloth.core.network.media.client

import net.subsloth.testing.assertions.assertThat
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test

/**
 * Tests for [HttpUrlExt.toRedactedString].
 */
class HttpUrlExtTest {
    @Test
    fun `toRedactedString preserves scheme host and path`() {
        val url = "https://api.media.tv/api/v2/movies".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv/api/v2/movies")
    }

    @Test
    fun `toRedactedString strips query parameters`() {
        val url = "https://api.media.tv/api/v2/movies?page=1&token=secret123".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv/api/v2/movies")
    }

    @Test
    fun `toRedactedString strips fragment`() {
        val url = "https://api.media.tv/api/v2/movies#section".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv/api/v2/movies")
    }

    @Test
    fun `toRedactedString includes non-default port`() {
        val url = "https://api.media.tv:8443/api/v2/movies".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv:8443/api/v2/movies")
    }

    @Test
    fun `toRedactedString omits default port 443`() {
        val url = "https://api.media.tv:443/api/v2/movies".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv/api/v2/movies")
    }

    @Test
    fun `toRedactedString omits default port 80 for http`() {
        val url = "http://api.media.tv:80/api/v2/movies".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("http://api.media.tv/api/v2/movies")
    }

    @Test
    fun `toRedactedString preserves encoded path characters`() {
        val url = "https://api.media.tv/api/v2/movies/123%20test".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv/api/v2/movies/123%20test")
    }

    @Test
    fun `toRedactedString handles root path`() {
        val url = "https://api.media.tv".toHttpUrl()
        assertThat(url.toRedactedString()).isEqualTo("https://api.media.tv")
    }
}
