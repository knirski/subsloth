package net.subsloth.player

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.subsloth.core.domain.port.ExternalPlaybackRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level checks for the external-player intent contract: the plain
 * `ACTION_VIEW` shape plus the subtitle extras VLC (`subtitles_location`)
 * and MX Player (`subs`) document. No third-party activity is started.
 */
@RunWith(AndroidJUnit4::class)
class AndroidExternalPlayerTest {
    private val streamUrl = "https://cdn.example.com/movie.m3u8?wmsAuthSign=abc"
    private val subtitleUrl = "https://cdn.example.com/subtitles/movie_en.srt?wmsAuthSign=abc"

    @Test
    fun buildsAVideoViewIntentForTheStream() {
        val intent = buildExternalPlayerIntent(request())

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(Uri.parse(streamUrl), intent.data)
        assertEquals("video/*", intent.type)
    }

    @Test
    fun attachesTitleAndResumePosition() {
        val intent = buildExternalPlayerIntent(request(title = "Black Fire Orchid", positionSeconds = 90))

        assertEquals("Black Fire Orchid", intent.getStringExtra(Intent.EXTRA_TITLE))
        assertEquals("Black Fire Orchid", intent.getStringExtra("title"))
        assertEquals(90_000, intent.getIntExtra("position", -1))
    }

    @Test
    fun omitsTitleAndPositionWhenAbsent() {
        val intent = buildExternalPlayerIntent(request())

        assertNull(intent.getStringExtra("title"))
        assertEquals(-1, intent.getIntExtra("position", -1))
    }

    @Test
    fun attachesSubtitlesForVlcAndMxPlayer() {
        val intent = buildExternalPlayerIntent(request(subtitleUrl = subtitleUrl))

        assertEquals(subtitleUrl, intent.getStringExtra("subtitles_location"))
        assertEquals(
            Uri.parse(subtitleUrl),
            IntentCompat.getParcelableArrayExtra(intent, "subs", Uri::class.java)?.single(),
        )
        assertEquals(
            Uri.parse(subtitleUrl),
            IntentCompat.getParcelableArrayExtra(intent, "subs.enable", Uri::class.java)?.single(),
        )
        assertArrayEquals(arrayOf("movie_en.srt"), intent.getStringArrayExtra("subs.filename"))
    }

    @Test
    fun omitsSubtitleExtrasWhenNoSubtitleWasSelected() {
        val intent = buildExternalPlayerIntent(request())

        assertNull(intent.getStringExtra("subtitles_location"))
        assertNull(IntentCompat.getParcelableArrayExtra(intent, "subs", Uri::class.java))
    }

    private fun request(
        title: String? = null,
        subtitleUrl: String? = null,
        positionSeconds: Long? = null,
    ) = ExternalPlaybackRequest(
        streamUrl = streamUrl,
        subtitleUrl = subtitleUrl,
        title = title,
        positionSeconds = positionSeconds,
    )
}
