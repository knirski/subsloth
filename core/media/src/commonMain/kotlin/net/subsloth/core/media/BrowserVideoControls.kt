package net.subsloth.core.media

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState

/**
 * Browser `<video>` element controls for autoplay-policy compliance on
 * Wasm.
 *
 * The player library creates the DOM element asynchronously after
 * `openUri`, so the first play command must wait for it. Autoplay
 * policies also check the element's `muted` flag (not its volume), so the
 * muted start must set it directly on the DOM element — the library
 * exposes no mute setter.
 *
 * Native platforms return the element-present default and no-op the mute:
 * their players start immediately and never hit the policy.
 */
internal expect fun hasBrowserVideoElement(): Boolean

internal expect fun setBrowserVideoMuted(muted: Boolean)

/**
 * Clears [playerState]'s fullscreen flag when the browser leaves fullscreen
 * on its own (Esc or the browser chrome).
 *
 * The player library's own `fullscreenchange` listener captures the video
 * element before it exists, so it never clears the flag; without this the
 * player would stay in its fullscreen layout after the browser exited.
 * Native platforms no-op: their fullscreen mode cannot change externally.
 */
@Composable
internal expect fun SyncPlayerFullscreenWithBrowser(playerState: VideoPlayerState)
