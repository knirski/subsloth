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

/** How long a direct unmuted `play()` gets to start before the muted retry. */
private const val AUTOPLAY_DIRECT_WAIT_ATTEMPTS = 20
private const val AUTOPLAY_DIRECT_WAIT_INTERVAL_MS = 50L

/** How long the muted retry gets to start before the volume is restored anyway. */
private const val AUTOPLAY_MUTED_WAIT_ATTEMPTS = 40
private const val AUTOPLAY_MUTED_WAIT_INTERVAL_MS = 50L

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
            playerState.play()
            ensurePlaybackStarted(playerState)
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
 * Ensures playback actually starts under browser autoplay policies.
 *
 * Browsers reject an unmuted `play()` unless the page still holds
 * transient user activation. The details-screen Play tap is a gesture,
 * but stream resolution can outlive the activation window, leaving the
 * player paused. When the direct attempt does not start, retry with the
 * media element muted (always allowed by autoplay policies) and unmute
 * it once playback is running, so the user still hears audio. Policies
 * check the element's `muted` flag, not its volume, so this must go
 * through [setBrowserVideoMuted].
 *
 * Native players start immediately, so the retry never runs there.
 */
private suspend fun ensurePlaybackStarted(playerState: VideoPlayerState) {
    if (awaitPlaying(playerState, AUTOPLAY_DIRECT_WAIT_ATTEMPTS, AUTOPLAY_DIRECT_WAIT_INTERVAL_MS)) return
    setBrowserVideoMuted(true)
    playerState.play()
    awaitPlaying(playerState, AUTOPLAY_MUTED_WAIT_ATTEMPTS, AUTOPLAY_MUTED_WAIT_INTERVAL_MS)
    setBrowserVideoMuted(false)
}

private suspend fun awaitPlaying(playerState: VideoPlayerState, attempts: Int, intervalMs: Long): Boolean {
    repeat(attempts) {
        if (playerState.isPlaying) return true
        delay(intervalMs)
    }
    return playerState.isPlaying
}
