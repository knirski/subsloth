package subsloth.core.domain.policy

import subsloth.core.model.download.SubtitleSelection
import subsloth.core.model.download.TransferPreference
import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.media.Subtitle

/** Pure policies for season-level batch download queues: subtitle selection and queue resume gating. */
object SeasonQueuePolicy {
    fun selectInitialSubtitle(available: List<Subtitle>, preferred: LanguageCode): SubtitleSelection {
        val english = LanguageCode("en")
        val preferredTrack = available.firstOrNull { it.language == preferred }
        val englishTrack = available.firstOrNull { it.language == english }
        return when {
            preferredTrack != null -> SubtitleSelection.Preferred(preferredTrack)
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
    ): Boolean = isOnline &&
        hasStorage &&
        authValid &&
        DownloadPolicy.canTransferOnNetwork(
            isMetered = isMetered,
            transferPreference = transferPreference,
        )
}
