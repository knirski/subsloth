package net.subsloth.core.media

import kotlinx.io.Buffer
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class SubtitleParserTest {

    // ── Source-based parsing ───────────────────────────────────────────────

    @Test
    fun `parseSrt from Source parses single cue`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,500
            Hello world
        """.trimIndent()

        val buffer = Buffer().apply { write(srt.encodeToByteArray()) }
        val cues = SubtitleParser.parseSrt(buffer)

        assertThat(cues).hasSize(1)
        assertThat(cues[0].index).isEqualTo(1)
        assertThat(cues[0].start).isEqualTo(1.seconds)
        assertThat(cues[0].end).isEqualTo(4.5.seconds)
        assertThat(cues[0].text).isEqualTo("Hello world")
    }

    @Test
    fun `parseSrt from ByteArray uses buffer-backed source`() {
        val srt = """
            1
            00:00:00,500 --> 00:00:03,000
            Test
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srt.encodeToByteArray())

        assertThat(cues).hasSize(1)
        assertThat(cues[0].start).isEqualTo(500.milliseconds)
    }

    // ── String-based parsing ───────────────────────────────────────────────

    @Test
    fun `parseSrt from String parses multiple cues`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            First line

            2
            00:00:05,000 --> 00:00:08,500
            Second line
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srt)

        assertThat(cues).hasSize(2)
        assertThat(cues[0].index).isEqualTo(1)
        assertThat(cues[0].text).isEqualTo("First line")
        assertThat(cues[1].index).isEqualTo(2)
        assertThat(cues[1].text).isEqualTo("Second line")
    }

    @Test
    fun `parseSrt handles multiline text`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:04,000
            Line one
            Line two
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srt)

        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Line one\nLine two")
    }

    @Test
    fun `parseSrt handles hours in timestamp`() {
        val srt = """
            1
            01:30:00,000 --> 02:00:00,000
            Long content
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srt)

        assertThat(cues).hasSize(1)
        assertThat(cues[0].start).isEqualTo(1.hours + 30.minutes)
        assertThat(cues[0].end).isEqualTo(2.hours)
    }

    @Test
    fun `parseSrt returns empty for blank input`() {
        val cues = SubtitleParser.parseSrt("")
        assertThat(cues).isEmpty()
    }

    @Test
    fun `parseSrt skips invalid blocks`() {
        val srt = """
            invalid block

            1
            00:00:01,000 --> 00:00:04,000
            Valid cue
        """.trimIndent()

        val cues = SubtitleParser.parseSrt(srt)
        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("Valid cue")
    }

    // ── Timestamp parsing ──────────────────────────────────────────────────

    @Test
    fun `parseTimestamp handles comma and dot separators`() {
        val withComma = SubtitleParser.parseTimestamp("00:00:01,500")
        val withDot = SubtitleParser.parseTimestamp("00:00:01.500")

        assertThat(withComma).isEqualTo(1.5.seconds)
        assertThat(withDot).isEqualTo(1.5.seconds)
    }

    @Test
    fun `parseTimestamp returns null for invalid format`() {
        assertThat(SubtitleParser.parseTimestamp("invalid")).isNull()
        assertThat(SubtitleParser.parseTimestamp("00:00")).isNull()
        assertThat(SubtitleParser.parseTimestamp("")).isNull()
    }

    @Test
    fun `parseTimestamp handles milliseconds`() {
        val result = SubtitleParser.parseTimestamp("00:00:00,123")
        assertThat(result).isEqualTo(123.milliseconds)
    }
}
