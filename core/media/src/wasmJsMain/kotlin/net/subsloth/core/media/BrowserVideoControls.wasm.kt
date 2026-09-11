@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.core.media

import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement
import kotlin.js.unsafeCast

internal actual fun hasBrowserVideoElement(): Boolean = document.querySelectorAll("video").length > 0

internal actual fun setBrowserVideoMuted(muted: Boolean) {
    val videos = document.querySelectorAll("video")
    for (index in 0 until videos.length) {
        val element = videos.item(index) ?: continue
        element.unsafeCast<HTMLVideoElement>().muted = muted
    }
}
