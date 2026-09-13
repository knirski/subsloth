package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Material 3 window width size class, for layout decisions in shared screens.
 *
 * Thresholds are in density-independent pixels, so the same values apply on
 * phones, tablets, desktop windows, and the web viewport.
 */
enum class WindowWidthClass {
    /** Phones and narrow desktop windows (< 600dp). */
    COMPACT,

    /** Large phones, tablets, and medium desktop windows (600dp-840dp). */
    MEDIUM,

    /** Tablets in landscape, desktop windows, and TV (>= 840dp). */
    EXPANDED,
}

/** Pure width classification, exposed for tests. */
fun windowWidthClassOf(widthDp: Float): WindowWidthClass = when {
    widthDp < COMPACT_MAX_WIDTH_DP -> WindowWidthClass.COMPACT
    widthDp < MEDIUM_MAX_WIDTH_DP -> WindowWidthClass.MEDIUM
    else -> WindowWidthClass.EXPANDED
}

/**
 * The current window width class, measured from the host window/viewport.
 *
 * Desktop and web windows resize freely, so screens that care about available
 * width should branch on this instead of the device form factor.
 */
@Composable
@ReadOnlyComposable
fun currentWindowWidthClass(): WindowWidthClass {
    val density = LocalDensity.current
    val widthDp = with(density) { LocalWindowInfo.current.containerSize.width.toDp().value }
    return windowWidthClassOf(widthDp)
}

private const val COMPACT_MAX_WIDTH_DP = 600f
private const val MEDIUM_MAX_WIDTH_DP = 840f
