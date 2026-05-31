package net.subsloth.ui.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalWindowInfo

/**
 * Device form-factor classification based on screen width.
 *
 * These align with Material 3 WindowSizeClass values, but are
 * expressed as a domain enum so feature screens can make layout
 * decisions without importing window-size-class infrastructure.
 */
@Immutable
enum class DeviceFormFactor {
    /** Phone-sized screen (compact width). */
    Phone,

    /** Tablet-sized screen (medium or expanded width). */
    Tablet,

    /** TV-sized screen (expanded width, always landscape). */
    Tv,
}

/**
 * Returns the current [DeviceFormFactor] based on the screen width
 * in density-independent pixels (dp).
 *
 * Thresholds follow Material 3 window size classes:
 * - Compact (Phone): < 600dp
 * - Medium (Tablet portrait/small tablet): 600dp – 840dp
 * - Expanded (Tablet landscape / TV): > 840dp
 */
@Composable
@ReadOnlyComposable
fun currentDeviceFormFactor(): DeviceFormFactor {
    val widthDp = LocalWindowInfo.current.containerSize.width
    return when {
        widthDp < COMPACT_WIDTH_THRESHOLD -> DeviceFormFactor.Phone
        widthDp < MEDIUM_WIDTH_THRESHOLD -> DeviceFormFactor.Tablet
        else -> DeviceFormFactor.Tv
    }
}

/**
 * Returns true when the current window is at least medium width (≥600dp).
 */
@Composable
@ReadOnlyComposable
fun isTabletOrWider(): Boolean =
    LocalWindowInfo.current.containerSize.width >= COMPACT_WIDTH_THRESHOLD

private const val COMPACT_WIDTH_THRESHOLD = 600
private const val MEDIUM_WIDTH_THRESHOLD = 840
