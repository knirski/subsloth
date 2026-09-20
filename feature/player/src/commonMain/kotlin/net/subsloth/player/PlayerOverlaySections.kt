@file:Suppress("TooManyFunctions", "ktlint:standard:no-wildcard-imports")

package net.subsloth.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import net.subsloth.core.domain.policy.PlaybackSpeed
import net.subsloth.core.media.SubtitleMapper
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.core.model.playback.PlaybackMode
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.player.generated.resources.*

@Composable
internal fun PlayerUiState.Notice.resolve(): String = when (this) {
    is PlayerUiState.Notice.Localized.NoSubtitles -> stringResource(Res.string.player_no_subtitles)
    is PlayerUiState.Notice.Localized.SubtitleIn -> stringResource(Res.string.player_subtitle_in, language)
    is PlayerUiState.Notice.Localized.QualityReduced -> stringResource(Res.string.player_quality_reduced, quality)
    is PlayerUiState.Notice.Raw -> message
}

/**
 * Host-owned fullscreen state.
 *
 * Android keeps the video in the activity window and hides the system bars
 * natively instead of opening the player library's fullscreen [Dialog], which
 * cannot cover the status bar. Platforms that use the library's fullscreen
 * leave this null.
 */
@Immutable
data class PlayerFullscreenControl(val isFullscreen: Boolean, val onToggle: () -> Unit)

/**
 * Renders the active subtitle cue in Compose above the control bar,
 * replacing the player library's native subtitle layer (which is drawn
 * under our overlay).
 */
@Composable
internal fun SubtitleText(
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
internal fun PlaybackControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onToggleSpeed: () -> Unit,
    onToggleSubtitles: () -> Unit,
    onToggleQuality: () -> Unit = {},
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
) {
    // Wraps so the added fullscreen control still fits narrow (portrait)
    // layouts; wide layouts keep one centered row.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedButton(onClick = onTogglePlayPause) {
            Text(if (isPlaying) stringResource(Res.string.player_pause) else stringResource(Res.string.player_play))
        }
        OutlinedButton(onClick = onToggleSpeed) {
            Text(stringResource(Res.string.player_speed))
        }
        OutlinedButton(onClick = onToggleSubtitles) {
            Text(stringResource(Res.string.player_subtitles))
        }
        OutlinedButton(onClick = onToggleQuality) {
            Text(stringResource(Res.string.player_quality))
        }
        OutlinedButton(onClick = onToggleFullscreen) {
            Text(
                if (isFullscreen) {
                    stringResource(Res.string.player_exit_fullscreen)
                } else {
                    stringResource(Res.string.player_fullscreen)
                },
            )
        }
    }
}

@Composable
internal fun SpeedPicker(currentSpeed: Float, onSelect: (Float) -> Unit) {
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
internal fun QualityPicker(qualities: List<Quality>, selectedLabel: String?, onSelect: (String) -> Unit) {
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
internal fun SubtitlePicker(subtitles: List<Subtitle>, selected: Subtitle?, onSelect: (Subtitle?) -> Unit) {
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
internal fun AutoQualityNotice(onDismiss: () -> Unit) {
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
internal fun NextEpisodePrompt(countdownSeconds: Int?, onPlay: () -> Unit, onDismiss: () -> Unit) {
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
internal fun ErrorContent(
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

internal fun formatTime(seconds: Long): String {
    val clamped = maxOf(0L, seconds)
    val h = clamped / 3600
    val m = clamped % 3600 / 60
    val s = clamped % 60
    fun pad(v: Long) = if (v < 10) "0$v" else "$v"
    return if (h > 0) "$h:${pad(m)}:${pad(s)}" else "${pad(m)}:${pad(s)}"
}
