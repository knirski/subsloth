package net.subsloth.core.media

import io.github.kdroidfilter.composemediaplayer.SubtitleTrack
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PlayCommandPlayerEventTest {
    @Test
    fun `PlayCommand defaults position to zero`() {
        val cmd = PlayCommand(url = "https://example.com/stream")
        assertThat(cmd.url).isEqualTo("https://example.com/stream")
        assertThat(cmd.positionSeconds).isEqualTo(0L)
        assertThat(cmd.subtitleTrack).isNull()
    }

    @Test
    fun `PlayCommand carries custom position and subtitle`() {
        val track = SubtitleTrack(label = "English", language = "en", src = "https://example.com/sub.vtt")
        val cmd = PlayCommand(url = "https://example.com/stream", positionSeconds = 120L, subtitleTrack = track)
        assertThat(cmd.positionSeconds).isEqualTo(120L)
        assertThat(cmd.subtitleTrack?.language).isEqualTo("en")
    }

    @Test
    fun `PlayerEvent Snapshot carries player snapshot`() {
        val snapshot =
            PlayerSnapshot(positionSeconds = 60L, durationSeconds = 3600L, isPlaying = true, isLoading = false)
        val event = PlayerEvent.Snapshot(snapshot)
        assertThat(event.value.positionSeconds).isEqualTo(60L)
        assertThat(event.value.isPlaying).isTrue()
    }

    @Test
    fun `PlayerEvent Error carries message`() {
        val event = PlayerEvent.Error("Playback failed")
        assertThat(event.message).isEqualTo("Playback failed")
    }

    @Test
    fun `PlayerEvent PlaybackEnded is the same reference`() {
        assertThat(PlayerEvent.PlaybackEnded === PlayerEvent.PlaybackEnded).isTrue()
    }

    @Test
    fun `PlayerSnapshot defaults are false`() {
        val snap = PlayerSnapshot(positionSeconds = 0L, durationSeconds = 0L, isPlaying = false, isLoading = false)
        assertThat(snap.isPlaying).isFalse()
        assertThat(snap.isLoading).isFalse()
    }
}
