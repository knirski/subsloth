package net.subsloth.player

import net.subsloth.core.model.playback.PlaybackError
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.Test

class PlaybackErrorMappingTest {

    @Test
    fun `a 401 in the message classifies as auth failure`() {
        assertThat(classifyPlaybackErrorMessage("HTTP 401 Unauthorized"))
            .isInstanceOf(PlaybackError.AuthFailure::class.java)
    }

    @Test
    fun `a 403 in the message classifies as expired stream url`() {
        assertThat(classifyPlaybackErrorMessage("Stream URL expired (403)"))
            .isInstanceOf(PlaybackError.StreamUrlExpired::class.java)
    }

    @Test
    fun `a message without a status code classifies as recoverable`() {
        assertThat(classifyPlaybackErrorMessage("Codec error: malformed input"))
            .isInstanceOf(PlaybackError.Recoverable::class.java)
    }

    @Test
    fun `a server error status classifies as recoverable`() {
        assertThat(classifyPlaybackErrorMessage("Server returned HTTP 500"))
            .isInstanceOf(PlaybackError.Recoverable::class.java)
    }
}
