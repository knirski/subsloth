package net.subsloth.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// ── Account-Scoped Entities ────────────────────────────────────────────────

/**
 * Cached online metadata for a movie or show.
 * Scoped by account profile key for isolation.
 */
@Entity(
    tableName = "cached_online_metadata",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class CachedOnlineMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String, // "movie" or "show"
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val genres: String?, // JSON-encoded list
    val runtime: Int?,
    val rating: Double?,
    val cachedAtEpochSeconds: Long,
)

/**
 * Streamed/online playback progress, scoped by account.
 */
@Entity(
    tableName = "account_playback_progress",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class AccountPlaybackProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String, // "movie" or "episode"
    val positionSeconds: Long,
    val durationSeconds: Long,
    val updatedAtEpochSeconds: Long,
)

/**
 * Favorite items, scoped by account.
 */
@Entity(
    tableName = "favorites",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String,
)

/**
 * Watch-later items, scoped by account.
 */
@Entity(
    tableName = "watch_later",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class WatchLaterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String,
)

/**
 * Watched state (fully watched or watched to a point), scoped by account.
 */
@Entity(
    tableName = "watched_state",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class WatchedStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String,
    val isWatched: Boolean,
    val watchedAtEpochSeconds: Long?,
)

/**
 * Subscriptions/server mirrors, scoped by account.
 */
@Entity(
    tableName = "subscriptions",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String,
    val subscribedAtEpochSeconds: Long,
)

/**
 * Local-only library records, scoped by account.
 */
@Entity(
    tableName = "local_library_records",
    indices = [Index(value = ["profileKey", "contentId"], unique = true)],
)
data class LocalLibraryRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileKey: String,
    val contentId: String,
    val contentType: String,
    val addedAtEpochSeconds: Long,
)

// ── Shared Offline Entities (no profile key) ───────────────────────────────

/**
 * Downloaded media record for shared offline access.
 * No account profile key — visible across all accounts and logged-out state.
 */
@Entity(
    tableName = "downloaded_media",
    indices = [Index(value = ["contentId", "mediaType"], unique = true)],
)
data class DownloadedMediaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val mediaType: String, // "movie" or "episode"
    val localFilePath: String, // opaque app-private path
    val sizeBytes: Long,
    val status: String, // "downloading", "completed", "failed", "paused"
    val selectedQuality: String?,
    val downloadedAtEpochSeconds: Long?,
)

/**
 * Subtitle sidecar record for downloaded media.
 */
@Entity(
    tableName = "downloaded_subtitles",
    indices = [Index(value = ["downloadId", "language"], unique = true)],
)
data class DownloadedSubtitleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: Long, // FK to downloaded_media
    val language: String,
    val source: String?,
    val format: String?,
    val localFilePath: String, // opaque app-private path
)

/**
 * Shared offline display metadata — persists indefinitely while downloaded
 * media exists. Contains last known metadata for offline browsing.
 */
@Entity(
    tableName = "offline_display_metadata",
    indices = [Index(value = ["contentId"], unique = true)],
)
data class OfflineDisplayMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val title: String,
    val posterCacheKey: String?,
    val backdropCacheKey: String?,
    val episodeTitle: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val effectiveQuality: String?,
    val subtitleLanguages: String?, // JSON-encoded list
    val durationSeconds: Long?,
    val localProgressSeconds: Long?,
)

/**
 * Shared offline playback progress — visible across accounts.
 */
@Entity(
    tableName = "offline_playback_progress",
    indices = [Index(value = ["contentId"], unique = true)],
)
data class OfflinePlaybackProgressEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val positionSeconds: Long,
    val durationSeconds: Long,
    val updatedAtEpochSeconds: Long,
)
