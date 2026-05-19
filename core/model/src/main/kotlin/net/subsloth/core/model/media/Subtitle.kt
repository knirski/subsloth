package net.subsloth.core.model.media

import net.subsloth.core.model.identifier.LanguageCode

/**
 * A subtitle track associated with a media item.
 *
 * Only the language metadata is retained in domain state. The subtitle
 * [url] and [downloadUrl] are ephemeral values used during active playback
 * or download and must not be persisted in storage records.
 */
data class Subtitle(
    val language: LanguageCode,
    val languageDisplayName: String?,
    /** Ephemeral subtitle stream URL — must not be persisted. */
    val url: String?,
    /** Ephemeral subtitle download URL — must not be persisted. */
    val downloadUrl: String?,
    val format: SubtitleFormat,
)

/** Known subtitle file formats. */
enum class SubtitleFormat {
    SRT,
    VTT,
    ASS,
    SSA,
    UNKNOWN,
}
