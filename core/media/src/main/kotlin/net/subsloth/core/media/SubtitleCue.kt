package net.subsloth.core.media

import kotlin.time.Duration

/**
 * A single subtitle cue with parsed timing and text, e.g. from an SRT or VTT file.
 */
data class SubtitleCue(val index: Int, val start: Duration, val end: Duration, val text: String)
