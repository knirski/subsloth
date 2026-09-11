package net.subsloth.database

import kotlinx.coroutines.test.runTest
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.model.error.fold
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.library.LibraryCollection
import net.subsloth.core.model.library.LibraryItem
import net.subsloth.core.model.media.Media
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.LocalLibraryRecordEntity
import net.subsloth.database.entity.WatchLaterEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class LibraryPortAdapterTest {
    @Test
    fun `listLibrary includes favorites watch later and custom items`() = runTest {
        val db = createTestDatabase()
        try {
            val session = signedInSession()
            db.favoriteDao().upsert(favorite(profileKey = "user", contentId = "1"))
            db.watchLaterDao().upsert(watchLater(profileKey = "user", contentId = "2"))
            db.localLibraryRecordDao().upsert(custom(profileKey = "user", contentId = "3"))
            val adapter = adapter(db, session)

            val items = adapter.listLibrary().fold(
                onSuccess = { it },
                onFailure = { error("unexpected failure: $it") },
            )

            assertEquals(3, items.size)
            assertEquals(
                listOf(
                    LibraryCollection.FAVORITES,
                    LibraryCollection.HISTORY,
                    LibraryCollection.CUSTOM,
                ),
                items.map { it.collection },
            )
            assertTrue(items.any { it.mediaId == Media.MediaId.Movie(MovieId(1)) })
            assertTrue(items.any { it.mediaId == Media.MediaId.Movie(MovieId(2)) })
            assertTrue(items.any { it.mediaId == Media.MediaId.Show(ShowId(3)) })
        } finally {
            db.close()
        }
    }

    @Test
    fun `listLibrary excludes other profiles`() = runTest {
        val db = createTestDatabase()
        try {
            val session = signedInSession()
            db.favoriteDao().upsert(favorite(profileKey = "user", contentId = "1"))
            db.favoriteDao().upsert(favorite(profileKey = "other", contentId = "2"))
            db.watchLaterDao().upsert(watchLater(profileKey = "other", contentId = "3"))
            val adapter = adapter(db, session)

            val items = adapter.listLibrary().fold(
                onSuccess = { it },
                onFailure = { error("unexpected failure: $it") },
            )

            assertEquals(1, items.size)
            assertEquals(Media.MediaId.Movie(MovieId(1)), items.single().mediaId)
            assertEquals(LibraryCollection.FAVORITES, items.single().collection)
        } finally {
            db.close()
        }
    }

    @Test
    fun `addToLibrary history writes watch later and reads it back`() = runTest {
        val db = createTestDatabase()
        try {
            val session = signedInSession()
            val adapter = adapter(db, session)

            adapter.addToLibrary(
                LibraryItem(
                    mediaId = Media.MediaId.Movie(MovieId(4)),
                    collection = LibraryCollection.HISTORY,
                    addedAtEpochSeconds = Instant.fromEpochSeconds(0),
                    sortOrder = 0,
                ),
            )

            assertNotNull(db.watchLaterDao().getByProfileAndContentId("user", "4"))
            val items = adapter.listLibrary().fold(
                onSuccess = { it },
                onFailure = { error("unexpected failure: $it") },
            )
            assertEquals(LibraryCollection.HISTORY, items.single().collection)
            assertEquals(Media.MediaId.Movie(MovieId(4)), items.single().mediaId)
        } finally {
            db.close()
        }
    }

    @Test
    fun `removeFromLibrary removes watch later row`() = runTest {
        val db = createTestDatabase()
        try {
            val session = signedInSession()
            db.watchLaterDao().upsert(watchLater(profileKey = "user", contentId = "2"))
            val adapter = adapter(db, session)

            adapter.removeFromLibrary(Media.MediaId.Movie(MovieId(2)))

            assertNull(db.watchLaterDao().getByProfileAndContentId("user", "2"))
        } finally {
            db.close()
        }
    }

    private suspend fun signedInSession(): InMemorySessionState = InMemorySessionState().apply {
        open(Credentials(login = "user@example.com", password = "secret"))
    }

    private fun adapter(db: SubSlothDatabase, session: InMemorySessionState): LibraryPortAdapter = LibraryPortAdapter(
        favoriteDao = db.favoriteDao(),
        watchLaterDao = db.watchLaterDao(),
        localLibraryDao = db.localLibraryRecordDao(),
        sessionPort = session,
    )

    private fun favorite(profileKey: String, contentId: String) = FavoriteEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = "movie",
    )

    private fun watchLater(profileKey: String, contentId: String) = WatchLaterEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = "movie",
    )

    private fun custom(profileKey: String, contentId: String) = LocalLibraryRecordEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = "show",
        addedAtEpochSeconds = 0,
    )
}
