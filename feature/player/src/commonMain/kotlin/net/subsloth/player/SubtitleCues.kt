package net.subsloth.player

import net.subsloth.core.model.media.SubtitleFormat

/** A single timed subtitle cue parsed from an SRT/VTT document. */
data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

/**
 * Parses SRT and WebVTT subtitle documents into [SubtitleCue]s.
 *
 * The player renders subtitles in Compose above the control bar, so cue
 * parsing lives in the app instead of the player library (whose native
 * subtitle layer is drawn *under* our overlay and would be hidden by an
 * opaque control bar).
 */
object SubtitleTextParser {
    /**
     * Parses [text]. [format] is accepted for call-site readability; both
     * SRT and VTT share the same block structure (cue blocks separated by
     * blank lines, each containing one `-->` timing line). Blocks without
     * a valid cue header (VTT `WEBVTT` header, `NOTE`/`STYLE` blocks, SRT
     * numbering is tolerated via the timing-line scan) are skipped.
     */
    fun parse(text: String, format: SubtitleFormat): List<SubtitleCue> = parseCueBlocks(text)

    private val VTT_METADATA_PREFIXES = setOf("NOTE", "STYLE", "REGION")

    private fun parseCueBlocks(text: String): List<SubtitleCue> = text
        .split(Regex("\\n\\s*\\n"))
        .mapNotNull { block -> block.lines().parseCueBlock() }

    private fun List<String>.parseCueBlock(): SubtitleCue? {
        // VTT metadata blocks (NOTE/STYLE/REGION) may legitimately contain
        // timing-looking text; they never carry cues.
        if (first().trimStart().uppercase() in VTT_METADATA_PREFIXES) return null
        val timingIndex = indexOfFirst { it.contains("-->") }
        if (timingIndex < 0) return null
        val timing = parseTimingLine(this[timingIndex]) ?: return null
        val body = drop(timingIndex + 1)
            .dropWhile { it.isBlank() }
            .joinToString("\n")
            .trim()
        if (body.isEmpty()) return null
        return SubtitleCue(
            startMs = timing.first,
            endMs = timing.second,
            text = body,
        )
    }

    private fun parseTimingLine(line: String): Pair<Long, Long>? {
        val parts = line.split("-->")
        if (parts.size != 2) return null
        val start = parseTimestamp(parts[0]) ?: return null
        val end = parseTimestamp(parts[1]) ?: return null
        if (end <= start) return null
        return start to end
    }

    /**
     * Parses `hh:mm:ss,mmm`, `hh:mm:ss.mmm` or `mm:ss.mmm` timestamps;
     * trailing cue settings (VTT positioning after the end timestamp) and
     * any leading cue identifier are ignored.
     */
    private fun parseTimestamp(raw: String): Long? {
        val token = raw.trim().split(Regex("\\s")).firstOrNull() ?: return null
        val (clock, millis) = token.split('.', ',').let {
            if (it.size == 2) it[0] to it[1] else return null
        }
        val segments = clock.split(':').map { it.toIntOrNull() ?: return null }
        val (hours, minutes, seconds) = when (segments.size) {
            3 -> Triple(segments[0], segments[1], segments[2])
            2 -> Triple(0, segments[0], segments[1])
            else -> return null
        }
        if (minutes !in 0..59 || seconds !in 0..59) return null
        val ms = millis.take(3).padEnd(3, '0').toIntOrNull() ?: return null
        return ((hours * 60 + minutes) * 60 + seconds) * 1000L + ms
    }
}
