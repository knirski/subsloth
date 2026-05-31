package subsloth.core.model.playback

import androidx.compose.runtime.Immutable

/**
 * Identifies whether playback is online (streamed) or offline (local file).
 *
 * Online playback uses ephemeral signed URLs that may require refresh.
 * Offline playback uses local files and must not attempt network refresh.
 */
@Immutable
enum class PlaybackMode {
    /** Streaming from a remote server using ephemeral signed URLs. */
    ONLINE,

    /** Playing a downloaded local file without network dependency. */
    OFFLINE,
}
