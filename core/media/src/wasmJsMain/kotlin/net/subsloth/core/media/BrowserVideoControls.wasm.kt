@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package net.subsloth.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.browser.document
import org.w3c.dom.HTMLVideoElement
import org.w3c.dom.events.Event
import kotlin.js.unsafeCast

internal actual fun hasBrowserVideoElement(): Boolean = document.querySelectorAll("video").length > 0

internal actual fun setBrowserVideoMuted(muted: Boolean) {
    val videos = document.querySelectorAll("video")
    for (index in 0 until videos.length) {
        val element = videos.item(index) ?: continue
        element.unsafeCast<HTMLVideoElement>().muted = muted
    }
}

@Composable
internal actual fun SyncPlayerFullscreenWithBrowser(playerState: VideoPlayerState) {
    DisposableEffect(playerState) {
        val listener: (Event) -> Unit = {
            if (document.fullscreenElement == null && playerState.isFullscreen) {
                playerState.isFullscreen = false
            }
        }
        document.addEventListener("fullscreenchange", listener)
        onDispose { document.removeEventListener("fullscreenchange", listener) }
    }
}
