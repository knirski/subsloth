package net.subsloth.core.network.media.playback

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CancellationException
import net.subsloth.core.domain.policy.QualityPolicy
import net.subsloth.core.domain.port.PlaybackPort
import net.subsloth.core.model.error.MediaError
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.identifier.EpisodeId
import net.subsloth.core.model.identifier.Resolution
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.QualityDescriptor
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.playback.VideoSource
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.mapper.Mapper
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import net.subsloth.core.network.media.api.model.Episode as DtoEpisode
import net.subsloth.core.network.media.api.model.Movie as DtoMovie
import net.subsloth.core.network.media.api.model.Show as DtoShow

/**
 * Production [PlaybackPort] implementation over the Media REST API.
 *
 * Stream URLs are server-signed and expire (`wmsAuthSign`), so every
 * [prepareSource]/[refreshStreamUrl] call re-fetches media details and
 * builds a fresh [VideoSource]. The at-most-one-refresh-per-session rule
 * is enforced upstream by `StreamRefreshPolicy` in the player view model.
 *
 * Resolution rules:
 * - A media item with a non-empty `qualities` list uses those variants and
 *   `QualityPolicy.selectDefault` picks the initial one.
 * - Otherwise the item's top-level `url` (an adaptive HLS playlist) is
 *   exposed as a single quality derived from its `resolution` label.
 * - A show resolves to its first available episode, re-fetched via
 *   `Api.getEpisode` to obtain the signed stream URL.
 *
 * Renderer-level transport control (play/pause/seek) is owned by the
 * platform player bridge in `core:media` (`PlayCommand` →
 * `VideoPlayerState`), which drives the renderer directly; these port
 * methods exist for future remote-control flows and are accepted as
 * successful no-ops here.
 */
class ApiPlaybackPort(private val api: Api, private val isTvDevice: Boolean = false) : PlaybackPort {

    private val log = Logger.withTag("ApiPlaybackPort")

    override suspend fun prepareSource(mediaId: Media.MediaId): Outcome<VideoSource> = resolveSource(mediaId)

    override suspend fun refreshStreamUrl(mediaId: Media.MediaId): Outcome<VideoSource> = resolveSource(mediaId)

    override suspend fun play(source: VideoSource, positionSeconds: Long): Outcome<Unit> {
        log.d { "play(${source.mediaId}, $positionSeconds) — renderer control is owned by the player bridge" }
        return Outcome.Success(Unit)
    }

    override suspend fun pause(): Outcome<Unit> {
        log.d { "pause() — renderer control is owned by the player bridge" }
        return Outcome.Success(Unit)
    }

    override suspend fun seek(positionSeconds: Long): Outcome<Unit> {
        log.d { "seek($positionSeconds) — renderer control is owned by the player bridge" }
        return Outcome.Success(Unit)
    }

