@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.web

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.browser.document
import net.subsloth.core.ui.FullscreenController
import org.w3c.dom.events.Event

/**
 * Browser fullscreen through the Fullscreen API.
 *
 * The document element (not the `<video>`) goes fullscreen so the Compose
 * player overlay and its controls stay visible. A `fullscreenchange`
 * listener keeps the state in sync when the user leaves fullscreen with Esc
 * or the browser chrome.
 */
internal class WebFullscreenController : FullscreenController {
    private val fullscreenState = mutableStateOf(browserIsFullscreen())

    private val listener: (Event) -> Unit = {
        fullscreenState.value = browserIsFullscreen()
    }

    override val isFullscreen: State<Boolean> = fullscreenState

    fun start() {
        document.addEventListener("fullscreenchange", listener)
    }

    fun stop() {
        document.removeEventListener("fullscreenchange", listener)
    }

    override fun toggle() {
        if (fullscreenState.value) {
            browserExitFullscreen()
        } else {
            browserEnterFullscreen()
        }
    }
}

@JsFun("() => document.fullscreenElement != null")
private external fun browserIsFullscreen(): Boolean

@JsFun("() => { document.documentElement.requestFullscreen?.().catch(() => {}); }")
private external fun browserEnterFullscreen()

@JsFun("() => { if (document.fullscreenElement) { document.exitFullscreen?.().catch(() => {}); } }")
private external fun browserExitFullscreen()
