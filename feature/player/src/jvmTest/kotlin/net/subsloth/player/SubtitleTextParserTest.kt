package net.subsloth.player

import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.testing.assertions.assertThat
import kotlin.test.Test

class SubtitleTextParserTest {
    @Test
    fun `parses SRT with index lines and comma milliseconds`() {
        val cues = SubtitleTextParser.parse(
            text = """
            1
            00:00:01,000 --> 00:00:03,500
            Hello world.

            2
            00:00:04,000 --> 00:01:06,250
            Second cue.
            multiple lines
            """.trimIndent(),
            format = SubtitleFormat.SRT,
        )

        assertThat(cues).hasSize(2)
        assertThat(cues[0].startMs).isEqualTo(1_000L)
        assertThat(cues[0].endMs).isEqualTo(3_500L)
        assertThat(cues[0].text).isEqualTo("Hello world.")
        assertThat(cues[1].startMs).isEqualTo(4_000L)
        assertThat(cues[1].endMs).isEqualTo(66_250L)
        assertThat(cues[1].text).isEqualTo("Second cue.\nmultiple lines")
    }

    @Test
    fun `parses VTT with header and cue settings`() {
        val cues = SubtitleTextParser.parse(
            text = """
            WEBVTT

            NOTE this is a comment

            intro
            00:00.500 --> 00:02.000 position:50% align:center
            Hi there.
            """.trimIndent(),
            format = SubtitleFormat.VTT,
        )

        assertThat(cues).hasSize(1)
        assertThat(cues[0].startMs).isEqualTo(500L)
        assertThat(cues[0].endMs).isEqualTo(2_000L)
        assertThat(cues[0].text).isEqualTo("Hi there.")
    }

    @Test
    fun `sniffs format for UNKNOWN documents`() {
        val srt = "1\n00:00:01,000 --> 00:00:02,000\nA."
        val vtt = "WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nB."

        assertThat(SubtitleTextParser.parse(srt, SubtitleFormat.UNKNOWN)).hasSize(1)
        assertThat(SubtitleTextParser.parse(vtt, SubtitleFormat.UNKNOWN)).hasSize(1)
    }

    @Test
    fun `drops malformed blocks but keeps valid ones`() {
        val cues = SubtitleTextParser.parse(
            text = """
            no timing here

            00:00:01,000 --> 00:00:00,000
            inverted timing

            00:00:01,000 --> 00:00:02,000
            valid
            """.trimIndent(),
            format = SubtitleFormat.SRT,
        )

        assertThat(cues).hasSize(1)
        assertThat(cues[0].text).isEqualTo("valid")
    }

    @Test
    fun `empty or header-only documents produce no cues`() {
        assertThat(SubtitleTextParser.parse("", SubtitleFormat.SRT)).isEmpty()
        assertThat(SubtitleTextParser.parse("WEBVTT\n", SubtitleFormat.VTT)).isEmpty()
    }
}
