package net.subsloth.database.entity

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

// ── Catalog Cache Entities (global, account-agnostic) ───────────────────

/**
 * Cached catalog item for a movie or show.
 * Scoped globally (not per-account) — the same catalog exists for all users.
 */
@Entity(
    tableName = "cached_catalog",
    indices = [Index(value = ["contentId"], unique = true)],
)
data class CachedCatalogItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val contentType: String, // "movie" or "show"
    val title: String,
    val plot: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Double?,
    val durationMinutes: Int?,
    val slug: String?,
    val imdbId: String?,
    val tmdbId: Int?,
    val status: String?, // Show only: "ongoing", "ended", "upcoming"
    val updatedAtEpochSeconds: Long?, // Movie: updated_at; Show: null
    val newestVideoEpochSeconds: Long?, // Show only: newest_video
)

/**
 * Genre join table for cached catalog items.
 */
@Entity(
    tableName = "cached_catalog_genre",
    indices = [Index(value = ["catalogItemId", "genre"], unique = true)],
)
data class CachedCatalogGenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogItemId: Long,
    val genre: String,
)

/**
 * Country join table for cached catalog items.
 */
@Entity(
    tableName = "cached_catalog_country",
    indices = [Index(value = ["catalogItemId", "country"], unique = true)],
)
data class CachedCatalogCountryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val catalogItemId: Long,
    val country: String,
)

/**
 * Intermediate representation combining a catalog item with its genres and countries.
 * Not a Room entity — assembled from separate queries in the DAO.
 */
data class CachedCatalogItemWithMetadata(
    val item: CachedCatalogItemEntity,
    val genres: List<CachedCatalogGenreEntity>,
    val countries: List<CachedCatalogCountryEntity>,
)
