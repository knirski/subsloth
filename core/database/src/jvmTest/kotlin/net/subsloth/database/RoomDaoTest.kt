package net.subsloth.database

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import net.subsloth.database.entity.CachedCatalogCountryEntity
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity
import net.subsloth.database.entity.CachedCatalogItemWithMetadata
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoomDaoTest {
    // ── Catalog helpers ──────────────────────────────────────────────

    private fun catalogItem(contentId: String, contentType: String = "movie", title: String = "Title") =
        CachedCatalogItemEntity(
            contentId = contentId,
            contentType = contentType,
            title = title,
            plot = null,
            posterUrl = null,
            backdropUrl = null,
            year = null,
            rating = null,
            durationMinutes = null,
            slug = null,
            imdbId = null,
            tmdbId = null,
            status = null,
            updatedAtEpochSeconds = null,
            newestVideoEpochSeconds = null,
        )

    // ── Catalog DAO tests ────────────────────────────────────────────

    @Test
    fun `cachedCatalog upsertAll inserts items and count matches`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAll(listOf(catalogItem("1"), catalogItem("2", contentType = "show", title = "Beta")))
        assertEquals(2, dao.count())
        db.close()
    }

    @Test
    fun `cachedCatalog upsertAll replaces duplicate contentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAll(listOf(catalogItem("1", title = "Original")))
        dao.upsertAll(listOf(catalogItem("1", title = "Updated")))
        assertEquals(1, dao.count())
        dao.getAllByType("movie").test {
            assertEquals("Updated", awaitItem().first().title)
        }
        db.close()
    }

    @Test
    fun `cachedCatalog deleteAll clears table`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAll(listOf(catalogItem("1")))
        assertEquals(1, dao.count())
        dao.deleteAll()
        assertEquals(0, dao.count())
        db.close()
    }

    @Test
    fun `cachedCatalog getAllByType returns only matching type`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAll(
            listOf(
                catalogItem("1", title = "Movie"),
                catalogItem("2", contentType = "show", title = "Show"),
            ),
        )
        dao.getAllByType("movie").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("Movie", list.first().title)
        }
        db.close()
    }

    @Test
    fun `cachedCatalog getAllGenres returns inserted genres`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAllGenres(
            listOf(
                CachedCatalogGenreEntity(contentId = "1", genre = "Action"),
                CachedCatalogGenreEntity(contentId = "1", genre = "Drama"),
            ),
        )
        dao.getAllGenres().test {
            val genres = awaitItem()
            assertEquals(2, genres.size)
            assertTrue(genres.any { it.genre == "Action" })
            assertTrue(genres.any { it.genre == "Drama" })
        }
        db.close()
    }

    @Test
    fun `cachedCatalog upsertAllCountries inserts countries`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAllCountries(
            listOf(
                CachedCatalogCountryEntity(contentId = "1", country = "US"),
                CachedCatalogCountryEntity(contentId = "1", country = "GB"),
            ),
        )
        dao.getAllCountries().test {
            assertEquals(2, awaitItem().size)
        }
        db.close()
    }

    @Test
    fun `cachedCatalog replaceAll replaces all items atomically`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.upsertAll(listOf(catalogItem("1", title = "Old Movie")))
        dao.upsertAllGenres(listOf(CachedCatalogGenreEntity(contentId = "1", genre = "Old Genre")))
        dao.replaceAll(
            listOf(
                CachedCatalogItemWithMetadata(
                    item = catalogItem("2", contentType = "show", title = "New Show"),
                    genres = listOf(CachedCatalogGenreEntity(contentId = "2", genre = "Sci-Fi")),
                    countries = listOf(CachedCatalogCountryEntity(contentId = "2", country = "JP")),
                ),
            ),
        )
        assertEquals(1, dao.count())
        dao.getAllByType("show").test {
            assertEquals("New Show", awaitItem().first().title)
        }
        dao.getAllGenres().test {
            val genres = awaitItem()
            assertEquals(1, genres.size)
            assertEquals("Sci-Fi", genres.first().genre)
        }
        dao.getAllCountries().test {
            val countries = awaitItem()
            assertEquals(1, countries.size)
            assertEquals("JP", countries.first().country)
        }
        db.close()
    }

    @Test
    fun `cachedCatalog Flow emits updates on insert`() = runTest {
        val db = createTestDatabase()
        val dao = db.cachedCatalogDao()
        dao.getAllByType("movie").test {
            assertEquals(0, awaitItem().size)
            dao.upsertAll(listOf(catalogItem("1")))
            assertEquals(1, awaitItem().size)
        }
        db.close()
    }

    // ── Favorite DAO tests ───────────────────────────────────────────

    @Test
    fun `favorite upsert inserts and getByProfileAndContentId returns entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.favoriteDao()
        dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "movie"))
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals("movie", result?.contentType)
        db.close()
    }

    @Test
    fun `favorite upsert replaces existing entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.favoriteDao()
        dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "movie"))
        dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "show"))
        assertEquals("show", dao.getByProfileAndContentId("user1", "100")?.contentType)
        db.close()
    }

    @Test
    fun `favorite getAllForProfile returns only matching profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.favoriteDao()
        dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "movie"))
        dao.upsert(FavoriteEntity(profileKey = "user2", contentId = "200", contentType = "show"))
        dao.getAllForProfile("user1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("100", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `favorite delete removes entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.favoriteDao()
        dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "movie"))
        val inserted = dao.getByProfileAndContentId("user1", "100")!!
        dao.delete(inserted)
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        db.close()
    }

    @Test
    fun `favorite deleteAllForProfile clears only that profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.favoriteDao()
        dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "movie"))
        dao.upsert(FavoriteEntity(profileKey = "user2", contentId = "200", contentType = "show"))
        dao.deleteAllForProfile("user1")
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        assertEquals("show", dao.getByProfileAndContentId("user2", "200")?.contentType)
        db.close()
    }

    @Test
    fun `favorite getAllForProfile Flow emits updates`() = runTest {
        val db = createTestDatabase()
        val dao = db.favoriteDao()
        dao.getAllForProfile("user1").test {
            assertEquals(0, awaitItem().size)
            dao.upsert(FavoriteEntity(profileKey = "user1", contentId = "100", contentType = "movie"))
            assertEquals(1, awaitItem().size)
        }
        db.close()
    }

    // ── DownloadedMedia DAO tests ────────────────────────────────────

    private fun download(contentId: String, mediaType: String = "movie", status: String = "completed") =
        DownloadedMediaEntity(
            contentId = contentId,
            mediaType = mediaType,
            localFilePath = "/data/$contentId.mp4",
            sizeBytes = 1024,
            status = status,
            selectedQuality = null,
            downloadedAtEpochSeconds = null,
        )

    @Test
    fun `downloadedMedia upsert inserts and getByContent returns entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("100"))
        val result = dao.getByContent("100", "movie")
        assertEquals("/data/100.mp4", result?.localFilePath)
        db.close()
    }

    @Test
    fun `downloadedMedia getAll returns all downloads`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("1"))
        dao.upsert(download("2", mediaType = "episode", status = "downloading"))
        dao.getAll().test {
            assertEquals(2, awaitItem().size)
        }
        db.close()
    }

    @Test
    fun `downloadedMedia getCompleted returns only completed`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("1", status = "completed"))
        dao.upsert(download("2", status = "downloading"))
        dao.getCompleted().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("1", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `downloadedMedia countCompleted returns correct count`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("1"))
        dao.upsert(download("2"))
        dao.upsert(download("3", status = "failed"))
        assertEquals(2, dao.countCompleted())
        db.close()
    }

    @Test
    fun `downloadedMedia delete removes entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("1"))
        val inserted = dao.getByContent("1", "movie")!!
        dao.delete(inserted)
        assertNull(dao.getByContent("1", "movie"))
        db.close()
    }

    @Test
    fun `downloadedMedia deleteAll clears all`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("1"))
        dao.upsert(download("2"))
        dao.deleteAll()
        assertEquals(0, dao.countCompleted())
        db.close()
    }

    @Test
    fun `downloadedMedia getById returns entity by primary key`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        dao.upsert(download("100"))
        val inserted = dao.getByContent("100", "movie")!!
        val found = dao.getById(inserted.id)
        assertEquals("100", found?.contentId)
        db.close()
    }

    @Test
    fun `downloadedMedia getById returns null for nonexistent id`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedMediaDao()
        assertNull(dao.getById(999))
        db.close()
    }

    // ── SeasonQueue DAO tests ────────────────────────────────────────

    private fun queue(
        id: String,
        showId: String = "s1",
        seasonNumber: Int = 1,
        status: String = "pending",
        createdAtEpochSeconds: Long = 1000,
    ) = SeasonQueueEntity(
        id = id,
        showId = showId,
        seasonNumber = seasonNumber,
        status = status,
        createdAtEpochSeconds = createdAtEpochSeconds,
    )

    private fun queueItem(
        queueId: String,
        episodeId: String = "e1",
        episodeTitle: String = "Pilot",
        status: String = "pending",
    ) = QueueItemEntity(
        queueId = queueId,
        episodeId = episodeId,
        episodeTitle = episodeTitle,
        qualityLabel = null,
        subtitleLanguages = null,
        sizeBytes = null,
        status = status,
    )

    @Test
    fun `seasonQueue upsertQueue inserts queue`() = runTest {
        val db = createTestDatabase()
        val dao = db.seasonQueueDao()
        dao.upsertQueue(queue("q1"))
        val result = dao.getQueue("q1")
        assertEquals("s1", result?.showId)
        assertEquals(1, result?.seasonNumber)
        db.close()
    }

    @Test
    fun `seasonQueue upsertItem inserts item linked to queue`() = runTest {
        val db = createTestDatabase()
        val dao = db.seasonQueueDao()
        dao.upsertQueue(queue("q1"))
        dao.upsertItem(queueItem("q1"))
        val items = dao.getItemsForQueue("q1")
        assertEquals(1, items.size)
        assertEquals("e1", items.first().episodeId)
        db.close()
    }

    @Test
    fun `seasonQueue getItemsForQueue returns items for given queue`() = runTest {
        val db = createTestDatabase()
        val dao = db.seasonQueueDao()
        dao.upsertQueue(queue("q1"))
        dao.upsertQueue(queue("q2", showId = "s2", seasonNumber = 2))
        dao.upsertItem(queueItem("q1"))
        dao.upsertItem(queueItem("q2", episodeId = "e2", episodeTitle = "P2"))
        val q1Items = dao.getItemsForQueue("q1")
        assertEquals(1, q1Items.size)
        assertEquals("e1", q1Items.first().episodeId)
        db.close()
    }

    @Test
    fun `seasonQueue deleteQueue cascades to QueueItemEntity`() = runTest {
        val db = createTestDatabase()
        val dao = db.seasonQueueDao()
        dao.upsertQueue(queue("q1", status = "completed"))
        dao.upsertItem(queueItem("q1", status = "completed"))
        dao.upsertItem(queueItem("q1", episodeId = "e2", episodeTitle = "P2", status = "completed"))
        dao.deleteQueue("q1")
        assertNull(dao.getQueue("q1"))
        assertTrue(dao.getItemsForQueue("q1").isEmpty())
        db.close()
    }

    @Test
    fun `seasonQueue getAllQueues emits updates`() = runTest {
        val db = createTestDatabase()
        val dao = db.seasonQueueDao()
        dao.getAllQueues().test {
            assertEquals(0, awaitItem().size)
            dao.upsertQueue(queue("q1"))
            assertEquals(1, awaitItem().size)
        }
        db.close()
    }
}
