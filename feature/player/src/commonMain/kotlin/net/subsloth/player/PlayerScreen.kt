@file:Suppress("TooManyFunctions", "ktlint:standard:no-wildcard-imports")

package net.subsloth.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.delay
import net.subsloth.core.domain.policy.PlaybackSpeed
import net.subsloth.core.media.PlayerBridgeSurface
import net.subsloth.core.media.PlayerEvent
import net.subsloth.core.media.SubtitleMapper
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.PlaybackMode
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.player.generated.resources.*

@Composable
private fun PlayerUiState.Notice.resolve(): String = when (this) {
    is PlayerUiState.Notice.Localized.NoSubtitles -> stringResource(Res.string.player_no_subtitles)
    is PlayerUiState.Notice.Localized.SubtitleIn -> stringResource(Res.string.player_subtitle_in, language)
    is PlayerUiState.Notice.Localized.QualityReduced -> stringResource(Res.string.player_quality_reduced, quality)
    is PlayerUiState.Notice.Raw -> message
}

@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    onNavigateBack: () -> Unit = {},
    onNavigateToAuthRepair: () -> Unit = {},
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
                    }
                },
                overlay = { playerState ->
                    PlayerOverlay(
                        state = s,
                        playerState = playerState,
                        onRetry = { viewModel.retryPlayback() },
                        onRetryWithRefresh = { viewModel.retryWithRefresh() },
                        onPlayNextEpisode = { viewModel.playNextEpisode() },
                        onDismissNextEpisode = { viewModel.dismissNextEpisode() },
                        onSetPlaybackSpeed = { viewModel.setPlaybackSpeed(it) },
                        onSelectSubtitle = { viewModel.selectSubtitle(it) },
                        onSelectQuality = { viewModel.selectQuality(it) },
                        onNavigateBack = onNavigateBack,
                        onNavigateToAuthRepair = onNavigateToAuthRepair,
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
    onPlayNextEpisode: () -> Unit = {},
    onDismissNextEpisode: () -> Unit = {},
    onSetPlaybackSpeed: (Float) -> Unit = {},
    onSelectSubtitle: (Subtitle?) -> Unit = {},
    onSelectQuality: (String) -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToAuthRepair: () -> Unit = {},
) {
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }
    var draggingPosition by remember { mutableStateOf<Float?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }

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

/**
 * Renders the active subtitle cue in Compose above the control bar,
 * replacing the player library's native subtitle layer (which is drawn
 * under our overlay).
 */
@Composable
private fun SubtitleText(
    cues: List<SubtitleCue>,
    playerState: VideoPlayerState,
    durationSeconds: Long,
    modifier: Modifier = Modifier,
) {
    if (cues.isEmpty()) return
    val durationMs = if (durationSeconds > 0) durationSeconds * 1000 else (playerState.duration * 1000).toLong()
    if (durationMs <= 0) return
    val positionMs = (playerState.sliderPos / 1000f * durationMs).toLong()
    val activeCue = cues.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs } ?: return
    Text(
        text = activeCue.text,
        color = Color.White,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        modifier = modifier
            .padding(horizontal = 32.dp)
            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onToggleQuality: () -> Unit = {},
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(onClick = onTogglePlayPause) {
            Text(if (isPlaying) stringResource(Res.string.player_pause) else stringResource(Res.string.player_play))
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onToggleSpeed) {
            Text(stringResource(Res.string.player_speed))
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onToggleSubtitles) {
            Text(stringResource(Res.string.player_subtitles))
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onToggleQuality) {
            Text(stringResource(Res.string.player_quality))
        }
    }
}

@Composable
private fun SpeedPicker(currentSpeed: Float, onSelect: (Float) -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        PlaybackSpeed.entries.forEach { speed ->
            val isSelected = speed.value == currentSpeed
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Button(
                onClick = { onSelect(speed.value) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            ) {
                Text(text = "${speed.value}x")
            }
        }
    }
}

@Composable
private fun QualityPicker(qualities: List<Quality>, selectedLabel: String?, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        qualities.forEach { quality ->
            val label = quality.info.label ?: quality.info.resolution.label
            val isSelected = label == selectedLabel
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Button(
                onClick = { onSelect(label) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor,
                ),
            ) {
                Text(text = label)
            }
        }
    }
}

@Composable
private fun SubtitlePicker(subtitles: List<Subtitle>, selected: Subtitle?, onSelect: (Subtitle?) -> Unit) {
    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        val offContainerColor = if (selected == null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
        val offContentColor = if (selected == null) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Button(
            onClick = { onSelect(null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = offContainerColor,
                contentColor = offContentColor,
            ),
        ) {
            Text(stringResource(Res.string.player_subtitles_off))
        }
        subtitles.forEach { subtitle ->
            val supported = SubtitleMapper.isFormatSupported(subtitle.format)
            val isSelected = selected?.language == subtitle.language
            val containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
            val contentColor = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            Button(
                onClick = {
                    if (supported) onSelect(subtitle)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = if (supported) contentColor else contentColor.copy(alpha = 0.5f),
                ),
                enabled = supported,
            ) {
                Text(
                    text = subtitle.languageDisplayName ?: subtitle.language.value,
                )
            }
        }
    }
}

@Composable
private fun AutoQualityNotice(onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.player_quality_auto),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(8.dp),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.player_close))
        }
    }
}

@Composable
private fun NextEpisodePrompt(countdownSeconds: Int?, onPlay: () -> Unit, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(Res.string.player_next_episode),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        countdownSeconds?.let { seconds ->
            Spacer(modifier = Modifier.height(8.dp))
            // Counts down from 10 to 1; auto-plays the next episode at 0
            // unless the user cancels.
            Text(
                text = stringResource(Res.string.player_next_episode_countdown, seconds),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onPlay, modifier = Modifier.width(200.dp)) {
            Text(stringResource(Res.string.player_play))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onDismiss, modifier = Modifier.width(200.dp)) {
            Text(stringResource(Res.string.player_cancel))
        }
    }
}

@Composable
private fun ErrorContent(
    playbackError: PlaybackError,
    playbackMode: PlaybackMode,
    onRetry: () -> Unit,
    onRetryWithRefresh: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAuthRepair: () -> Unit,
) {
    val isAuthError = playbackError is PlaybackError.AuthFailure
    Column(
        modifier = Modifier
            .fillMaxSize()
            // The video renders behind the Compose canvas, so the error
            // screen needs its own surface to stay readable over frames.
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isAuthError) {
                stringResource(Res.string.player_session_expired)
            } else {
                stringResource(Res.string.player_playback_error)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        val errorMessage = when (playbackError) {
            is PlaybackError.AuthFailure -> stringResource(Res.string.player_session_expired)
            is PlaybackError.StreamUrlExpired -> stringResource(Res.string.player_stream_expired)
            is PlaybackError.Recoverable -> stringResource(Res.string.player_playback_error)
        }
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isAuthError) {
            Button(onClick = onNavigateToAuthRepair, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.player_sign_in_again))
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.player_retry))
            }
            if (playbackMode == PlaybackMode.ONLINE) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onRetryWithRefresh, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.player_retry_with_fresh_link))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.player_back_to_details))
        }
    }
}

private fun formatTime(seconds: Long): String {
    val clamped = maxOf(0L, seconds)
    val h = clamped / 3600
    val m = clamped % 3600 / 60
    val s = clamped % 60
    fun pad(v: Long) = if (v < 10) "0$v" else "$v"
    return if (h > 0) "$h:${pad(m)}:${pad(s)}" else "${pad(m)}:${pad(s)}"
}
