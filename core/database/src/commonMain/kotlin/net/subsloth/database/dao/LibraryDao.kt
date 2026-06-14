package net.subsloth.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow
import net.subsloth.database.entity.AccountPlaybackProgressEntity
import net.subsloth.database.entity.DownloadedMediaEntity
import net.subsloth.database.entity.DownloadedSubtitleEntity
import net.subsloth.database.entity.FavoriteEntity
import net.subsloth.database.entity.LocalLibraryRecordEntity
import net.subsloth.database.entity.OfflineDisplayMetadataEntity
import net.subsloth.database.entity.OfflinePlaybackProgressEntity
import net.subsloth.database.entity.SubscriptionEntity
import net.subsloth.database.entity.WatchLaterEntity
import net.subsloth.database.entity.WatchedStateEntity

// ── Account-Scoped DAOs ────────────────────────────────────────────────────

@Dao
interface AccountPlaybackProgressDao {
    @Query("SELECT * FROM account_playback_progress WHERE profileKey = :profileKey AND contentId = :contentId")
    suspend fun getByProfileAndContentId(profileKey: String, contentId: String): AccountPlaybackProgressEntity?

    @Query("SELECT * FROM account_playback_progress WHERE profileKey = :profileKey")
    fun getAllForProfile(profileKey: String): Flow<List<AccountPlaybackProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AccountPlaybackProgressEntity)

    @Query("DELETE FROM account_playback_progress WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites WHERE profileKey = :profileKey")
    fun getAllForProfile(profileKey: String): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE profileKey = :profileKey AND contentId = :contentId")
    suspend fun getByProfileAndContentId(profileKey: String, contentId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteEntity)

    @Delete
    suspend fun delete(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later WHERE profileKey = :profileKey")
    fun getAllForProfile(profileKey: String): Flow<List<WatchLaterEntity>>

    @Query("SELECT * FROM watch_later WHERE profileKey = :profileKey AND contentId = :contentId")
    suspend fun getByProfileAndContentId(profileKey: String, contentId: String): WatchLaterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchLaterEntity)

    @Delete
    suspend fun delete(entity: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}

@Dao
interface WatchedStateDao {
    @Query("SELECT * FROM watched_state WHERE profileKey = :profileKey")
    fun getAllForProfile(profileKey: String): Flow<List<WatchedStateEntity>>

    @Query("SELECT * FROM watched_state WHERE profileKey = :profileKey AND contentId = :contentId")
    suspend fun getByProfileAndContentId(profileKey: String, contentId: String): WatchedStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: WatchedStateEntity)

    @Query("DELETE FROM watched_state WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions WHERE profileKey = :profileKey")
    fun getAllForProfile(profileKey: String): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE profileKey = :profileKey AND contentId = :contentId")
    suspend fun getByProfileAndContentId(profileKey: String, contentId: String): SubscriptionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}

@Dao
interface LocalLibraryRecordDao {
    @Query("SELECT * FROM local_library_records WHERE profileKey = :profileKey")
    fun getAllForProfile(profileKey: String): Flow<List<LocalLibraryRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LocalLibraryRecordEntity)

    @Query("DELETE FROM local_library_records WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}

// ── Shared Offline DAOs (no profile key) ───────────────────────────────────

@Dao
interface DownloadedMediaDao {
    @Query("SELECT * FROM downloaded_media")
    fun getAll(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE status = 'completed'")
    fun getCompleted(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE contentId = :contentId AND mediaType = :mediaType")
    suspend fun getByContent(contentId: String, mediaType: String): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedMediaEntity)

    @Delete
    suspend fun delete(entity: DownloadedMediaEntity)

    @Query("DELETE FROM downloaded_media")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM downloaded_media WHERE status = 'completed'")
    suspend fun countCompleted(): Int
}

@Dao
interface DownloadedSubtitleDao {
    @Query("SELECT * FROM downloaded_subtitles WHERE downloadId = :downloadId")
    fun getForDownload(downloadId: Long): Flow<List<DownloadedSubtitleEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DownloadedSubtitleEntity)

    @Delete
    suspend fun delete(entity: DownloadedSubtitleEntity)

    @Query("DELETE FROM downloaded_subtitles WHERE downloadId = :downloadId")
    suspend fun deleteForDownload(downloadId: Long)
}

@Dao
interface OfflineDisplayMetadataDao {
    @Query("SELECT * FROM offline_display_metadata")
    fun getAll(): Flow<List<OfflineDisplayMetadataEntity>>

    @Query("SELECT * FROM offline_display_metadata WHERE contentId = :contentId")
    suspend fun getByContentId(contentId: String): OfflineDisplayMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineDisplayMetadataEntity)

    @Delete
    suspend fun delete(entity: OfflineDisplayMetadataEntity)

    @Query("DELETE FROM offline_display_metadata")
    suspend fun deleteAll()
}

@Dao
interface OfflinePlaybackProgressDao {
    @Query("SELECT * FROM offline_playback_progress WHERE contentId = :contentId")
    suspend fun getByContentId(contentId: String): OfflinePlaybackProgressEntity?

    @Query("SELECT * FROM offline_playback_progress")
    fun getAll(): Flow<List<OfflinePlaybackProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflinePlaybackProgressEntity)

    @Query("DELETE FROM offline_playback_progress")
    suspend fun deleteAll()
}
