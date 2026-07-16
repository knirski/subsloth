package net.subsloth.database

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.CachedCatalogCountryEntity
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity
import net.subsloth.database.entity.CachedCatalogItemWithMetadata
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.DownloadedSubtitleEntity
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.LocalLibraryRecordEntity
import net.subsloth.database.entity.OfflineDisplayMetadataEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.QueueItemEntity
import net.subsloth.database.entity.SeasonQueueEntity
import net.subsloth.database.entity.SubscriptionEntity
import net.subsloth.database.entity.WatchLaterEntity
import net.subsloth.database.entity.WatchedStateEntity
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

    // ── AccountPlaybackProgress DAO tests ────────────────────────────

    private fun progress(
        profileKey: String = "user1",
        contentId: String = "100",
        contentType: String = "movie",
        positionSeconds: Long = 300,
        durationSeconds: Long = 3600,
        updatedAtEpochSeconds: Long = 1000,
    ) = AccountPlaybackProgressEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = contentType,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        updatedAtEpochSeconds = updatedAtEpochSeconds,
    )

    @Test
    fun `accountPlaybackProgress upsert and getByProfileAndContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.accountPlaybackProgressDao()
        dao.upsert(progress())
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals("movie", result?.contentType)
        assertEquals(300, result?.positionSeconds)
        db.close()
    }

    @Test
    fun `accountPlaybackProgress upsert replaces existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.accountPlaybackProgressDao()
        dao.upsert(progress(positionSeconds = 300))
        dao.upsert(progress(positionSeconds = 600, updatedAtEpochSeconds = 2000))
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals(600, result?.positionSeconds)
        db.close()
    }

    @Test
    fun `accountPlaybackProgress getAllForProfile returns only matching profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.accountPlaybackProgressDao()
        dao.upsert(progress(profileKey = "user1", contentId = "100"))
        dao.upsert(progress(profileKey = "user2", contentId = "200"))
        dao.getAllForProfile("user1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("100", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `accountPlaybackProgress deleteAllForProfile isolates`() = runTest {
        val db = createTestDatabase()
        val dao = db.accountPlaybackProgressDao()
        dao.upsert(progress(profileKey = "user1", contentId = "100"))
        dao.upsert(progress(profileKey = "user2", contentId = "200"))
        dao.deleteAllForProfile("user1")
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        assertEquals(300L, dao.getByProfileAndContentId("user2", "200")?.positionSeconds)
        db.close()
    }

    @Test
    fun `accountPlaybackProgress getAllForProfile Flow emits updates`() = runTest {
        val db = createTestDatabase()
        val dao = db.accountPlaybackProgressDao()
        dao.getAllForProfile("user1").test {
            assertEquals(0, awaitItem().size)
            dao.upsert(progress())
            assertEquals(1, awaitItem().size)
        }
        db.close()
    }

    // ── WatchLater DAO tests ──────────────────────────────────────────

    private fun watchLater(profileKey: String = "user1", contentId: String = "100", contentType: String = "movie") =
        WatchLaterEntity(
            profileKey = profileKey,
            contentId = contentId,
            contentType = contentType,
        )

    @Test
    fun `watchLater upsert and getByProfileAndContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchLaterDao()
        dao.upsert(watchLater())
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals("movie", result?.contentType)
        db.close()
    }

    @Test
    fun `watchLater upsert replaces existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchLaterDao()
        dao.upsert(watchLater(contentType = "movie"))
        dao.upsert(watchLater(contentType = "show"))
        assertEquals("show", dao.getByProfileAndContentId("user1", "100")?.contentType)
        db.close()
    }

    @Test
    fun `watchLater getAllForProfile returns only matching profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchLaterDao()
        dao.upsert(watchLater(profileKey = "user1", contentId = "100"))
        dao.upsert(watchLater(profileKey = "user2", contentId = "200"))
        dao.getAllForProfile("user1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("100", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `watchLater delete removes entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchLaterDao()
        dao.upsert(watchLater())
        val inserted = dao.getByProfileAndContentId("user1", "100")!!
        dao.delete(inserted)
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        db.close()
    }

    @Test
    fun `watchLater deleteAllForProfile clears only that profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchLaterDao()
        dao.upsert(watchLater(profileKey = "user1", contentId = "100"))
        dao.upsert(watchLater(profileKey = "user2", contentId = "200"))
        dao.deleteAllForProfile("user1")
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        assertEquals("200", dao.getByProfileAndContentId("user2", "200")?.contentId)
        db.close()
    }

    // ── WatchedState DAO tests ────────────────────────────────────────

    private fun watchedState(
        profileKey: String = "user1",
        contentId: String = "100",
        contentType: String = "movie",
        isWatched: Boolean = true,
        watchedAtEpochSeconds: Long? = 1000,
    ) = WatchedStateEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = contentType,
        isWatched = isWatched,
        watchedAtEpochSeconds = watchedAtEpochSeconds,
    )

    @Test
    fun `watchedState upsert and getByProfileAndContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchedStateDao()
        dao.upsert(watchedState())
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals(true, result?.isWatched)
        assertEquals(1000, result?.watchedAtEpochSeconds)
        db.close()
    }

    @Test
    fun `watchedState upsert updates existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchedStateDao()
        dao.upsert(watchedState(isWatched = true, watchedAtEpochSeconds = 1000))
        dao.upsert(watchedState(isWatched = true, watchedAtEpochSeconds = 2000))
        assertEquals(2000, dao.getByProfileAndContentId("user1", "100")?.watchedAtEpochSeconds)
        db.close()
    }

    @Test
    fun `watchedState getAllForProfile returns only matching profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchedStateDao()
        dao.upsert(watchedState(profileKey = "user1", contentId = "100"))
        dao.upsert(watchedState(profileKey = "user2", contentId = "200"))
        dao.getAllForProfile("user1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("100", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `watchedState deleteAllForProfile isolates`() = runTest {
        val db = createTestDatabase()
        val dao = db.watchedStateDao()
        dao.upsert(watchedState(profileKey = "user1", contentId = "100"))
        dao.upsert(watchedState(profileKey = "user2", contentId = "200"))
        dao.deleteAllForProfile("user1")
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        assertTrue(dao.getByProfileAndContentId("user2", "200")?.isWatched == true)
        db.close()
    }

    // ── Subscription DAO tests ────────────────────────────────────────

    private fun subscription(
        profileKey: String = "user1",
        contentId: String = "100",
        contentType: String = "movie",
        subscribedAtEpochSeconds: Long = 5000,
    ) = SubscriptionEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = contentType,
        subscribedAtEpochSeconds = subscribedAtEpochSeconds,
    )

    @Test
    fun `subscription upsert and getByProfileAndContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.subscriptionDao()
        dao.upsert(subscription())
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals("movie", result?.contentType)
        assertEquals(5000, result?.subscribedAtEpochSeconds)
        db.close()
    }

    @Test
    fun `subscription upsert replaces existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.subscriptionDao()
        dao.upsert(subscription(subscribedAtEpochSeconds = 5000))
        dao.upsert(subscription(subscribedAtEpochSeconds = 6000))
        assertEquals(6000, dao.getByProfileAndContentId("user1", "100")?.subscribedAtEpochSeconds)
        db.close()
    }

    @Test
    fun `subscription getAllForProfile returns only matching profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.subscriptionDao()
        dao.upsert(subscription(profileKey = "user1", contentId = "100"))
        dao.upsert(subscription(profileKey = "user2", contentId = "200"))
        dao.getAllForProfile("user1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("100", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `subscription deleteAllForProfile isolates`() = runTest {
        val db = createTestDatabase()
        val dao = db.subscriptionDao()
        dao.upsert(subscription(profileKey = "user1", contentId = "100"))
        dao.upsert(subscription(profileKey = "user2", contentId = "200"))
        dao.deleteAllForProfile("user1")
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        assertEquals(5000L, dao.getByProfileAndContentId("user2", "200")?.subscribedAtEpochSeconds)
        db.close()
    }

    // ── LocalLibraryRecord DAO tests ──────────────────────────────────

    private fun libraryRecord(
        profileKey: String = "user1",
        contentId: String = "100",
        contentType: String = "show",
        addedAtEpochSeconds: Long = 8000,
    ) = LocalLibraryRecordEntity(
        profileKey = profileKey,
        contentId = contentId,
        contentType = contentType,
        addedAtEpochSeconds = addedAtEpochSeconds,
    )

    @Test
    fun `localLibraryRecord upsert and getByProfileAndContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.localLibraryRecordDao()
        dao.upsert(libraryRecord())
        val result = dao.getByProfileAndContentId("user1", "100")
        assertEquals("show", result?.contentType)
        assertEquals(8000, result?.addedAtEpochSeconds)
        db.close()
    }

    @Test
    fun `localLibraryRecord upsert replaces existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.localLibraryRecordDao()
        dao.upsert(libraryRecord(contentType = "show"))
        dao.upsert(libraryRecord(contentType = "movie"))
        assertEquals("movie", dao.getByProfileAndContentId("user1", "100")?.contentType)
        db.close()
    }

    @Test
    fun `localLibraryRecord getAllForProfile returns only matching profile`() = runTest {
        val db = createTestDatabase()
        val dao = db.localLibraryRecordDao()
        dao.upsert(libraryRecord(profileKey = "user1", contentId = "100"))
        dao.upsert(libraryRecord(profileKey = "user2", contentId = "200"))
        dao.getAllForProfile("user1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("100", list.first().contentId)
        }
        db.close()
    }

    @Test
    fun `localLibraryRecord delete removes entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.localLibraryRecordDao()
        dao.upsert(libraryRecord())
        val inserted = dao.getByProfileAndContentId("user1", "100")!!
        dao.delete(inserted)
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        db.close()
    }

    @Test
    fun `localLibraryRecord deleteAllForProfile isolates`() = runTest {
        val db = createTestDatabase()
        val dao = db.localLibraryRecordDao()
        dao.upsert(libraryRecord(profileKey = "user1", contentId = "100"))
        dao.upsert(libraryRecord(profileKey = "user2", contentId = "200"))
        dao.deleteAllForProfile("user1")
        assertNull(dao.getByProfileAndContentId("user1", "100"))
        assertEquals("200", dao.getByProfileAndContentId("user2", "200")?.contentId)
        db.close()
    }

    // ── DownloadedSubtitle DAO tests ──────────────────────────────────

    private fun subtitle(
        downloadId: Long = 1,
        language: String = "en",
        source: String? = "opensubtitles",
        format: String? = "srt",
        localFilePath: String = "/data/1/en.srt",
    ) = DownloadedSubtitleEntity(
        downloadId = downloadId,
        language = language,
        source = source,
        format = format,
        localFilePath = localFilePath,
    )

    @Test
    fun `downloadedSubtitle upsert and getForDownload`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedSubtitleDao()
        dao.upsert(subtitle())
        dao.getForDownload(1).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("en", list.first().language)
        }
        db.close()
    }

    @Test
    fun `downloadedSubtitle returns subtitles for specific download`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedSubtitleDao()
        dao.upsert(subtitle(downloadId = 1, language = "en"))
        dao.upsert(subtitle(downloadId = 2, language = "pl"))
        dao.getForDownload(1).test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("en", list.first().language)
        }
        db.close()
    }

    @Test
    fun `downloadedSubtitle delete removes entity`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedSubtitleDao()
        dao.upsert(subtitle())
        dao.getForDownload(1).test {
            val list = awaitItem()
            val entity = list.first()
            dao.delete(entity)
        }
        dao.getForDownload(1).test {
            assertTrue(awaitItem().isEmpty())
        }
        db.close()
    }

    @Test
    fun `downloadedSubtitle deleteForDownload removes all for download`() = runTest {
        val db = createTestDatabase()
        val dao = db.downloadedSubtitleDao()
        dao.upsert(subtitle(downloadId = 1, language = "en"))
        dao.upsert(subtitle(downloadId = 1, language = "pl"))
        dao.deleteForDownload(1)
        dao.getForDownload(1).test {
            assertTrue(awaitItem().isEmpty())
        }
        db.close()
    }

    // ── OfflineDisplayMetadata DAO tests ──────────────────────────────

    private fun displayMetadata(
        contentId: String = "100",
        title: String = "Test Movie",
        posterCacheKey: String? = null,
        durationSeconds: Long? = 3600,
    ) = OfflineDisplayMetadataEntity(
        contentId = contentId,
        title = title,
        posterCacheKey = posterCacheKey,
        backdropCacheKey = null,
        episodeTitle = null,
        seasonNumber = null,
        episodeNumber = null,
        effectiveQuality = null,
        subtitleLanguages = null,
        durationSeconds = durationSeconds,
        localProgressSeconds = null,
    )

    @Test
    fun `offlineDisplayMetadata upsert and getByContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlineDisplayMetadataDao()
        dao.upsert(displayMetadata())
        val result = dao.getByContentId("100")
        assertEquals("Test Movie", result?.title)
        db.close()
    }

    @Test
    fun `offlineDisplayMetadata upsert replaces existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlineDisplayMetadataDao()
        dao.upsert(displayMetadata(title = "Original"))
        dao.upsert(displayMetadata(title = "Updated"))
        assertEquals("Updated", dao.getByContentId("100")?.title)
        db.close()
    }

    @Test
    fun `offlineDisplayMetadata getAll returns all`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlineDisplayMetadataDao()
        dao.upsert(displayMetadata(contentId = "100", title = "Movie 1"))
        dao.upsert(displayMetadata(contentId = "200", title = "Movie 2"))
        dao.getAll().test {
            assertEquals(2, awaitItem().size)
        }
        db.close()
    }

    @Test
    fun `offlineDisplayMetadata deleteAll clears all`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlineDisplayMetadataDao()
        dao.upsert(displayMetadata())
        dao.deleteAll()
        dao.getAll().test {
            assertTrue(awaitItem().isEmpty())
        }
        db.close()
    }

    @Test
    fun `offlineDisplayMetadata delete removes single`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlineDisplayMetadataDao()
        dao.upsert(displayMetadata())
        val entity = dao.getByContentId("100")!!
        dao.delete(entity)
        assertNull(dao.getByContentId("100"))
        db.close()
    }

    // ── OfflinePlaybackProgress DAO tests ─────────────────────────────

    private fun offlineProgress(
        contentId: String = "100",
        positionSeconds: Long = 600,
        durationSeconds: Long = 3600,
        updatedAtEpochSeconds: Long = 2000,
    ) = OfflinePlaybackProgressEntity(
        contentId = contentId,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        updatedAtEpochSeconds = updatedAtEpochSeconds,
    )

    @Test
    fun `offlinePlaybackProgress upsert and getByContentId`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlinePlaybackProgressDao()
        dao.upsert(offlineProgress())
        val result = dao.getByContentId("100")
        assertEquals(600, result?.positionSeconds)
        db.close()
    }

    @Test
    fun `offlinePlaybackProgress upsert replaces existing`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlinePlaybackProgressDao()
        dao.upsert(offlineProgress(positionSeconds = 600))
        dao.upsert(offlineProgress(positionSeconds = 1200, updatedAtEpochSeconds = 3000))
        assertEquals(1200, dao.getByContentId("100")?.positionSeconds)
        db.close()
    }

    @Test
    fun `offlinePlaybackProgress getAll returns all`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlinePlaybackProgressDao()
        dao.upsert(offlineProgress(contentId = "100"))
        dao.upsert(offlineProgress(contentId = "200"))
        dao.getAll().test {
            assertEquals(2, awaitItem().size)
        }
        db.close()
    }

    @Test
    fun `offlinePlaybackProgress deleteAll clears all`() = runTest {
        val db = createTestDatabase()
        val dao = db.offlinePlaybackProgressDao()
        dao.upsert(offlineProgress())
        dao.deleteAll()
        assertNull(dao.getByContentId("100"))
        db.close()
    }
}
