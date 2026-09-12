package net.subsloth.core.media

import androidx.compose.runtime.Composable
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState

@Suppress("UnusedParameter")
internal actual fun hasBrowserVideoElement(): Boolean = true

@Suppress("UnusedParameter")
internal actual fun setBrowserVideoMuted(muted: Boolean) = Unit

@Suppress("UnusedParameter")
@Composable
internal actual fun SyncPlayerFullscreenWithBrowser(playerState: VideoPlayerState) = Unit
