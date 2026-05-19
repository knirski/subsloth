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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.subsloth.core.domain.policy.PlaybackSpeed
import net.subsloth.core.model.media.Subtitle
import net.subsloth.feature.player.R

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
            PlayerContent(
                state = s,
                modifier = modifier,
                onTogglePlayPause = viewModel::togglePlayPause,
                onSeek = viewModel::seekTo,
                onSetSpeed = viewModel::setPlaybackSpeed,
                onSelectSubtitle = viewModel::selectSubtitle,
                onDismissNextEpisode = viewModel::dismissNextEpisode,
                onPlayNextEpisode = viewModel::playNextEpisode,
                onRetry = viewModel::retryPlayback,
                onNavigateBack = onNavigateBack,
                onNavigateToAuthRepair = onNavigateToAuthRepair,
            )
        }
    }
}

@Composable
private fun PlayerContent(
    state: PlayerUiState.Content,
    modifier: Modifier = Modifier,
    onTogglePlayPause: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSetSpeed: (Float) -> Unit = {},
    onSelectSubtitle: (Subtitle?) -> Unit = {},
    onDismissNextEpisode: () -> Unit = {},
    onPlayNextEpisode: () -> Unit = {},
    onRetry: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateToAuthRepair: () -> Unit = {},
) {
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var draggingPosition by remember { mutableStateOf<Float?>(null) }

    Box(
        modifier = modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (state.error != null) {
            ErrorContent(
                error = state.error,
                isAuthError = state.authFailed,
                onRetry = onRetry,
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

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = state.title,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = formatTime(state.positionSeconds),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )

            if (state.durationSeconds > 0) {
                Slider(
                    value = draggingPosition ?: state.positionSeconds.toFloat(),
                    onValueChange = { draggingPosition = it },
                    onValueChangeFinished = {
                        onSeek((draggingPosition ?: state.positionSeconds.toFloat()).toLong())
                        draggingPosition = null
                    },
                    valueRange = 0f..state.durationSeconds.toFloat(),
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
                onTogglePlayPause = onTogglePlayPause,
                onToggleSpeed = { showSpeedPicker = !showSpeedPicker },
                onToggleSubtitles = { showSubtitlePicker = !showSubtitlePicker },
            )

            if (showSpeedPicker) {
                SpeedPicker(
                    currentSpeed = state.playbackSpeed,
                    onSelect = { speed ->
                        onSetSpeed(speed)
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
        }
    }
}

@Composable
private fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleSubtitles: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(onClick = onTogglePlayPause) {
            Text(if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play))
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onToggleSpeed) {
            Text(stringResource(R.string.player_speed))
        }
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedButton(onClick = onToggleSubtitles) {
            Text(stringResource(R.string.player_subtitles))
        }
    }
}

@Composable
private fun SpeedPicker(currentSpeed: Float, onSelect: (Float) -> Unit) {
    Column(modifier = Modifier.background(Color.DarkGray).padding(8.dp)) {
        PlaybackSpeed.entries.forEach { speed ->
            Button(
                onClick = { onSelect(speed.value) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Text(
                    text = "${speed.value}x",
                    color = if (speed.value == currentSpeed) Color.Yellow else Color.White,
                )
            }
        }
    }
}

@Composable
private fun SubtitlePicker(subtitles: List<Subtitle>, selected: Subtitle?, onSelect: (Subtitle?) -> Unit) {
    Column(modifier = Modifier.background(Color.DarkGray).padding(8.dp)) {
        Button(
            onClick = { onSelect(null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        ) {
            Text(
                stringResource(R.string.player_subtitles_off),
                color = if (selected ==
                    null
                ) {
                    Color.Yellow
                } else {
                    Color.White
                },
            )
        }
        subtitles.forEach { subtitle ->
            Button(
                onClick = { onSelect(subtitle) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            ) {
                Text(
                    text = subtitle.languageDisplayName ?: subtitle.language.value,
                    color = if (selected?.language == subtitle.language) Color.Yellow else Color.White,
                )
            }
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
            text = stringResource(R.string.player_next_episode),
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onPlay, modifier = Modifier.width(200.dp)) {
            Text(stringResource(R.string.player_play))
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onDismiss, modifier = Modifier.width(200.dp)) {
            Text(stringResource(R.string.player_cancel))
        }
    }
}

@Composable
private fun ErrorContent(
    error: String,
    isAuthError: Boolean,
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToAuthRepair: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isAuthError) {
                stringResource(
                    R.string.player_session_expired,
                )
            } else {
                stringResource(R.string.player_playback_error)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(24.dp))
        if (isAuthError) {
            Button(onClick = onNavigateToAuthRepair, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.player_sign_in_again))
            }
        } else {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.player_retry))
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onNavigateBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.player_back_to_details))
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
