@file:Suppress("TooManyFunctions", "ktlint:standard:no-wildcard-imports")

package net.subsloth.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.delay
import net.subsloth.core.media.PlayerBridgeSurface
import net.subsloth.core.media.PlayerEvent
import net.subsloth.core.model.media.Subtitle
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.player.generated.resources.*

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToAuthRepair: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    fullscreen: PlayerFullscreenControl? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val s = state) {
        is PlayerUiState.Loading -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is PlayerUiState.Content -> {
            PlayerBridgeSurface(
                modifier = modifier.fillMaxSize(),
                playCommands = viewModel.playCommands,
                onEvent = { event ->
                    when (event) {
                        is PlayerEvent.Snapshot -> viewModel.onPlayerSnapshot(event.value)
                        is PlayerEvent.Error -> viewModel.onPlayerError(event.message)
                        is PlayerEvent.PlaybackEnded -> viewModel.onPlaybackEnded()
                        is PlayerEvent.Attached -> viewModel.onPlayerAttached()
                    }
                },
                overlay = { playerState ->
                    PlayerOverlay(
                        state = s,
                        playerState = playerState,
                        onRetry = { viewModel.retryPlayback() },
                        onRetryWithRefresh = { viewModel.retryWithRefresh() },
                        onRetrySubtitle = { viewModel.retrySubtitleLoad() },
                        onPlayNextEpisode = { viewModel.playNextEpisode() },
                        onDismissNextEpisode = { viewModel.dismissNextEpisode() },
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onSelectSubtitle = { viewModel.selectSubtitle(it) },
                        onSelectQuality = { viewModel.selectQuality(it) },
                        onNavigateBack = onNavigateBack,
                        onNavigateToAuthRepair = onNavigateToAuthRepair,
                        onFullscreenChanged = onFullscreenChanged,
                        isFullscreen = fullscreen?.isFullscreen ?: playerState.isFullscreen,
                        onToggleFullscreen = fullscreen?.onToggle ?: playerState::toggleFullscreen,
                    )
                },
            )
        }
    }
}

