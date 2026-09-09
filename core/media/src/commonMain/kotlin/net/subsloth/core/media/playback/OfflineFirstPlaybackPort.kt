package net.subsloth.core.media.playback

import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.playback.VideoSource

/**
 * [PlaybackPort] decorator that prefers stored offline assets: when the
 * requested media has a verified local download, [prepareSource] returns
 * its [net.subsloth.core.model.playback.PlaybackMode.OFFLINE] source
 * without any network access; otherwise resolution (and every other
 * operation) delegates to the online port.
 *
 * `refreshStreamUrl` is part of the online signed-URL lifecycle — per
 * `StreamRefreshPolicy`, offline playback must never refresh. As a
 * defensive guard, a refresh over offline-resolved media returns the
 * unchanged offline source instead of delegating to the network.
 */
class OfflineFirstPlaybackPort(
    private val offlineSourceResolver: OfflineSourceResolver,
    private val online: PlaybackPort,
) : PlaybackPort {

    override suspend fun prepareSource(mediaId: Media.MediaId): Outcome<VideoSource> =
        resolveOfflineOr(mediaId) { online.prepareSource(mediaId) }

    override suspend fun refreshStreamUrl(mediaId: Media.MediaId): Outcome<VideoSource> =
        resolveOfflineOr(mediaId) { online.refreshStreamUrl(mediaId) }

    override suspend fun play(source: VideoSource, positionSeconds: Long): Outcome<Unit> =
        online.play(source, positionSeconds)

    override suspend fun pause(): Outcome<Unit> = online.pause()

    override suspend fun seek(positionSeconds: Long): Outcome<Unit> = online.seek(positionSeconds)

    private suspend fun resolveOfflineOr(
        mediaId: Media.MediaId,
        fallback: suspend () -> Outcome<VideoSource>,
    ): Outcome<VideoSource> = offlineSourceResolver.resolve(mediaId)
        ?.let { Outcome.Success(it) }
        ?: fallback()
}
