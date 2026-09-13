package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether the app is running on an Android TV / leanback device.
 *
 * Provided by the Android root; the default keeps every other platform on the
 * phone/tablet layout. Shared screens use it for TV-only affordances
 * (overscan-safe padding, initial D-pad focus).
 */
val LocalIsTelevision = compositionLocalOf { false }

/**
 * Horizontal content padding that widens on TV so overscan does not clip
 * content at the screen edges.
 */
@Composable
@ReadOnlyComposable
fun tvSafeHorizontalPadding(default: Dp): Dp =
    if (LocalIsTelevision.current) maxOf(default, TvOverscanPadding) else default

/**
 * Requests focus when the element enters composition on TV, giving each
 * screen a deterministic initial D-pad target (back button, search action).
 * A no-op elsewhere.
 */
@Composable
fun Modifier.tvInitialFocus(): Modifier {
    val enabled = LocalIsTelevision.current
    val requester = remember { FocusRequester() }
    LaunchedEffect(enabled) {
        if (enabled) {
            runCatching { requester.requestFocus() }
        }
    }
    return this.then(Modifier.focusRequester(requester))
}

private val TvOverscanPadding = 48.dp
