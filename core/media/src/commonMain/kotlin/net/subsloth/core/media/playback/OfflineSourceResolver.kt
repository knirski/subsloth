package net.subsloth.core.media.playback

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import net.subsloth.core.model.download.OfflineAsset
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.Quality
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.playback.PlaybackMode
import net.subsloth.core.model.playback.VideoSource

/**
 * Resolves a media id to a playable offline [VideoSource] from
 * `DownloadController.listOfflineAssets()`.
 *
 * A match requires: same media id, the recorded `isPlayable` flag, and a
 * file that still verifies on disk (`OfflineAssetFiles.verifyFile`) — a
 * missing or empty file silently yields `null` (online fallback). The
 * assembled source runs in [PlaybackMode.OFFLINE], carries the asset's
 * local id and verified local subtitle files, and zero duration (the
 * player learns the real duration from the first snapshot). The `file://`
 * URIs are only read at playback time — never persisted.
 */
class OfflineSourceResolver(
    private val offlineAssets: suspend () -> Result<List<OfflineAsset>>,
    private val files: OfflineAssetFiles,
) {
    private val log = Logger.withTag("OfflineSourceResolver")

    suspend fun resolve(mediaId: Media.MediaId): VideoSource? {
        val asset = offlineAssets()
            .onFailure { log.e(it) { "listOfflineAssets failed while resolving offline playback for $mediaId" } }
            .getOrNull()
            ?.firstOrNull { it.mediaId == mediaId && it.isPlayable }
            ?.takeIf { files.verifyFile(it.videoRelativePath) }
            ?: return null
        val fileUri = files.fileUri(asset.videoRelativePath)
        val quality = Quality(
            info = asset.effectiveQuality,
            url = fileUri,
            downloadUrl = null,
        )
        val subtitles = asset.subtitles.mapNotNull { subtitle ->
            if (!files.verifyFile(subtitle.relativePath)) return@mapNotNull null
            Subtitle(
                language = subtitle.language,
                languageDisplayName = null,
                url = files.fileUri(subtitle.relativePath),
                downloadUrl = null,
                format = subtitle.format ?: SubtitleFormat.VTT,
            )
        }.toPersistentList()
        return VideoSource(
            mediaId = asset.mediaId,
            streamUrl = fileUri,
            selectedQuality = quality,
            availableQualities = persistentListOf(quality),
            availableSubtitles = subtitles,
            durationSeconds = 0L,
            playbackMode = PlaybackMode.OFFLINE,
            localId = asset.localId,
            displayName = asset.displayTitle,
        )
    }
}
