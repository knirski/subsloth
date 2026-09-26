package net.subsloth.core.domain.port

import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome

/**
 * A request to hand playback of an online stream to an installed
 * third-party video player (for example VLC or MX Player).
 *
 * Only ephemeral values are carried: [streamUrl] and [subtitleUrl] are
 * server-signed URLs resolved for this playback session and must never be
 * persisted.
 *
 * [subtitleUrl] is best-effort. Android has no standard subtitle extra, so
 * the adapter attaches the track through the extras the major external
 * players document (VLC's `subtitles_location`, MX Player's `subs`); a
 * player that does not understand them simply opens the stream without
 * subtitles.
 */
data class ExternalPlaybackRequest(
    val streamUrl: String,
    val subtitleUrl: String? = null,
    /** Human-readable title shown by the external player. */
    val title: String? = null,
    /**
     * Resume position in whole seconds, or null/0 to start from the
     * beginning. Engines that understand the position extra open the
     * stream at this point instead of replaying from zero.
     */
    val positionSeconds: Long? = null,
)

/**
 * Port for handing an online stream to an external video player.
 *
 * Implementations exist only where the platform can route a stream to a
 * third-party app (currently Android). All other platforms never enable
 * the handoff preference, so they never need an implementation.
 */
interface ExternalPlaybackPort {
    /**
     * Opens [request] in an external player.
     *
     * Returns [Outcome.Failure] with [MediaError.Unavailable] when no
     * installed app accepted the stream; callers then fall back to
     * in-app playback. The command itself is fire-and-forget: no playback
     * result (position, completion) is reported back.
     */
    suspend fun open(request: ExternalPlaybackRequest): Outcome<Unit>
}
