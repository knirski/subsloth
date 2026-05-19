package net.subsloth.core.model.identifier

/**
 * A video resolution expressed as width × height in pixels.
 */
data class Resolution(
    val width: Int,
    val height: Int,
) : Comparable<Resolution> {
    init {
        require(width > 0) { "width must be positive: $width" }
        require(height > 0) { "height must be positive: $height" }
    }

    /** Human-readable label such as "1080p" or "4K". */
    @Suppress("MagicNumber") // Industry-standard video resolution thresholds (SD, 480p, 720p, 1080p, 1440p, 4K).
    val label: String
        get() =
            when {
                height <= 360 -> "SD"
                height <= 480 -> "480p"
                height <= 720 -> "720p"
                height <= 1080 -> "1080p"
                height <= 1440 -> "1440p"
                height <= 2160 -> "4K"
                else -> "${height}p"
            }

    /** Total pixel count used for quality comparisons. */
    val pixelCount: Long get() = width.toLong() * height

    override fun compareTo(other: Resolution): Int = pixelCount.compareTo(other.pixelCount)

    @Suppress("MagicNumber") // Industry-standard resolutions (SD, HD, FHD, QHD, UHD-4K).
    companion object {
        val SD: Resolution = Resolution(640, 360)
        val HD_720: Resolution = Resolution(1280, 720)
        val FULL_HD: Resolution = Resolution(1920, 1080)
        val QHD: Resolution = Resolution(2560, 1440)
        val UHD_4K: Resolution = Resolution(3840, 2160)
    }
}
