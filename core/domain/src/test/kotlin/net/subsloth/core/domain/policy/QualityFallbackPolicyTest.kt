package net.subsloth.core.domain.policy

import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class QualityFallbackPolicyTest {
    private val quality1080 =
        Quality(
            info = QualityDescriptor(resolution = Resolution.FULL_HD, label = "1080p", bitrate = null, mimeType = null),
            url = "https://example.com/1080p.m3u8",
            downloadUrl = null,
        )
    private val quality720 =
        Quality(
            info = QualityDescriptor(resolution = Resolution.HD_720, label = "720p", bitrate = null, mimeType = null),
            url = "https://example.com/720p.m3u8",
            downloadUrl = null,
        )
    private val quality480 =
        Quality(
            info = QualityDescriptor(resolution = Resolution.SD, label = "480p", bitrate = null, mimeType = null),
            url = "https://example.com/480p.m3u8",
            downloadUrl = null,
        )

    // ── canFallback ───────────────────────────────────────────────────────

    @Test
    fun `canFallback returns true when fallback not used`() {
        assertTrue(QualityFallbackPolicy.canFallback(fallbackUsed = false))
    }

    @Test
    fun `canFallback returns false when fallback already used`() {
        assertFalse(QualityFallbackPolicy.canFallback(fallbackUsed = true))
    }

    // ── selectFallback ────────────────────────────────────────────────────

    @Test
    fun `selectFallback returns nearest lower quality`() {
        val result =
            QualityFallbackPolicy.selectFallback(
                availableQualities = listOf(quality1080, quality720, quality480),
                currentResolution = Resolution.FULL_HD,
                fallbackUsed = false,
            )
        assertEquals(quality720, result)
    }

    @Test
    fun `selectFallback returns null when fallback already used`() {
        val result =
            QualityFallbackPolicy.selectFallback(
                availableQualities = listOf(quality1080, quality720, quality480),
                currentResolution = Resolution.FULL_HD,
                fallbackUsed = true,
            )
        assertNull(result)
    }

    @Test
    fun `selectFallback returns null when no lower quality exists`() {
        val result =
            QualityFallbackPolicy.selectFallback(
                availableQualities = listOf(quality480),
                currentResolution = Resolution.SD,
                fallbackUsed = false,
            )
        assertNull(result)
    }

    @Test
    fun `selectFallback returns null when qualities list is empty`() {
        val result =
            QualityFallbackPolicy.selectFallback(
                availableQualities = emptyList(),
                currentResolution = Resolution.FULL_HD,
                fallbackUsed = false,
            )
        assertNull(result)
    }

    @Test
    fun `selectFallback skips to next lower when 720p is current`() {
        val result =
            QualityFallbackPolicy.selectFallback(
                availableQualities = listOf(quality1080, quality720, quality480),
                currentResolution = Resolution.HD_720,
                fallbackUsed = false,
            )
        assertEquals(quality480, result)
    }
}
