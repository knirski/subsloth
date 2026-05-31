package subsloth.testing.screenshot

/**
 * A single device configuration for screenshot testing.
 *
 * Used with Roborazzi's [com.github.takahirom.roborazzi.DeviceConfig]
 * in downstream feature screenshot tests. Constants for the three
 * supported form factors are provided by [ScreenshotDevices].
 */
data class DeviceConfig(val width: Int, val height: Int, val density: Int)

/**
 * Predefined device configurations for screenshot tests.
 *
 * Each value targets one of the three supported form factors:
 * phone, tablet, and Android TV.
 *
 * Example usage:
 * ```kotlin
 * com.github.takahirom.roborazzi.RoborazziRule.DeviceConfig(
 *     screenWidth = ScreenshotDevices.Phone.width,
 *     screenHeight = ScreenshotDevices.Phone.height,
 *     density = ScreenshotDevices.Phone.density.toFloat(),
 * )
 * ```
 */
object ScreenshotDevices {

    /** Phone: 1080x1920 at 420dpi (~ Pixel 4). */
    val Phone = DeviceConfig(
        width = 1080,
        height = 1920,
        density = 420,
    )

    /** Tablet: 1600x2560 at 320dpi. */
    val Tablet = DeviceConfig(
        width = 1600,
        height = 2560,
        density = 320,
    )

    /** Android TV: 1920x1080 at 320dpi. */
    val Tv = DeviceConfig(
        width = 1920,
        height = 1080,
        density = 320,
    )

    /** Default font scale for all configurations. */
    const val FONT_SCALE = 1.0f
}
