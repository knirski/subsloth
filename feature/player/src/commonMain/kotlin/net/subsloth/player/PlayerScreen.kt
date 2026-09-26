@file:Suppress("TooManyFunctions", "ktlint:standard:no-wildcard-imports")

package net.subsloth.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.kdroidfilter.composemediaplayer.VideoPlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** Platform-owned bottom inset for the controls (Android navigation bar). */
    controlsBottomPadding: Dp = 0.dp,
    /** Desktop/web hosts enable keyboard shortcuts; touch-first hosts do not. */
    enableKeyboardShortcuts: Boolean = false,
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
                        controlsBottomPadding = controlsBottomPadding,
                        enableKeyboardShortcuts = enableKeyboardShortcuts,
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
    /** Platform-owned bottom inset for the controls (Android navigation bar). */
    controlsBottomPadding: Dp = 0.dp,
    /** Desktop/web hosts enable keyboard shortcuts; touch-first hosts do not. */
    enableKeyboardShortcuts: Boolean = false,
) {
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showQualityPicker by remember { mutableStateOf(false) }

    val controllerState = rememberPlayerControllerState()
    val density = LocalDensity.current
    val seekState = remember(density) {
        PlayerSeekState(cancelDistancePx = with(density) { SEEK_CANCEL_DISTANCE.toPx() })
    }
    val togglePlayPause = {
        if (playerState.isPlaying) playerState.pause() else playerState.play()
    }

    // Pickers and an in-flight slider drag keep the chrome on screen while
    // playback continues; the auto-hide timer only flips isFullVisible.
    val speedPickerRequester = remember { Any() }
    val subtitlePickerRequester = remember { Any() }
    val qualityPickerRequester = remember { Any() }
    val seekRequester = remember { Any() }
    controllerState.KeepVisibleWhile(speedPickerRequester, showSpeedPicker)
    controllerState.KeepVisibleWhile(subtitlePickerRequester, showSubtitlePicker)
    controllerState.KeepVisibleWhile(qualityPickerRequester, showQualityPicker)
    controllerState.KeepVisibleWhile(seekRequester, seekState.preview != null)

    // Last pointer family seen over the video: a finger tap toggles the
    // chrome, a mouse click toggles playback (desktop/web convention).
    var lastPointerType by remember { mutableStateOf(PointerType.Touch) }

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

    // Keyboard shortcuts (desktop/web): space toggles playback, arrows seek
    // and accelerate when held, F toggles fullscreen.
    val keyboardController = rememberPlayerKeyboardController(
        playerState = playerState,
        controllerState = controllerState,
        onToggleFullscreen = onToggleFullscreen,
    )
    val keyboardRequester = remember { Any() }
    controllerState.KeepVisibleWhile(keyboardRequester, keyboardController.isFastForwarding)
    val anyPickerOpen = showSpeedPicker || showSubtitlePicker || showQualityPicker
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(enableKeyboardShortcuts, anyPickerOpen) {
        // Picker buttons take focus while open; when the last picker closes
        // the focused button may be gone, so hand focus back to the overlay.
        if (enableKeyboardShortcuts && !anyPickerOpen) focusRequester.requestFocus()
    }

    // Auto-hide the chrome a few seconds into uninterrupted playback. Any
    // interaction either shows the chrome or bumps lastInteraction, which
    // restarts this timer; requesters suspend hiding entirely, and paused
    // playback always keeps the chrome on screen.
    LaunchedEffect(
        controllerState.isVisible,
        controllerState.alwaysOn,
        controllerState.lastInteraction,
        state.isPlaying,
    ) {
        if (!state.isPlaying) {
            // Paused playback always keeps the chrome on screen.
            if (!controllerState.isVisible) controllerState.show()
            return@LaunchedEffect
        }
        if (controllerState.isVisible && !controllerState.alwaysOn) {
            delay(CONTROLS_AUTO_HIDE_MS)
            controllerState.hide()
        }
    }

    val keyboardModifier = if (enableKeyboardShortcuts) {
        Modifier
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // While a picker is open, its buttons own the keyboard.
                if (anyPickerOpen) false else keyboardController.onKeyEvent(event)
            }
    } else {
        Modifier
    }

    // No background here: the video surface renders behind the Compose
    // canvas (e.g. zIndex -1 on web), so the overlay must stay transparent
    // for frames to show through. Error and prompt screens draw their own
    // opaque backgrounds.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(PLAYER_OVERLAY_TAG)
            .then(keyboardModifier)
            // Observe pointer events without consuming them: the family of the
            // last pointer decides tap semantics, and mouse movement shows the
            // chrome (desktop/web convention).
            .pointerInput(controllerState) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull() ?: continue
                        lastPointerType = change.type
                        if (change.type == PointerType.Mouse && event.type == PointerEventType.Move) {
                            controllerState.show()
                        }
                    }
                }
            }
            .pointerInput(controllerState) {
                // Taps on the video toggle chrome or playback; taps on controls
                // are consumed by their own handlers and never reach here.
                detectTapGestures(
                    onTap = {
                        when (lastPointerType) {
                            PointerType.Touch -> controllerState.toggle()
                            else -> togglePlayPause()
                        }
                    },
                    onDoubleTap = {
                        when (lastPointerType) {
                            PointerType.Touch -> togglePlayPause()
                            else -> onToggleFullscreen()
                        }
                    },
                )
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
                modifier = Modifier.padding(bottom = if (controllerState.isVisible) 0.dp else 32.dp),
            )

            if (controllerState.isVisible) {
                val barDisplaySeconds = seekState.preview?.let {
                    (it / 1000f * state.durationSeconds).toLong()
                } ?: state.positionSeconds
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(vertical = 8.dp)
                        // Platform-owned bottom inset (Android navigation bar /
                        // gesture area); zero on desktop, web, and previews.
                        .padding(bottom = controlsBottomPadding),
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
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                // Observe the drag without consuming it, so a
                                // pointer moving far above the slider cancels
                                // the seek on release.
                                .pointerInput(seekState) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull() ?: continue
                                            if (event.type == PointerEventType.Press) {
                                                // The pointer-down position is the
                                                // cancellation origin, recorded before
                                                // the slider reports any preview.
                                                seekState.onDragStart(change.position.y)
                                            } else if (change.pressed) {
                                                seekState.onDragPosition(change.position.y)
                                            }
                                        }
                                    }
                                },
                        ) {
                            Slider(
                                // The preview is UI state only; the player is
                                // seeked once, on release.
                                value = seekState.preview ?: playerState.sliderPos,
                                onValueChange = seekState::onPreview,
                                onValueChangeFinished = {
                                    seekState.commit()?.let(playerState::seekTo)
                                },
                                valueRange = 0f..1000f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )

                            if (seekState.isCancelling) {
                                Text(
                                    text = stringResource(Res.string.player_seek_cancel_hint),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .offset(y = (-22).dp)
                                        .background(
                                            color = Color.Black.copy(alpha = 0.6f),
                                            shape = RoundedCornerShape(4.dp),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }

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
                            togglePlayPause()
                            controllerState.show()
                        },
                        onToggleSpeed = { showSpeedPicker = !showSpeedPicker },
                        onToggleSubtitles = { showSubtitlePicker = !showSubtitlePicker },
                        onToggleQuality = { showQualityPicker = !showQualityPicker },
                        isFullscreen = isFullscreen,
                        onToggleFullscreen = onToggleFullscreen,
                        playbackSpeed = state.playbackSpeed,
                        qualityLabel = state.selectedQualityLabel,
                        canSkip = state.durationSeconds > 0,
                        onSkipBackward = {
                            playerState.seekBy(-SKIP_SECONDS)
                            controllerState.show()
                        },
                        onSkipForward = {
                            playerState.seekBy(SKIP_SECONDS)
                            controllerState.show()
                        },
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

/** How far a pointer must move above the slider to cancel the seek on release. */
private val SEEK_CANCEL_DISTANCE = 144.dp

/** Test tag for the player overlay root; UI tests drive taps and keys through it. */
const val PLAYER_OVERLAY_TAG = "player-overlay"

/** Seconds moved by one tap on a rewind or fast-forward control. */
internal const val SKIP_SECONDS = 10L

/** Slider scale expected by [VideoPlayerState.seekTo]. */
private const val SLIDER_RANGE = 1000f

/**
 * Target of a skip from [positionSeconds], clamped to `0..durationSeconds`.
 * Returns null when the duration or position is unknown (live stream, source
 * still opening, or the platform reporting NaN/Infinity — browsers expose
 * `NaN` as `HTMLVideoElement.duration` until metadata arrives), so callers
 * leave the player untouched instead of seeking to zero or to `NaN`.
 */
internal fun skipTargetSeconds(positionSeconds: Double, durationSeconds: Double, deltaSeconds: Long): Double? =
    if (durationSeconds <= 0.0 || !durationSeconds.isFinite() || !positionSeconds.isFinite()) {
        null
    } else {
        (positionSeconds + deltaSeconds).coerceIn(0.0, durationSeconds)
    }

/**
 * Seeks the player by [deltaSeconds] (negative rewinds).
 *
 * The base position comes from [VideoPlayerState.sliderPos] rather than
 * [VideoPlayerState.currentTime]: native backends refresh `currentTime` only on
 * the next position poll, so two quick skips would read the same position and
 * one would be lost. `sliderPos` is advanced to the target before the seek so
 * consecutive skips accumulate immediately.
 */
internal fun VideoPlayerState.seekBy(deltaSeconds: Long) {
    val positionSeconds = sliderPos.toDouble() / SLIDER_RANGE.toDouble() * duration
    val target = skipTargetSeconds(positionSeconds, duration, deltaSeconds) ?: return
    val targetSlider = (target / duration * SLIDER_RANGE).toFloat().coerceIn(0f, SLIDER_RANGE)
    sliderPos = targetSlider
    seekTo(targetSlider)
}
