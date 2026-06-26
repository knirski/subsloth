package net.subsloth.core.domain.port

import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media

/**
 * Port for reading and writing local library state.
 *
 * Implementations are provided by the Android persistence shell.
 */
interface LibraryPort {
    /** Returns the user's local library items. */
    suspend fun listLibrary(): Outcome<List<LibraryItem>>

    /** Adds an item to the local library. */
    suspend fun addToLibrary(item: LibraryItem): Outcome<Unit>

    /** Removes an item from the local library. */
    suspend fun removeFromLibrary(mediaId: Media.MediaId): Outcome<Unit>
}
