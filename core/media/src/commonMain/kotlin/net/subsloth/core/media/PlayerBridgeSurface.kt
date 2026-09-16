package net.subsloth.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import io.github.kdroidfilter.composemediaplayer.InitialPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerError
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import io.github.kdroidfilter.composemediaplayer.VideoPlayerSurface
import io.github.kdroidfilter.composemediaplayer.rememberVideoPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.isActive

/** Poll interval mirroring the underlying player's ~250 ms position updates. */
private const val SNAPSHOT_POLL_INTERVAL_MS = 250L

/** How long the muted start gets to begin before the volume is restored anyway. */
private const val MUTED_START_WAIT_ATTEMPTS = 40
private const val MUTED_START_WAIT_INTERVAL_MS = 50L

/** How long the first play command waits for the player's `<video>` element. */
private const val BROWSER_VIDEO_WAIT_ATTEMPTS = 40
private const val BROWSER_VIDEO_WAIT_INTERVAL_MS = 50L

/**
 * How long a resume seek waits for the player to report a non-zero
 * duration. The wait is generous because browsers may block autoplay and
 * only report the duration once the user has started playback manually.
 */
private const val SEEK_DURATION_WAIT_ATTEMPTS = 3_000
private const val SEEK_DURATION_WAIT_INTERVAL_MS = 100L

@Composable
fun PlayerBridgeSurface(
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    subtitleTextStyle: TextStyle = TextStyle.Default,
    subtitleBackground: Color = Color.Transparent,
    playCommands: Flow<PlayCommand> = emptyFlow(),
    onEvent: (PlayerEvent) -> Unit = {},
    overlay: @Composable (VideoPlayerState) -> Unit = {},
) {
    val playerState = rememberVideoPlayerState()
    SyncPlayerFullscreenWithBrowser(playerState)
    val currentOnEvent = rememberUpdatedState(onEvent)

    playerState.subtitleTextStyle = subtitleTextStyle
    playerState.subtitleBackgroundColor = subtitleBackground

    LaunchedEffect(playerState) {
        var previous: PlayerSnapshot? = null
        while (isActive) {
            // The web player implementation backs currentTime/duration with
            // plain vars, so snapshotFlow would never re-emit. Poll instead
            // and forward only actual changes.
            val snapshot = PlayerSnapshot(
                positionSeconds = playerState.currentTime.toLong(),
                durationSeconds = playerState.duration.toLong(),
                isPlaying = playerState.isPlaying,
                isLoading = playerState.isLoading,
            )
            if (snapshot != previous) {
                previous = snapshot
                currentOnEvent.value(PlayerEvent.Snapshot(snapshot))
            }
            delay(SNAPSHOT_POLL_INTERVAL_MS)
        }
    }

    LaunchedEffect(playerState) {
        snapshotFlow { playerState.error }
            .collect { error ->
                if (error != null) {
                    val msg = when (error) {
                        is VideoPlayerError.CodecError -> error.message
                        is VideoPlayerError.NetworkError -> error.message
                        is VideoPlayerError.SourceError -> error.message
                        is VideoPlayerError.UnknownError -> error.message
                    }
                    currentOnEvent.value(PlayerEvent.Error(msg))
                    playerState.clearError()
                }
            }
    }

    LaunchedEffect(playerState) {
        playerState.onPlaybackEnded = {
            currentOnEvent.value(PlayerEvent.PlaybackEnded)
        }
    }

    LaunchedEffect(playerState, playCommands) {
        playCommands.collectLatest { cmd ->
            playerState.openUri(cmd.url, InitialPlayerState.PAUSE)
            cmd.subtitleTrack?.let { playerState.selectSubtitleTrack(it) }
            startPlaybackMutedOnWeb(playerState)
            if (cmd.positionSeconds > 0L) {
                // The seek needs a non-zero duration, and on web the duration
                // is only reported through timeupdate events — which fire
                // once playback has started. Browsers may block unmuted
                // autoplay, so playback (and therefore the duration) can
                // arrive only when the user presses play; wait for it rather
                // than giving up after a short window. Native platforms
                // report the duration immediately and skip the wait.
                var attempts = 0
                while (attempts < SEEK_DURATION_WAIT_ATTEMPTS && playerState.duration <= 0.0) {
                    delay(SEEK_DURATION_WAIT_INTERVAL_MS)
                    attempts++
                }
                if (playerState.duration > 0.0) {
                    val seekValue =
                        (cmd.positionSeconds.toFloat() / playerState.duration.toFloat() * 1000f)
                            .coerceIn(0f, 1000f)
                    playerState.seekTo(seekValue)
                }
            }
        }
    }

    VideoPlayerSurface(
        playerState = playerState,
        modifier = modifier,
        contentScale = contentScale,
    ) {
        overlay(playerState)
    }
}

/**
 * Starts playback with the media element muted, then unmutes once playback
 * is running.
 *
 * Two browser-specific hazards are handled here:
 * - the player library attaches its `<video>` element asynchronously, so
 *   the first `play()` can be lost unless the element is awaited; and
 * - browsers reject an unmuted `play()` without transient user activation,
 *   while the library reports `isPlaying` optimistically, so a
 *   "try, then detect rejection and retry" dance cannot reliably see the
 *   rejection. Muting before `play()` is always allowed by autoplay
 *   policies; unmuting after playback starts keeps the audio.
 *
 * Both hooks are no-ops on native platforms, where the element is always
 * ready and play starts immediately.
 */
private suspend fun startPlaybackMutedOnWeb(playerState: VideoPlayerState) {
    awaitBrowserVideoElement()
    setBrowserVideoMuted(true)
    playerState.play()
    awaitPlaying(playerState, MUTED_START_WAIT_ATTEMPTS, MUTED_START_WAIT_INTERVAL_MS)
    setBrowserVideoMuted(false)
}

private suspend fun awaitBrowserVideoElement(): Boolean {
    repeat(BROWSER_VIDEO_WAIT_ATTEMPTS) {
        if (hasBrowserVideoElement()) return true
        delay(BROWSER_VIDEO_WAIT_INTERVAL_MS)
    }
    return hasBrowserVideoElement()
}

private suspend fun awaitPlaying(playerState: VideoPlayerState, attempts: Int, intervalMs: Long): Boolean {
    repeat(attempts) {
        if (playerState.isPlaying) return true
        delay(intervalMs)
    }
    return playerState.isPlaying
}
