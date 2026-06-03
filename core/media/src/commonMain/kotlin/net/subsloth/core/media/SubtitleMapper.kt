package net.subsloth.core.media

import io.github.kdroidfilter.composemediaplayer.SubtitleTrack
import net.subsloth.core.model.media.Subtitle
import net.subsloth.core.model.media.SubtitleFormat

object SubtitleMapper {
    private val UNSUPPORTED = setOf(SubtitleFormat.ASS, SubtitleFormat.SSA)

    fun toSubtitleTrack(subtitle: Subtitle): SubtitleTrack? {
        if (subtitle.format in UNSUPPORTED) return null
        val url = subtitle.url ?: return null
        return SubtitleTrack(
            label = subtitle.languageDisplayName ?: subtitle.language.value,
            language = subtitle.language.value,
            src = url,
        )
    }

    fun isFormatSupported(format: SubtitleFormat): Boolean = format !in UNSUPPORTED
}
