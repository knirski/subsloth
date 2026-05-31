package net.subsloth.ui.accessibility

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.sizeIn
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Minimum touch target size for interactive elements per Material
 * accessibility guidelines (48dp).
 */
val minimumTouchTarget: Dp = 48.dp

/**
 * Minimum touch target size for TV D-pad focusable items (32dp
 * is the Android TV minimum, but 48dp is preferred for accessibility).
 */
val tvMinimumFocusTarget: Dp = 32.dp

/**
 * Modifier ensuring this element meets the minimum touch target size.
 *
 * Use on interactive elements (buttons, icons, chips) so they are
 * accessible on all device form factors.
 *
 * @param minSize The minimum size. Defaults to [minimumTouchTarget].
 */
fun Modifier.minimumTouchTarget(minSize: Dp = minimumTouchTarget): Modifier {
    return this.then(
        Modifier.defaultMinSize(minWidth = minSize, minHeight = minSize),
    )
}

/**
 * Applies a content description for accessibility services.
 *
 * @param description The spoken description for this element.
 */
fun Modifier.accessibilityDescription(description: String): Modifier {
    return this.then(
        Modifier.semantics {
            contentDescription = description
        },
    )
}

/**
 * Visual state classification for elements that must maintain
 * sufficient color contrast per WCAG 2.1 AA.
 */
@Immutable
enum class ContrastState {
    /** Normal text or standard icon — needs at least 4.5:1 contrast. */
    Normal,

    /** Large text (>18pt or >14pt bold) — needs at least 3:1 contrast. */
    LargeText,

    /** Disabled or inactive element — contrast requirement is relaxed. */
    Disabled,
}

/**
 * Labels for accessibility-critical content that describe the
 * current state of a toggle or action.
 *
 * TODO: Migrate to string resources for i18n support when the
 *  string-resource pattern is established in the project.
 *  These constants are used directly in `Modifier.semantics`
 *  which accepts plain strings, but a resource-aware helper
 *  should replace them for localized builds.
 */
object AccessibilityLabels {
    const val Play = "Play"
    const val Pause = "Pause"
    const val Resume = "Resume"
    const val Favorite = "Toggle favorite"
    const val FavoriteActive = "Remove from favorites"
    const val WatchLater = "Add to watch later"
    const val WatchLaterActive = "Remove from watch later"
    const val Download = "Download"
    const val DownloadActive = "Downloaded"
    const val DownloadProgress = "Download in progress"
    const val Search = "Search"
    const val Back = "Navigate back"
    const val Close = "Close"
    const val Menu = "Menu"
    const val Settings = "Settings"
    const val Diagnostics = "Diagnostics"
    const val Library = "Library"
    const val DownloadsScreen = "Downloads"
    const val OfflineLibrary = "Offline library"
    const val Login = "Log in"
    const val Logout = "Log out"
    const val Retry = "Retry"
    const val NextEpisode = "Next episode"
    const val PreviousEpisode = "Previous episode"
    const val SeasonSelector = "Select season"
    const val Episode = "Episode"
    const val Movie = "Movie"
    const val Show = "TV show"
    const val Subtitle = "Subtitles"
    const val Quality = "Video quality"
    const val PlaybackSpeed = "Playback speed"
    const val DownloadQueue = "Download queue"
    const val StorageUsage = "Storage usage"

    private const val DEFAULT_RATING_MAX = 10.0

    /**
     * Returns an accessibility description for a numeric rating.
     */
    fun rating(value: Double, max: Double = DEFAULT_RATING_MAX): String =
        "Rating ${value.toInt()} out of ${max.toInt()}"

    /**
     * Returns an accessibility description for a progress value.
     */
    fun progress(current: Long, total: Long): String =
        "Progress ${formatMinutes(current)} of ${formatMinutes(total)}"

    private fun formatMinutes(seconds: Long): String {
        val duration = seconds.seconds
        val hours = duration.inWholeHours
        val minutes = duration.inWholeMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
}
