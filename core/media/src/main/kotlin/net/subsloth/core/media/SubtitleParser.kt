@file:Suppress("ReturnCount")

package net.subsloth.core.media

import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readString
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Parses SRT subtitle content into structured [SubtitleCue]s using
 * [kotlinx.io.Source] for efficient buffered reading.
 */
object SubtitleParser {

    private const val SRT_ARROW = "-->"
    private const val TIMESTAMP_PARTS = 3

    /**
     * Parses SRT content from a [Source] into a list of [SubtitleCue]s.
     *
     * The [Source] is consumed entirely. Callers are responsible for
     * closing the source after parsing.
     */
    fun parseSrt(source: Source): List<SubtitleCue> {
        val text = source.readString()
        return parseSrtString(text)
    }

    /**
     * Parses SRT content from a [ByteArray] using a [Buffer]-backed [Source].
     */
    fun parseSrt(bytes: ByteArray): List<SubtitleCue> {
        val buffer = Buffer().apply { write(bytes) }
        return parseSrt(buffer)
    }

    /**
     * Parses SRT content from a [String].
     */
    fun parseSrt(text: String): List<SubtitleCue> {
        val buffer = Buffer().apply { write(text.encodeToByteArray()) }
        return parseSrt(buffer)
    }

    internal fun parseSrtString(text: String): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = text.trim().split("\n\n", "\r\n\r\n")

        for (block in blocks) {
            val trimmed = block.trim()
            if (trimmed.isEmpty()) continue
            val cue = parseBlock(trimmed) ?: continue
            cues.add(cue)
        }

        return cues
    }

    private fun parseBlock(block: String): SubtitleCue? {
        val lines = block.lines()
        if (lines.size < 2) return null

        val index = lines[0].trim().toIntOrNull() ?: return null
        val timingLine = lines.find { it.contains(SRT_ARROW) } ?: return null
        val timing = parseTiming(timingLine) ?: return null

        val textLines = lines.dropWhile { it != timingLine }.drop(1)
            .takeWhile { it.isNotBlank() || lines.indexOf(it) < lines.lastIndex }
        val text = textLines.joinToString("\n").trim()
        if (text.isEmpty()) return null

        return SubtitleCue(index = index, start = timing.first, end = timing.second, text = text)
    }

    private fun parseTiming(line: String): Pair<kotlin.time.Duration, kotlin.time.Duration>? {
        val parts = line.split(SRT_ARROW)
        if (parts.size != 2) return null
        val start = parseTimestamp(parts[0].trim()) ?: return null
        val end = parseTimestamp(parts[1].trim()) ?: return null
        return start to end
    }

    internal fun parseTimestamp(timestamp: String): kotlin.time.Duration? {
        // Format: HH:MM:SS,mmm or HH:MM:SS.mmm
        val cleaned = timestamp.replace(',', '.')
        val timeParts = cleaned.split(':')
        if (timeParts.size != TIMESTAMP_PARTS) return null

        val hours = timeParts[0].toIntOrNull() ?: return null
        val minutes = timeParts[1].toIntOrNull() ?: return null
        val secondsPart = timeParts[2].toFloatOrNull() ?: return null
        val wholeSeconds = secondsPart.toInt()
        val millis = ((secondsPart - wholeSeconds) * 1000).toInt()

        return hours.hours + minutes.minutes + wholeSeconds.seconds + millis.milliseconds
    }
}
