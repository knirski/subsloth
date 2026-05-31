package subsloth.core.model.library

import androidx.compose.runtime.Immutable
import subsloth.core.model.media.Media
import kotlin.time.Instant

/**
 * An item in the user's local library collection.
 *
 * Library state is maintained locally per account profile. Server-side
 * library mutations are not supported in v1 until Kodi parity is proven
 * (see server mutation gate requirement).
 *
 * This is a persistent record and does not contain any raw media URLs.
 */
@Immutable
data class LibraryItem(
    val mediaId: Media.MediaId,
    val collection: LibraryCollection,
    val addedAtEpochSeconds: Instant,
    val sortOrder: Int,
)

/** The type of library collection an item belongs to. */
@Immutable
enum class LibraryCollection {
    /** User's favorites / watchlist. */
    FAVORITES,

    /** Recently watched items. */
    HISTORY,

    /** Custom user-defined collection. */
    CUSTOM,
}
