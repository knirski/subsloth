package net.subsloth.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
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
 * Requests focus for this element when it appears on TV, giving each screen a
 * deterministic initial D-pad target (back button, search action). A no-op
 * elsewhere.
 *
 * The request is retried over a few frames: the very first effect run can
 * happen before the focus target exists (or before the window is ready), and
 * a single fire-and-forget request would be lost.
 */
@Composable
fun Modifier.tvInitialFocus(): Modifier {
    if (!LocalIsTelevision.current) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        repeat(MAX_FOCUS_ATTEMPTS) {
            if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            withFrameNanos { }
        }
    }
    return this.then(Modifier.focusRequester(requester))
}

private const val MAX_FOCUS_ATTEMPTS = 3
private val TvOverscanPadding = 48.dp
