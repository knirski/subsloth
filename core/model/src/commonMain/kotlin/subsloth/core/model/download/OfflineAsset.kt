package subsloth.core.model.download

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableSet
import subsloth.core.model.identifier.LanguageCode
import subsloth.core.model.identifier.LocalMediaIdentifier
import subsloth.core.model.media.Media
import subsloth.core.model.media.QualityDescriptor
import subsloth.core.model.media.Subtitle

/** Unique identifier for a download or season queue operation. */
data class QueueId(val value: String)

/**
 * A relative path within the app's download directory.
 *
 * Construct directly only when the path is trusted. For untrusted input,
 * use [OfflineRelativePath.safe] which validates the path.
 */
data class OfflineRelativePath(val value: String) {
    companion object {
        /**
         * Creates an [OfflineRelativePath] after validating the input.
         *
         * @throws IllegalArgumentException if the path is blank, absolute,
         * or contains parent-directory traversal segments.
         */
        fun safe(path: String): OfflineRelativePath {
            require(path.isNotBlank()) { "path must not be blank" }
            val normalized = normalizePure(path)
            require(!normalized.startsWith("/")) { "path must not be absolute: $path" }
            require(".." !in normalized) { "path must not contain traversal segments: $path" }
            return OfflineRelativePath(normalized)
        }
    }
}

/** Pure-Kotlin path normalization (replaces java.nio.file.Paths for KMP compatibility). */
@PublishedApi
internal fun normalizePure(path: String): String {
    val isAbsolute = path.startsWith('/')
    val segments = path.split('/')
    val result =
        buildList {
            for (segment in segments) {
                when (segment) {
                    ".", "" -> {
                    }

                    ".." -> {
                        if (isNotEmpty() && last() != "..") {
                            removeAt(lastIndex)
                        } else if (!isAbsolute) {
                            add("..")
                        }
                    }

                    else -> add(segment)
                }
            }
        }
    val normalized = result.joinToString("/")
    return if (isAbsolute) "/$normalized" else normalized
}

/** Whether a download transfer may use a metered network connection. */
sealed interface TransferPreference {
    data object WifiOnly : TransferPreference

    data object MeteredAllowed : TransferPreference
}

/** Estimated size of a download or set of downloads. */
sealed interface SizeEstimate {
    /** Known byte count. */
    data class Known(val bytes: Long) : SizeEstimate {
        init {
            require(bytes >= 0) { "bytes must be non-negative" }
        }
    }

    /** Size is not yet known (e.g. before server negotiation). */
    data object Unknown : SizeEstimate
}

/** Result of selecting a subtitle track for a download. */
sealed interface SubtitleSelection {
    /** Preferred language track was found and selected. */
    data class Preferred(val subtitle: Subtitle) : SubtitleSelection

    /** Preferred language not found; English substituted as fallback. */
    data class EnglishFallback(val subtitle: Subtitle) : SubtitleSelection

    /** No matching subtitle track was found. */
    data object None : SubtitleSelection
}

/**
 * A downloaded media asset available for offline playback.
 *
 * Separate from [DownloadState.Completed] — this is a presentation-oriented
 * projection that includes display metadata and playability status, whereas
 * [DownloadState] is the canonical persistence record.
 */
@Immutable
data class OfflineAsset(
    val mediaId: Media.MediaId,
    val localId: LocalMediaIdentifier,
    val videoRelativePath: OfflineRelativePath,
    val subtitleLanguages: ImmutableSet<LanguageCode>,
    val effectiveQuality: QualityDescriptor,
    val displayTitle: String,
    val isPlayable: Boolean,
)
