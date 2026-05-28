package net.subsloth.core.domain.policy

import net.subsloth.core.model.download.SubtitleSelection
import net.subsloth.core.model.download.TransferPreference
import net.subsloth.core.model.identifier.LanguageCode
import net.subsloth.core.model.media.Subtitle

object SeasonQueuePolicy {
    fun selectInitialSubtitle(
        available: List<Subtitle>,
        preferred: LanguageCode,
    ): SubtitleSelection {
        val english = LanguageCode("en")
        val preferredTrack = available.firstOrNull { it.language == preferred }
        val englishTrack = available.firstOrNull { it.language == english }
        return when {
            preferred != english && preferredTrack != null -> SubtitleSelection.Preferred(preferredTrack)
            englishTrack != null -> SubtitleSelection.EnglishFallback(englishTrack)
            else -> SubtitleSelection.None
        }
    }

    fun canResumeQueue(
        isOnline: Boolean,
        hasStorage: Boolean,
        transferPreference: TransferPreference,
        isMetered: Boolean,
        authValid: Boolean,
    ): Boolean =
        isOnline &&
            hasStorage &&
            authValid &&
            when (transferPreference) {
                TransferPreference.WifiOnly -> !isMetered
                TransferPreference.MeteredAllowed -> true
            }
}
