package net.subsloth.core.network.media

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import net.subsloth.core.domain.policy.CatalogSyncPolicy
import net.subsloth.core.domain.port.CachedCatalogItem
import net.subsloth.core.domain.port.CatalogCachePort
import net.subsloth.core.domain.port.CatalogSyncPort
import net.subsloth.core.domain.port.ClockPort
import net.subsloth.core.model.Availability
import net.subsloth.core.model.error.Outcome
import net.subsloth.core.model.error.SyncError
import net.subsloth.core.model.identifier.ExternalId
import net.subsloth.core.model.identifier.ExternalIdSource
import net.subsloth.core.model.identifier.MovieId
import net.subsloth.core.model.identifier.ShowId
import net.subsloth.core.model.media.Media
import net.subsloth.core.model.media.MovieSummary
import net.subsloth.core.model.media.ShowStatus
import net.subsloth.core.model.media.ShowSummary
import net.subsloth.core.network.error.NetworkErrorClassifier
import net.subsloth.core.network.media.api.Api
import net.subsloth.core.network.media.mapper.Mapper
import net.subsloth.database.dao.CachedCatalogDao
import net.subsloth.database.entity.CachedCatalogCountryEntity
import net.subsloth.database.entity.CachedCatalogGenreEntity
import net.subsloth.database.entity.CachedCatalogItemEntity
import net.subsloth.database.entity.CachedCatalogItemWithMetadata
import net.subsloth.preferences.UserPreferences
import kotlin.time.Instant

/**
 * Implements [CatalogCachePort] and [CatalogSyncPort] for catalog synchronization.
 *
 * Coordinates API fetching, Room database caching, and DataStore timestamp tracking.
 * The catalog is account-agnostic — the same data exists for all users.
 */
