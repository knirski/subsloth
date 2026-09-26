package net.subsloth.core.ui

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether the current platform can hand playback to an installed external
 * video player.
 *
 * Only Android can route a stream to a third-party app through an `Intent`,
 * so the Android shell provides `true` and desktop/web keep the default
 * `false`, which hides the settings option entirely.
 */
val LocalExternalPlayerSupported = staticCompositionLocalOf { false }
