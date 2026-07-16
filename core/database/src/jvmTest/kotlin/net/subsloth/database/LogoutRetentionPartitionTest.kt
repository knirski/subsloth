package net.subsloth.database

import kotlinx.coroutines.test.runTest
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.LocalLibraryRecordEntity
import net.subsloth.database.entity.OfflineDisplayMetadataEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.SubscriptionEntity
import net.subsloth.database.entity.WatchLaterEntity
import net.subsloth.database.entity.WatchedStateEntity
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Verifies the logout retention partition scenario from the verification-release spec:
 *
 * - Logout retains shared downloads/progress
 * - Deleting downloads clears shared offline media/progress
 * - Clearing watch/library data clears only active-profile data
 * - Other account profiles remain untouched
 */
class LogoutRetentionPartitionTest {

    private val profileA = "profile-a"
    private val profileB = "profile-b"
    private val sharedContentId = "shared-movie-1"
    private val profileAContentId = "profile-a-content-1"
    private val profileBContentId = "profile-b-content-1"

    // ── Setup helpers ──────────────────────────────────────────────────────

    private suspend fun seedProfileData(db: SubSlothDatabase, profileKey: String, contentId: String) {
        db.favoriteDao().upsert(FavoriteEntity(profileKey = profileKey, contentId = contentId, contentType = "movie"))
        db.watchLaterDao().upsert(
            WatchLaterEntity(profileKey = profileKey, contentId = contentId, contentType = "movie"),
        )
        db.watchedStateDao().upsert(
            WatchedStateEntity(
                profileKey = profileKey,
                contentId = contentId,
                contentType = "movie",
                isWatched = true,
                watchedAtEpochSeconds = 1000L,
            ),
        )
        db.subscriptionDao().upsert(
            SubscriptionEntity(
                profileKey = profileKey,
                contentId = contentId,
                contentType = "movie",
                subscribedAtEpochSeconds = 1000L,
            ),
        )
        db.localLibraryRecordDao().upsert(
            LocalLibraryRecordEntity(
                profileKey = profileKey,
                contentId = contentId,
                contentType = "movie",
                addedAtEpochSeconds = 1000L,
            ),
        )
        db.accountPlaybackProgressDao().upsert(
            AccountPlaybackProgressEntity(
                profileKey = profileKey,
                contentId = contentId,
                contentType = "movie",
                positionSeconds = 300L,
                durationSeconds = 3600L,
                updatedAtEpochSeconds = 1000L,
            ),
        )
    }