    private suspend fun resolveSource(mediaId: Media.MediaId): Outcome<VideoSource> = try {
        when (mediaId) {
            is Media.MediaId.Movie -> movieSource(api.getMovie(mediaId.value.value), mediaId)
            is Media.MediaId.Episode -> episodeSource(api.getEpisode(mediaId.value.value))
            is Media.MediaId.Show -> showSource(api.getShow(mediaId.value.value))
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log.e(e) { "Stream source resolution failed for $mediaId" }
        Outcome.Failure(NetworkErrorClassifier.classifyToNetwork(e))
    }

    private fun movieSource(dto: DtoMovie, mediaId: Media.MediaId.Movie): Outcome<VideoSource> = sourceFrom(
        mediaId = mediaId,
        payload =
        StreamPayload(
            streamUrl = dto.url,
            downloadUrl = dto.downloadUrl,
            resolutionLabel = dto.resolution,
            qualities = Mapper.mapQualities(dto.qualities),
            subtitles = Mapper.mapSubtitleTracks(dto.subtitles),
            durationSeconds = dto.duration.durationMinutesToSeconds(),
            displayName = dto.title ?: dto.name,
        ),
    )

    private fun episodeSource(dto: DtoEpisode): Outcome<VideoSource> = sourceFrom(
        mediaId = Media.MediaId.Episode(EpisodeId(dto.id)),
        payload =
        StreamPayload(
            streamUrl = dto.url,
            downloadUrl = dto.downloadUrl,
            resolutionLabel = dto.resolution,
            qualities = Mapper.mapQualities(dto.qualities),
            subtitles = Mapper.mapSubtitleTracks(dto.subtitles),
            durationSeconds = dto.duration.durationMinutesToSeconds(),
            displayName = dto.name ?: dto.title ?: dto.showName,
        ),
    )

    /**
     * Resolves a show to its first available episode. Embedded episode
     * entries carry no stream URL, so the chosen episode is re-fetched via
     * [Api.getEpisode] to obtain the signed stream URL.
     */
    private suspend fun showSource(dto: DtoShow): Outcome<VideoSource> {
        val firstEpisode = dto.episodes
            .orEmpty()
            .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: it.number ?: 0 }))
            .firstOrNull { it.available != false && it.id > 0 }
            ?: return Outcome.Failure(MediaError.NotFound)
        return episodeSource(api.getEpisode(firstEpisode.id))
    }

    private fun sourceFrom(mediaId: Media.MediaId, payload: StreamPayload): Outcome<VideoSource> {
        val effectiveQualities = if (payload.qualities.isEmpty()) {
            payload.streamUrl
                ?.let { persistentListOf(fallbackQuality(it, payload.downloadUrl, payload.resolutionLabel)) }
                ?: persistentListOf()
        } else {
            payload.qualities
        }
        val selected = QualityPolicy.selectDefault(effectiveQualities, isTvDevice)
            ?: return Outcome.Failure(MediaError.Unavailable)
        val stream = selected.url ?: payload.streamUrl ?: return Outcome.Failure(MediaError.Unavailable)
        return Outcome.Success(
            VideoSource(
                mediaId = mediaId,
                streamUrl = stream,
                selectedQuality = selected,
                availableQualities = effectiveQualities,
                availableSubtitles = payload.subtitles,
                durationSeconds = payload.durationSeconds,
                displayName = payload.displayName,
            ),
        )
    }

    /** Builds the single adaptive-stream quality for items without a `qualities` list. */
    private fun fallbackQuality(streamUrl: String, downloadUrl: String?, resolutionLabel: String?): Quality = Quality(
        info =
        QualityDescriptor(
            resolution = parseLabelResolution(resolutionLabel),
            label = resolutionLabel,
            bitrate = null,
            mimeType = null,
        ),
        url = streamUrl,
        downloadUrl = downloadUrl,
    )

    private fun parseLabelResolution(label: String?): Resolution {
        val normalized = label?.trim()?.uppercase() ?: return Resolution.FULL_HD
        return when (normalized) {
            "SD" -> Resolution.SD

            "HD" -> Resolution.HD_720

            "FHD", "FULL HD", "1080P" -> Resolution.FULL_HD

            "UHD", "4K", "2160P" -> Resolution.UHD_4K

            else ->
                normalized
                    .split("X", "×")
                    .mapNotNull(String::toIntOrNull)
                    .takeIf { it.size == 2 && it.all { dimension -> dimension > 0 } }
                    ?.let { (width, height) -> Resolution(width, height) }
                    ?: Resolution.FULL_HD
        }
    }

    /** The DTO carries durations in minutes; the player works in seconds. */
    private fun Int?.durationMinutesToSeconds(): Long = this?.toDuration(DurationUnit.MINUTES)?.inWholeSeconds ?: 0L
}

/**
 * Playback-relevant fields extracted from a movie or episode detail DTO.
 * A pure value object so the [VideoSource] assembly in [ApiPlaybackPort]
 * stays a two-parameter function.
 */
private data class StreamPayload(
    val streamUrl: String?,
    val downloadUrl: String?,
    val resolutionLabel: String?,
    val qualities: ImmutableList<Quality>,
    val subtitles: ImmutableList<Subtitle>,
    val durationSeconds: Long,
    val displayName: String?,
)
