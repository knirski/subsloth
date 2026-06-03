package net.subsloth.core.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.flow.emptyFlow

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

    playerState.subtitleTextStyle = subtitleTextStyle
    playerState.subtitleBackgroundColor = subtitleBackground

    LaunchedEffect(playerState) {
        snapshotFlow {
            PlayerSnapshot(
                positionSeconds = playerState.currentTime.toLong(),
                durationSeconds = playerState.duration.toLong(),
                isPlaying = playerState.isPlaying,
                isLoading = playerState.isLoading,
            )
        }.collect { snapshot ->
            onEvent(PlayerEvent.Snapshot(snapshot))
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
                    onEvent(PlayerEvent.Error(msg))
                    playerState.clearError()
                }
            }
    }

    LaunchedEffect(playerState) {
        playerState.onPlaybackEnded = {
            onEvent(PlayerEvent.PlaybackEnded)
        }
    }

    LaunchedEffect(playerState) {
        playCommands.collect { cmd ->
            playerState.openUri(cmd.url, InitialPlayerState.PAUSE)
            if (cmd.positionSeconds > 0L) {
                var attempts = 0
                while (attempts < 100 && playerState.duration <= 0.0) {
                    delay(100)
                    attempts++
                }
                if (playerState.duration > 0.0) {
                    val seekValue =
                        (cmd.positionSeconds.toFloat() / playerState.duration.toFloat() * 1000f)
                            .coerceIn(0f, 1000f)
                    playerState.seekTo(seekValue)
                }
            }
            cmd.subtitleTrack?.let { playerState.selectSubtitleTrack(it) }
            playerState.play()
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
