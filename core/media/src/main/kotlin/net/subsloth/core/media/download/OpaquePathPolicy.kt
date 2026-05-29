package net.subsloth.core.media.download

import net.subsloth.core.model.download.OfflineRelativePath
import net.subsloth.core.model.identifier.LanguageCode
import java.util.UUID

/**
 * Pure policies for generating opaque download paths within app-private storage.
 *
 * All paths are relative and use only the [OfflineRelativePath] type to enforce
 * the constraint at the type level. Components are content-ID-based to allow
 * grouping related assets, with a random UUID to prevent enumeration.
 */
object OpaquePathPolicy {
    /**
     * Generates an opaque relative path for a video download.
     *
     * @param contentId The content (movie or show) identifier for grouping.
     * @param extension The file extension (e.g. "mp4", "mkv").
     * @param randomId A random UUID to prevent path enumeration.
     */
    fun videoPath(contentId: String, extension: String, randomId: UUID): OfflineRelativePath =
        OfflineRelativePath("downloads/video/$contentId/$randomId.$extension")

    /**
     * Generates an opaque relative path for a subtitle download.
     *
     * @param contentId The content (movie or show) identifier for grouping.
     * @param language The language code for the subtitle track.
     * @param extension The file extension (e.g. "srt", "vtt").
     * @param randomId A random UUID to prevent path enumeration.
     */
    fun subtitlePath(
        contentId: String,
        language: LanguageCode,
        extension: String,
        randomId: UUID,
    ): OfflineRelativePath =
        OfflineRelativePath("downloads/subtitles/$contentId/${language.value}/$randomId.$extension")
}
