package net.subsloth.core.media

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import net.subsloth.core.model.media.SubtitleFormat
import net.subsloth.core.model.playback.VideoSource

object MediaItemFactory {

    fun createMediaItem(source: VideoSource): MediaItem {
        val builder = MediaItem.Builder()
            .setMediaId(source.mediaId.toString())
            .setUri(source.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE)
                    .build(),
            )

        source.selectedQuality.info.mimeType?.let { mimeType ->
            builder.setMimeType(mimeType)
        }

        return builder.build()
    }

    /**
     * Creates a [MediaItem] for local file playback.
     *
     * [localFileUri] must be a valid content or file URI pointing to an
     * app-private downloaded file.
     */
    fun createLocalMediaItem(localFileUri: String, source: VideoSource): MediaItem {
        val uri = Uri.parse(localFileUri)
        val scheme = uri.scheme
        require(scheme == "content" || scheme == "file") {
            "Invalid local URI scheme: $scheme. Must be content:// or file://"
        }

        return MediaItem.Builder()
            .setMediaId(source.mediaId.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MOVIE)
                    .build(),
            )
            .build()
    }

    fun buildSubtitleMediaItem(source: VideoSource): List<MediaItem.SubtitleConfiguration> =
        source.availableSubtitles.mapNotNull { subtitle ->
            val uri = subtitle.url ?: return@mapNotNull null
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
                .setMimeType(subtitle.format.toMedia3MimeType())
                .setLanguage(subtitle.language.value)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

    private fun SubtitleFormat.toMedia3MimeType(): String = when (this) {
        SubtitleFormat.SRT -> MimeTypes.APPLICATION_SUBRIP
        SubtitleFormat.VTT -> MimeTypes.TEXT_VTT
        SubtitleFormat.ASS -> MimeTypes.TEXT_SSA
        SubtitleFormat.SSA -> MimeTypes.TEXT_SSA
        SubtitleFormat.UNKNOWN -> MimeTypes.APPLICATION_SUBRIP
    }
}