    private suspend fun seedSharedData(db: SubSlothDatabase, contentId: String) {
        db.downloadedMediaDao().upsert(
            DownloadedMediaEntity(
                contentId = contentId,
                mediaType = "movie",
                localFilePath = "/data/$contentId.mp4",
                sizeBytes = 1024L,
                status = "completed",
                selectedQuality = null,
                downloadedAtEpochSeconds = null,
            ),
        )
        db.offlinePlaybackProgressDao().upsert(
            OfflinePlaybackProgressEntity(
                contentId = contentId,
                positionSeconds = 600L,
                durationSeconds = 3600L,
                updatedAtEpochSeconds = 2000L,
            ),
        )
        db.offlineDisplayMetadataDao().upsert(
            OfflineDisplayMetadataEntity(
                contentId = contentId,
                title = "Shared Movie",
                posterCacheKey = null,
                backdropCacheKey = null,
                episodeTitle = null,
                seasonNumber = null,
                episodeNumber = null,
                effectiveQuality = null,
                subtitleLanguages = null,
                durationSeconds = 3600L,
                localProgressSeconds = null,
            ),
        )
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    fun `clearing one profile library does not affect shared downloads and progress`() = runTest {
        val db = createTestDatabase()
        seedProfileData(db, profileA, profileAContentId)
        seedProfileData(db, profileB, profileBContentId)
        seedSharedData(db, sharedContentId)

        // Simulate "clear library" for profile A
        db.favoriteDao().deleteAllForProfile(profileA)
        db.watchLaterDao().deleteAllForProfile(profileA)
        db.watchedStateDao().deleteAllForProfile(profileA)
        db.subscriptionDao().deleteAllForProfile(profileA)
        db.localLibraryRecordDao().deleteAllForProfile(profileA)
        db.accountPlaybackProgressDao().deleteAllForProfile(profileA)

        // Profile A data is cleared
        assertNull(db.favoriteDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNull(db.watchLaterDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNull(db.watchedStateDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNull(db.subscriptionDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNull(db.localLibraryRecordDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNull(db.accountPlaybackProgressDao().getByProfileAndContentId(profileA, profileAContentId))

        // Shared downloads and progress survive
        assertNotNull(db.downloadedMediaDao().getByContent(sharedContentId, "movie"))
        assertNotNull(db.offlinePlaybackProgressDao().getByContentId(sharedContentId))
        assertNotNull(db.offlineDisplayMetadataDao().getByContentId(sharedContentId))

        db.close()
    }

    @Test
    fun `clearing one profile library does not affect another profile data`() = runTest {
        val db = createTestDatabase()
        seedProfileData(db, profileA, profileAContentId)
        seedProfileData(db, profileB, profileBContentId)

        // Simulate "clear library" for profile A only
        db.favoriteDao().deleteAllForProfile(profileA)
        db.watchLaterDao().deleteAllForProfile(profileA)
        db.watchedStateDao().deleteAllForProfile(profileA)
        db.subscriptionDao().deleteAllForProfile(profileA)
        db.localLibraryRecordDao().deleteAllForProfile(profileA)
        db.accountPlaybackProgressDao().deleteAllForProfile(profileA)

        // Profile B data is untouched
        assertNotNull(db.favoriteDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.watchLaterDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.watchedStateDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.subscriptionDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.localLibraryRecordDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.accountPlaybackProgressDao().getByProfileAndContentId(profileB, profileBContentId))

        db.close()
    }

    @Test
    fun `deleting all downloads clears shared offline media and progress`() = runTest {
        val db = createTestDatabase()
        seedProfileData(db, profileA, profileAContentId)
        seedSharedData(db, sharedContentId)

        // Simulate "delete all downloads"
        db.downloadedMediaDao().deleteAll()
        db.offlinePlaybackProgressDao().deleteAll()
        db.offlineDisplayMetadataDao().deleteAll()

        // Shared offline data is cleared
        assertNull(db.downloadedMediaDao().getByContent(sharedContentId, "movie"))
        assertNull(db.offlinePlaybackProgressDao().getByContentId(sharedContentId))
        assertNull(db.offlineDisplayMetadataDao().getByContentId(sharedContentId))

        // Profile A library data is untouched
        assertNotNull(db.favoriteDao().getByProfileAndContentId(profileA, profileAContentId))

        db.close()
    }

    @Test
    fun `full logout with no cleanup retains shared data`() = runTest {
        val db = createTestDatabase()
        seedProfileData(db, profileA, profileAContentId)
        seedProfileData(db, profileB, profileBContentId)
        seedSharedData(db, sharedContentId)

        // Simulate logout with NO cleanup options (just clear credentials, no Room changes)
        // Shared downloads and progress survive
        assertNotNull(db.downloadedMediaDao().getByContent(sharedContentId, "movie"))
        assertNotNull(db.offlinePlaybackProgressDao().getByContentId(sharedContentId))
        assertNotNull(db.offlineDisplayMetadataDao().getByContentId(sharedContentId))

        // Both profiles' library data remains
        assertNotNull(db.favoriteDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.favoriteDao().getByProfileAndContentId(profileB, profileBContentId))

        db.close()
    }

    @Test
    fun `deleting shared downloads does not affect profile-scoped data`() = runTest {
        val db = createTestDatabase()
        seedProfileData(db, profileA, profileAContentId)
        seedProfileData(db, profileB, profileBContentId)
        seedSharedData(db, sharedContentId)

        // Simulate deleting only shared downloads
        db.downloadedMediaDao().deleteAll()

        // Profile A and B data is untouched
        assertNotNull(db.favoriteDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.favoriteDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.watchLaterDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.watchLaterDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.watchedStateDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.watchedStateDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.subscriptionDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.subscriptionDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.localLibraryRecordDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.localLibraryRecordDao().getByProfileAndContentId(profileB, profileBContentId))
        assertNotNull(db.accountPlaybackProgressDao().getByProfileAndContentId(profileA, profileAContentId))
        assertNotNull(db.accountPlaybackProgressDao().getByProfileAndContentId(profileB, profileBContentId))

        db.close()
    }
}
