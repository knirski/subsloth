package net.subsloth.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import co.touchlab.kermit.Logger
import net.subsloth.core.domain.port.ExternalPlaybackPort
import net.subsloth.core.domain.port.ExternalPlaybackRequest
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome

/** MIME type every external player accepts for network video streams. */
private const val VIDEO_MIME_TYPE = "video/*"

/** VLC's documented title extra. */
private const val EXTRA_VLC_TITLE = "title"

/** Resume position in milliseconds, understood by VLC and MX Player. */
private const val EXTRA_POSITION = "position"

/** VLC's documented subtitle URI extra. */
private const val EXTRA_VLC_SUBTITLES_LOCATION = "subtitles_location"

/** MX Player's documented subtitle URI array extra. */
private const val EXTRA_MX_SUBS = "subs"

/** MX Player's documented "initially visible subtitles" URI array extra. */
private const val EXTRA_MX_SUBS_ENABLE = "subs.enable"

/** MX Player's documented per-subtitle filename array extra. */
private const val EXTRA_MX_SUBS_FILENAME = "subs.filename"

/** Milliseconds per second, for the position extras. */
private const val MILLIS_PER_SECOND = 1_000L

/** How much of a stream URL a log line keeps. */
private const val LOG_URL_PREFIX_LENGTH = 80

/**
 * Routes an online stream to an installed external video player.
 *
 * The intent is the plain `ACTION_VIEW` contract with a generic video MIME
 * type that every player accepts, so the system resolves the user's default
 * player or shows the disambiguation dialog. Subtitles are attached through
 * the extras the major Android players document — VLC reads `subtitles_location`, MX
 * Player reads the `subs` URI array (plus `subs.enable` so the track starts
 * visible). Players that do not understand an extra ignore it, and there is
 * no standard Android subtitle handoff to rely on instead.
 *
 * Playback is fire-and-forget: no position or completion result is
 * reported back, so external playback never updates in-app progress.
 */
class AndroidExternalPlayer(private val context: Context) : ExternalPlaybackPort {
    private val log = Logger.withTag("AndroidExternalPlayer")

    @Suppress("TooGenericExceptionCaught") // Platform boundary: any startActivity failure falls back to in-app playback.
    override suspend fun open(request: ExternalPlaybackRequest): Outcome<Unit> =
        try {
            // The context is the application context, so the intent must
            // start its own task.
            val intent = buildExternalPlayerIntent(request)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Outcome.Success(Unit)
        } catch (e: Exception) {
            log.w(e) { "No external player accepted ${request.streamUrl.take(LOG_URL_PREFIX_LENGTH)}" }
            Outcome.Failure(MediaError.Unavailable)
        }
}

/**
 * Builds the `ACTION_VIEW` intent handed to the external player. Pure
 * intent construction, separated so device tests can assert the extras
 * without starting an activity.
 */
internal fun buildExternalPlayerIntent(request: ExternalPlaybackRequest): Intent {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndTypeAndNormalize(request.streamUrl.toUri(), VIDEO_MIME_TYPE)
    }
    request.title?.takeIf { it.isNotBlank() }?.let { title ->
        intent.putExtra(Intent.EXTRA_TITLE, title)
        intent.putExtra(EXTRA_VLC_TITLE, title)
    }
    resumePositionMillis(request.positionSeconds)?.let { millis ->
        intent.putExtra(EXTRA_POSITION, millis)
    }
    request.subtitleUrl?.takeIf { it.isNotBlank() }?.let { subtitleUrl ->
        val subtitleUri = subtitleUrl.toUri()
        intent.putExtra(EXTRA_VLC_SUBTITLES_LOCATION, subtitleUrl)
        intent.putExtra(EXTRA_MX_SUBS, arrayOf(subtitleUri))
        intent.putExtra(EXTRA_MX_SUBS_ENABLE, arrayOf(subtitleUri))
        subtitleFileName(subtitleUri)?.let { fileName ->
            intent.putExtra(EXTRA_MX_SUBS_FILENAME, arrayOf(fileName))
        }
    }
    return intent
}

/**
 * Resume position in milliseconds, or null when playback should start from
 * the beginning. Clamped to the `Int` range because MX Player reads the
 * extra as an `int`.
 */
private fun resumePositionMillis(positionSeconds: Long?): Int? =
    positionSeconds
        ?.takeIf { it > 0L }
        ?.let { seconds -> (seconds * MILLIS_PER_SECOND).coerceAtMost(Int.MAX_VALUE.toLong()).toInt() }

/** File name MX Player uses to match a remote subtitle to the stream. */
private fun subtitleFileName(uri: Uri): String? =
    uri.lastPathSegment?.takeIf { it.isNotBlank() }
