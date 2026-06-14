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
    indices = [Index(value = ["contentType", "contentId"], unique = true)],
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
 *
 * Uses [contentId] as the join key (the business key from [CachedCatalogItemEntity])
 * rather than the auto-generated [CachedCatalogItemEntity.id] to avoid the
 * auto-gen-ID-propagation problem during atomic [replaceAll] transactions.
 * Referential integrity is enforced by the DAO's @Transaction atomicity.
 */
@Entity(
    tableName = "cached_catalog_genre",
    indices = [Index(value = ["contentId", "genre"], unique = true)],
)
data class CachedCatalogGenreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
    val genre: String,
)

/**
 * Country join table for cached catalog items.
 *
 * See [CachedCatalogGenreEntity] for the rationale for using [contentId]
 * as the join key instead of the auto-generated ID.
 */
@Entity(
    tableName = "cached_catalog_country",
    indices = [Index(value = ["contentId", "country"], unique = true)],
)
data class CachedCatalogCountryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val contentId: String,
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
