@file:Suppress("TooManyFunctions", "ktlint:standard:no-wildcard-imports")

package net.subsloth.player

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
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

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
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
                onPlay = onPlayNextEpisode,
                onDismiss = onDismissNextEpisode,
            )
            return
        }

        if (playerState.isLoading) {
            CircularProgressIndicator(color = Color.White)
            return
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )

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

            val displaySeconds = draggingPosition?.let {
                (it / 1000f * state.durationSeconds).toLong()
            } ?: state.positionSeconds
            Text(
                text = formatTime(displaySeconds),
                color = Color.White,
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
                    color = Color.White,
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
                        if (subtitle != null) {
                            val track = SubtitleMapper.toSubtitleTrack(subtitle)
                            if (track != null) {
                                playerState.selectSubtitleTrack(track)
                                onSelectSubtitle(subtitle)
                            }
                        } else {
                            playerState.disableSubtitles()
                            onSelectSubtitle(null)
                        }
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
private fun NextEpisodePrompt(onPlay: () -> Unit, onDismiss: () -> Unit) {
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
        modifier = Modifier.fillMaxSize().padding(24.dp),
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
