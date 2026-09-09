@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.browser.window
import org.w3c.dom.events.Event
import kotlin.js.JsNumber
import kotlin.js.toInt
import kotlin.js.toJsNumber

/**
 * Bridges the in-app Navigation3 back stack to browser history.
 *
 * Every entry the app pushes onto the browser history stack stores the
 * zero-based back-stack depth it represents. `popstate` events (browser back
 * and forward buttons) report that depth so the host can trim or restore the
 * app back stack.
 *
 * Depth-only state means a browser refresh realigns to the start destination
 * (see [rememberBrowserHistorySync]) and the browser forward button only
 * restores entries that were left via browser back within the same page load.
 */
internal fun pushHistoryDepth(depth: Int) {
    window.history.pushState(depth.toJsNumber(), "")
}

internal fun popHistory() {
    if (window.history.state != null) {
        window.history.back()
    }
}

private fun historyDepth(): Int = when (val state = window.history.state) {
    null -> 0
    is JsNumber -> state.toInt()
    else -> 0
}

/**
 * Listens for browser `popstate` events and reports the back-stack depth the
 * browser navigated to. Also realigns the current history entry to depth 0 so
 * a page refresh (which resets the in-app back stack) does not desync the two
 * stacks.
 */
@Composable
internal fun rememberBrowserHistorySync(onDepthChanged: (Int) -> Unit) {
    DisposableEffect(Unit) {
        window.history.replaceState(0.toJsNumber(), "")
        val listener: (Event) -> Unit = {
            onDepthChanged(historyDepth())
        }
        window.addEventListener("popstate", listener)
        onDispose { window.removeEventListener("popstate", listener) }
    }
}