@Composable
fun PlayerOverlay(
    state: PlayerUiState.Content,
    playerState: VideoPlayerState,
    onRetry: () -> Unit = {},
    onRetryWithRefresh: () -> Unit = {},
    onRetrySubtitle: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onDismissNextEpisode: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSelectSubtitle: (Subtitle?) -> Unit = {},
    onSelectQuality: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToAuthRepair: () -> Unit = {},
    onFullscreenChanged: (Boolean) -> Unit = {},
    isFullscreen: Boolean = playerState.isFullscreen,
    onToggleFullscreen: () -> Unit = playerState::toggleFullscreen,
) {
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var draggingPosition by remember { mutableStateOf<Float?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

    // The player library owns the fullscreen mode (it opens a dedicated
    // video window on desktop and lays the video out full-screen on web and
    // Android); report its state so hosts can apply platform window effects
    // (e.g. Android landscape + immersive bars, hiding the web demo banner).
    LaunchedEffect(playerState.isFullscreen) {
        onFullscreenChanged(playerState.isFullscreen)
    }
    // Leaving the player while fullscreen must not leave hosts stuck in the
    // fullscreen state (e.g. the web demo banner hidden).
    DisposableEffect(Unit) {
        onDispose { onFullscreenChanged(false) }
    }

    // Auto-hide the chrome a few seconds into uninterrupted playback. Any
    // user interaction (tap, drag, open picker) either re-shows the
    // controls or changes a tracked key, restarting this timer; paused
    // playback always keeps the controls on screen.
    LaunchedEffect(
        controlsVisible,
        state.isPlaying,
        showSpeedPicker,
        showSubtitlePicker,
        showQualityPicker,
        draggingPosition,
    ) {
        if (controlsVisible && state.isPlaying && showSpeedPicker.not() && showSubtitlePicker.not() &&
            showQualityPicker.not() && draggingPosition == null
        ) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controlsVisible = false
        } else if (!state.isPlaying) {
            // Paused playback always keeps the chrome on screen.
            controlsVisible = true
        }
    }

    // No background here: the video surface renders behind the Compose
    // canvas (e.g. zIndex -1 on web), so the overlay must stay transparent
    // for frames to show through. Error and prompt screens draw their own
    // opaque backgrounds.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // Taps on the video toggle the chrome; taps on controls are
                // consumed by their own handlers and never reach here.
                detectTapGestures { controlsVisible = !controlsVisible }
            },
    ) {
        if (state.playbackError != null) {
            ErrorContent(
                playbackError = state.playbackError,
                playbackMode = state.playbackMode,
                onRetry = onRetry,
                onRetryWithRefresh = onRetryWithRefresh,
                onNavigateBack = onNavigateBack,
                onNavigateToAuthRepair = onNavigateToAuthRepair,
            )
            return
        }

        if (state.showNextEpisodePrompt) {
            NextEpisodePrompt(
                countdownSeconds = state.nextEpisodeCountdownSeconds,
                onPlay = onPlayNextEpisode,
                onDismiss = onDismissNextEpisode,
            )
            return
        }

        if (playerState.isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
            return
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.qualityFallbackNotice != null) {
                Text(
                    text = state.qualityFallbackNotice.resolve(),
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.subtitleFallbackNotice != null) {
                Text(
                    text = state.subtitleFallbackNotice.resolve(),
                    color = Color.Yellow,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.subtitleLoadFailed) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.player_subtitle_load_failed),
                        color = Color.Yellow,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onRetrySubtitle) {
                        Text(stringResource(Res.string.player_retry))
                    }
                }
            }

            // Push controls to the bottom of the screen
            Spacer(modifier = Modifier.weight(1f))

            // Subtitles render above the control bar (or above a bottom
            // margin when the controls are hidden), never under it.
            SubtitleText(
                cues = state.subtitleCues,
                playerState = playerState,
                durationSeconds = state.durationSeconds,
                modifier = Modifier.padding(bottom = if (controlsVisible) 0.dp else 32.dp),
            )

            if (controlsVisible) {
                val barDisplaySeconds = draggingPosition?.let {
                    (it / 1000f * state.durationSeconds).toLong()
                } ?: state.positionSeconds
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Title and quit control live inside the bar and share
                    // its auto-hide behavior.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        Text(
                            text = state.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onNavigateBack) {
                            Text(stringResource(Res.string.player_close))
                        }
                    }

                    Text(
                        text = formatTime(barDisplaySeconds),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    if (state.durationSeconds > 0) {
                        Slider(
                            value = draggingPosition ?: playerState.sliderPos,
                            onValueChange = { value ->
                                draggingPosition = value
                                playerState.seekStart(value)
                            },
                            onValueChangeFinished = {
                                playerState.seekFinished()
                                draggingPosition = null
                            },
                            valueRange = 0f..1000f,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )

                        Text(
                            text = formatTime(state.durationSeconds),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    PlaybackControls(
                        isPlaying = state.isPlaying,
                        onTogglePlayPause = {
                            if (playerState.isPlaying) playerState.pause() else playerState.play()
                        },
                        onToggleSpeed = { showSpeedPicker = !showSpeedPicker },
                        onToggleSubtitles = { showSubtitlePicker = !showSubtitlePicker },
                        onToggleQuality = { showQualityPicker = !showQualityPicker },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                    )
                }

                if (showSpeedPicker) {
                    SpeedPicker(
                        currentSpeed = state.playbackSpeed,
                        onSelect = { speed ->
                            playerState.playbackSpeed = speed
                            onSetPlaybackSpeed(speed)
                            showSpeedPicker = false
                        },
                    )
                }

                if (showSubtitlePicker) {
                    SubtitlePicker(
                        subtitles = state.availableSubtitles,
                        selected = state.selectedSubtitle,
                        onSelect = { subtitle ->
                            onSelectSubtitle(subtitle)
                            showSubtitlePicker = false
                        },
                    )
                }

                if (showQualityPicker) {
                    val isAdaptive = state.availableQualities.none { it.url != null }
                    if (isAdaptive) {
                        AutoQualityNotice(onDismiss = { showQualityPicker = false })
                    } else {
                        QualityPicker(
                            qualities = state.availableQualities,
                            selectedLabel = state.selectedQualityLabel,
                            onSelect = { label ->
                                onSelectQuality(label)
                                showQualityPicker = false
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Controls hide this long into uninterrupted playback; a tap brings them back. */
private const val CONTROLS_AUTO_HIDE_MS = 4_000L
