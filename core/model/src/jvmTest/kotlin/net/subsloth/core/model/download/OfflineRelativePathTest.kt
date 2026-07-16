package net.subsloth.core.model.download

import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OfflineRelativePathTest {
    @Test
    fun `safe accepts valid relative path`() {
        val path = OfflineRelativePath.safe("downloads/video/movie.mp4")
        assertThat(path.value).isEqualTo("downloads/video/movie.mp4")
    }

    @Test
    fun `safe accepts single segment`() {
        val path = OfflineRelativePath.safe("movie.mp4")
        assertThat(path.value).isEqualTo("movie.mp4")
    }

    @Test
    fun `safe rejects blank path`() {
        assertThrows<IllegalArgumentException> { OfflineRelativePath.safe("") }
    }

    @Test
    fun `safe rejects absolute path`() {
        assertThrows<IllegalArgumentException> { OfflineRelativePath.safe("/absolute/path") }
    }

    @Test
    fun `safe rejects traversal segments`() {
        assertThrows<IllegalArgumentException> { OfflineRelativePath.safe("downloads/../../etc/passwd") }
    }

    @Test
    fun `safe rejects parent traversal at start`() {
        assertThrows<IllegalArgumentException> { OfflineRelativePath.safe("../outside") }
    }

    @Test
    fun `safe normalizes dot segments`() {
        val path = OfflineRelativePath.safe("downloads/./video/./movie.mp4")
        assertThat(path.value).isEqualTo("downloads/video/movie.mp4")
    }

    @Test
    fun `safe normalizes double slashes`() {
        val path = OfflineRelativePath.safe("downloads//video/movie.mp4")
        assertThat(path.value).isEqualTo("downloads/video/movie.mp4")
    }

    @Test
    fun `safe allows legitimate parent segments that stay relative`() {
        val path = OfflineRelativePath.safe("downloads/temp/../video/movie.mp4")
        assertThat(path.value).isEqualTo("downloads/video/movie.mp4")
    }

    @Test
    fun `normalizePure handles empty string`() {
        assertThat(normalizePure("")).isEqualTo("")
    }

    @Test
    fun `normalizePure handles absolute with traversal`() {
        assertThat(normalizePure("/a/b/../c")).isEqualTo("/a/c")
    }

    @Test
    fun `normalizePure keeps leading double-dot when cannot resolve`() {
        assertThat(normalizePure("a/../../b")).isEqualTo("../b")
    }
}
