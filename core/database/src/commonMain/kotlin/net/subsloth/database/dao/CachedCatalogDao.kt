package net.subsloth.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow
import net.subsloth.database.entity.CachedCatalogCountryEntity
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity

@Dao
interface CachedCatalogDao {
    @Query("SELECT * FROM cached_catalog WHERE contentType = :contentType ORDER BY title ASC")
    fun getAllByType(contentType: String): Flow<List<CachedCatalogItemEntity>>

    @Query("SELECT * FROM cached_catalog_genre")
    fun getAllGenres(): Flow<List<CachedCatalogGenreEntity>>

    @Query("SELECT * FROM cached_catalog_country")
    fun getAllCountries(): Flow<List<CachedCatalogCountryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedCatalogItemEntity>)

    @Query("DELETE FROM cached_catalog")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cached_catalog")
    suspend fun count(): Int

    @Query("DELETE FROM cached_catalog_genre")
    suspend fun deleteAllGenres()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllGenres(items: List<CachedCatalogGenreEntity>)

    @Query("DELETE FROM cached_catalog_country")
    suspend fun deleteAllCountries()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllCountries(items: List<CachedCatalogCountryEntity>)

    /** Atomic replace: delete all + insert new. Wraps in a Room @Transaction. */
    @Transaction
    suspend fun replaceAll(items: List<net.subsloth.database.entity.CachedCatalogItemWithMetadata>) {
        deleteAll()
        deleteAllGenres()
        deleteAllCountries()
        upsertAll(items.map { it.item })
        upsertAllGenres(
            items.flatMap { item ->
                item.genres.map { genre -> genre.copy(contentId = item.item.contentId) }
            },
        )
        upsertAllCountries(
            items.flatMap { item ->
                item.countries.map { country -> country.copy(contentId = item.item.contentId) }
            },
        )
    }
}