class CatalogRepository(
    private val api: Api,
    private val catalogDao: CachedCatalogDao,
    private val userPreferences: UserPreferences,
    private val clock: ClockPort,
) : CatalogCachePort,
    CatalogSyncPort {

    private val log = Logger.withTag("CatalogRepository")

    // ── CatalogCachePort ─────────────────────────────────────────────────

    override fun catalogItems(contentType: String): Flow<List<Media>> = combine(
        catalogDao.getAllByType(contentType),
        catalogDao.getAllGenres(),
        catalogDao.getAllCountries(),
    ) {
            items: List<CachedCatalogItemEntity>,
            genres: List<CachedCatalogGenreEntity>,
            countries: List<CachedCatalogCountryEntity>,
        ->
        val genresByItem = genres.groupBy { it.contentId }
        val countriesByItem = countries.groupBy { it.contentId }
        items.map { item ->
            CachedCatalogItemWithMetadata(
                item = item,
                genres = genresByItem[item.contentId].orEmpty(),
                countries = countriesByItem[item.contentId].orEmpty(),
            ).toDomainMedia()
        }
    }

    override suspend fun replaceCatalog(items: List<CachedCatalogItem>) {
        val entities = items.map { it.toEntity() }
        catalogDao.replaceAll(entities)
    }

    // ── CatalogSyncPort ──────────────────────────────────────────────────

    override suspend fun sync(): Outcome<Unit> = try {
        log.d { "Starting catalog sync..." }
        val allMovies = paginate { page -> api.listMovies(page = page, perPage = 100).movies }
        val allShows = paginate { page -> api.listShows(page = page, perPage = 100).shows }

        val movieItems = allMovies.mapNotNull { Mapper.mapMovieSummary(it) }
        val showItems = allShows.mapNotNull { Mapper.mapShowSummary(it) }

        val cacheItems = movieItems.map { it.toCacheItem() } + showItems.map { it.toCacheItem() }
        catalogDao.replaceAll(cacheItems.map { it.toEntity() })

        userPreferences.setGlobalCatalogCacheTimestamp(clock.now().epochSeconds * 1000)
        log.d { "Catalog sync complete: ${movieItems.size} movies, ${showItems.size} shows" }
        Outcome.Success(Unit)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.e(e) { "Catalog sync failed" }
        val error = when (e) {
            is SyncError -> e
            else -> mapExceptionToSyncError(e)
        }
        Outcome.Failure(error)
    }

    override suspend fun isStale(): Boolean {
        val timestamp = userPreferences.globalCatalogCacheTimestamp().first()
        return CatalogSyncPolicy.isStale(timestamp, clock.now().epochSeconds * 1000)
    }

    // ── Pagination ───────────────────────────────────────────────────────

    private suspend fun <T> paginate(fetch: suspend (Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var page = 1
        do {
            val batch = fetch(page)
            result.addAll(batch)
            page++
        } while (batch.isNotEmpty())
        return result
    }

    // ── Mapping: Domain → Cache ──────────────────────────────────────────

    private fun MovieSummary.toCacheItem(): CachedCatalogItem = CachedCatalogItem(
        contentId = id.value.value.toString(),
        contentType = "movie",
        title = title,
        plot = plot,
        posterUrl = null,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        durationMinutes = durationMinutes,
        slug = slug,
        imdbId = imdbId?.value,
        tmdbId = null,
        status = null,
        updatedAtEpochSeconds = updatedAtEpochSeconds?.epochSeconds,
        newestVideoEpochSeconds = null,
        genres = genres.toList(),
        countries = emptyList(),
    )

    private fun ShowSummary.toCacheItem(): CachedCatalogItem = CachedCatalogItem(
        contentId = id.value.value.toString(),
        contentType = "show",
        title = title,
        plot = plot,
        posterUrl = null,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        durationMinutes = durationMinutes,
        slug = slug,
        imdbId = imdbId?.value,
        tmdbId = null,
        status = status.name.lowercase(),
        updatedAtEpochSeconds = null,
        newestVideoEpochSeconds = newestVideoEpochSeconds?.epochSeconds,
        genres = genres.toList(),
        countries = countries.toList(),
    )

    // ── Mapping: Cache → Domain ──────────────────────────────────────────

    private fun CachedCatalogItemWithMetadata.toDomainMedia(): Media {
        val genreStrings = genres.map { it.genre }.toImmutableList()
        val countryStrings = countries.map { it.country }.toImmutableList()
        val entity = item
        return when (entity.contentType) {
            "movie" -> {
                val updatedAt = entity.updatedAtEpochSeconds
                MovieSummary(
                    id = Media.MediaId.Movie(MovieId(entity.contentId.toIntOrNull() ?: 0)),
                    title = entity.title,
                    plot = entity.plot,
                    availability = if (updatedAt != null && updatedAt > 0) {
                        Availability.Available
                    } else {
                        Availability.Expired
                    },
                    rating = entity.rating,
                    year = entity.year,
                    genres = genreStrings,
                    durationMinutes = entity.durationMinutes,
                    slug = entity.slug,
                    imdbId = entity.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
                    backdropUrl = entity.backdropUrl,
                    updatedAtEpochSeconds = updatedAt?.let { Instant.fromEpochSeconds(it) },
                )
            }

            "show" -> {
                val newestVideo = entity.newestVideoEpochSeconds
                ShowSummary(
                    id = Media.MediaId.Show(ShowId(entity.contentId.toIntOrNull() ?: 0)),
                    title = entity.title,
                    plot = entity.plot,
                    availability = if (newestVideo != null && newestVideo > 0) {
                        Availability.Available
                    } else {
                        Availability.Expired
                    },
                    rating = entity.rating,
                    year = entity.year,
                    genres = genreStrings,
                    durationMinutes = entity.durationMinutes,
                    slug = entity.slug,
                    imdbId = entity.imdbId?.let { ExternalId(it, ExternalIdSource.IMDb) },
                    backdropUrl = entity.backdropUrl,
                    status = parseShowStatus(entity.status),
                    countries = countryStrings,
                    newestVideoEpochSeconds = newestVideo?.let { Instant.fromEpochSeconds(it) },
                )
            }

            else -> error("Unknown content type: ${entity.contentType}")
        }
    }

    // ── Mapping: Cache Item → Entity ─────────────────────────────────────

    private fun CachedCatalogItem.toEntity(): CachedCatalogItemWithMetadata {
        val entity = CachedCatalogItemEntity(
            contentId = contentId,
            contentType = contentType,
            title = title,
            plot = plot,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            year = year,
            rating = rating,
            durationMinutes = durationMinutes,
            slug = slug,
            imdbId = imdbId,
            tmdbId = tmdbId,
            status = status,
            updatedAtEpochSeconds = updatedAtEpochSeconds,
            newestVideoEpochSeconds = newestVideoEpochSeconds,
        )
        val genreEntities = genres.map { genre ->
            CachedCatalogGenreEntity(contentId = contentId, genre = genre)
        }
        val countryEntities = countries.map { country ->
            CachedCatalogCountryEntity(contentId = contentId, country = country)
        }
        return CachedCatalogItemWithMetadata(
            item = entity,
            genres = genreEntities,
            countries = countryEntities,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun parseShowStatus(status: String?): ShowStatus = when {
        status.equals("ongoing", ignoreCase = true) -> ShowStatus.ONGOING
        status.equals("ended", ignoreCase = true) -> ShowStatus.ENDED
        status.equals("upcoming", ignoreCase = true) -> ShowStatus.UPCOMING
        else -> ShowStatus.UNKNOWN
    }

    private fun mapExceptionToSyncError(e: Exception): SyncError = NetworkErrorClassifier.classifyToSync(e)
}
