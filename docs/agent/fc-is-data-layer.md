# FC/IS Data Layer: Agent Instructions

This document describes the data layer conventions for the `subsloth` Android project. It covers Room database patterns, Retrofit 3.0.0 API calls, DataStore preferences, the DTO-to-domain mapper boundary, offline-first caching, and account-scoped data isolation.

## Overview

In FC/IS, the data layer **is** the Imperative Shell. It handles all I/O (network, disk, preferences) and translates between external formats (DTOs) and domain models. Pure domain code in `:core:domain` defines port interfaces. The data layer in `:core:network`, `:core:database`, and `:core:preferences` implements them. See `docs/agent/fc-is-architecture.md` for port/adapter conventions.

## Module Structure

| Module | Role |
|---|---|
| `:core:model` | Domain types, value classes, sealed ADTs (no dependencies) |
| `:core:domain` | Port interfaces, policy objects (depends on `:core:model`) |
| `:core:database` | Room entities, DAOs, `SubSlothDatabase` |
| `:core:network` | Retrofit API interfaces, DTOs, mapper functions |
| `:core:preferences` | DataStore-backed preferences, account profile store, credential store |

Dependencies flow inward. `:core:network` and `:core:database` depend on `:core:domain` and `:core:model`. `:core:preferences` depends on `:core:model`. No data layer module depends on the Android framework in a way that leaks into domain code.

## Room Database

`SubSlothDatabase` at `core/database/src/main/kotlin/net/subsloth/database/SubSlothDatabase.kt` uses `@Database` with 11 entities and KSP annotation processing. The class is `abstract class SubSlothDatabase : RoomDatabase()` with abstract DAO accessor functions.

Entity pattern: `data class` with `@PrimaryKey(autoGenerate = true) val id: Long = 0`. Account-scoped entities have a composite unique index on `(profileKey, contentId)`. Shared offline entities (no account scope) use a unique index on `(contentId)` or `(contentId, mediaType)`.

Entities live in `core/database/src/main/kotlin/net/subsloth/database/entity/LibraryEntities.kt`. All 11 entities are in a single file.

DAO pattern: `interface` annotated with `@Dao`. Methods return `Flow<List<T>>` for reactive reads. Write methods are `suspend` functions. Upsert uses `@Insert(onConflict = OnConflictStrategy.REPLACE)`. Delete methods use `@Delete` or `@Query("DELETE FROM ...")`.

Example from `CachedOnlineMetadataDao`:
```
@Dao
interface CachedOnlineMetadataDao {
    @Query("SELECT * FROM cached_online_metadata WHERE profileKey = :profileKey AND contentType = :contentType")
    fun getByProfileAndType(profileKey: String, contentType: String): Flow<List<CachedOnlineMetadataEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedOnlineMetadataEntity)

    @Query("DELETE FROM cached_online_metadata WHERE profileKey = :profileKey")
    suspend fun deleteAllForProfile(profileKey: String)
}
```

DAOs are organized by entity in `core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt`. Account-scoped DAOs filter by `profileKey`. Shared offline DAOs do not.

## Retrofit 3.0.0

The Retrofit API interface is `Api` at `core/network/src/main/kotlin/net/subsloth/core/network/media/api/Api.kt`. All API methods are `suspend` functions returning DTO types directly (no `Call<T>` or `Response<T>` wrappers).

```
interface Api {
    @GET("movies")
    suspend fun listMovies(...): MovieListResponse

    @GET("movies/{id}")
    suspend fun getMovie(@Path("id") id: Int): Movie
}
```

DTOs use `kotlinx.serialization` (`@Serializable`, `@SerialName`) and live in `core/network/src/main/kotlin/net/subsloth/core/network/media/api/model/`. Three model files: `Common.kt` (shared types like `VideoQuality`, `SubtitleTrack`, `PaginationMeta`), `Movie.kt` (movie DTOs), and `Show.kt` (show DTOs).

The Retrofit instance is created as a singleton with a configured base URL and OkHttp client. The converter factory is `kotlinx.serialization`.

## DataStore Preferences

`UserPreferences` at `core/preferences/src/main/kotlin/net/subsloth/preferences/UserPreferences.kt` wraps `DataStore<Preferences>`. Reads return `Flow<T>` via `dataStore.data.map {}`. Writes use `dataStore.edit {}`.

Each preference key is namespaced by `AccountProfileKey`:
```
private fun subtitleEnabledKey(profileKey: AccountProfileKey) =
    booleanPreferencesKey("${profileKey.value}_subtitle_enabled")
```

A `clearProfilePreferences()` method removes all keys for a given profile during logout cleanup.

`AccountProfileStore` derives a non-reversible hash from the user login using HMAC-SHA256 with an app-local salt. The raw login is never stored. `CredentialStore` encrypts credentials with AES/GCM/NoPadding backed by Android Keystore.

## DTO-to-Domain Mapper

`Mapper` at `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/Mapper.kt` is a pure `object` with all mapping functions. This is the FC/IS boundary. Pure domain types live in `:core:model`. External DTO formats live in `:core:network`. The mapper translates between them.

```
fun mapMovieDetails(dto: DtoMovie): Result<MovieDetails> {
    val title = dto.title ?: dto.name
        ?: return Result.failure(DomainResultException(DecodeError.MissingFields(listOf("title"))))
    return Result.success(MovieDetails(...))
}
```

Mapper functions are stateless and deterministic. They accept DTOs and return either domain models or `Result.failure(DomainResultException(...))` carrying a typed `DomainError`. `MappingResult` captures successfully mapped items plus a skip count for batch operations.

`DomainResultException` at `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/DomainResultException.kt` wraps a `DomainError` as a `Throwable` so it can cross `Result` boundaries.

## Repository as Imperative Shell

The repository pattern here is port/adapter. Domain code depends on port interfaces, never on Room DAOs, Retrofit calls, or DataStore instances directly.

Eight port interfaces in `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`:

| Port | Key methods |
|---|---|
| `CatalogPort` | `listCatalog(): Result<List<Media>>`, `getDetails(id): Result<MediaDetails>` |
| `LibraryPort` | `listLibrary()`, `addToLibrary(item)`, `removeFromLibrary(mediaId)` |
| `DownloadsPort` | `listDownloads()`, `enqueue(mediaId)`, `cancel(localId)`, `remove(localId)` |
| `PlaybackPort` | `prepareSource(mediaId)`, `play(source, position)`, `pause()`, `seek(position)` |
| `StoragePort` | `availableBytes(): Long`, `reserveBytes(): Long` |
| `ConnectivityPort` | `isOnline(): Boolean`, `isMetered(): Boolean` |
| `ClockPort` | `currentEpochSeconds(): Long` |
| `CredentialsPort` | `save(login, password)`, `read()`, `clear()` |

All port methods return `Result<T>` for fallible operations. Adapter implementations catch network and database errors at the shell layer and wrap them in `DomainError` subtypes.

## Offline-First Pattern

The canonical data source is Room, exposed via `Flow<T>`. The refresh flow is:

1. Domain code calls the port method (e.g. `CatalogPort.listCatalog()`).
2. The adapter returns cached data from Room as `Flow<List<Entity>>`.
3. In parallel, the adapter triggers a network refresh via Retrofit.
4. On success, the response is mapped to domain types and written to Room via upsert.
5. Room's `Flow` auto-emits the updated data. The UI observes the `Flow` and updates reactively.

Cache TTL uses timestamps stored in `UserPreferences` (`catalogCacheTimestamp`, `detailCacheTimestamp`) to decide when to refresh.

## Account-Scoped Data Isolation

Room entities that belong to a specific account carry a `profileKey` column. DAO queries always filter by `profileKey`:

```
@Query("SELECT * FROM favorites WHERE profileKey = :profileKey")
fun getAllForProfile(profileKey: String): Flow<List<FavoriteEntity>>
```

DataStore preference keys include the profile key prefix. `clearProfilePreferences()` removes all keys for a given profile during logout.

Offline download entities (`DownloadedMediaEntity`, `DownloadedSubtitleEntity`, `OfflineDisplayMetadataEntity`, `OfflinePlaybackProgressEntity`) are shared across accounts. They have no `profileKey` column and use a content-based unique index instead.

## Error Boundary

The `DomainError` sealed hierarchy at `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt` covers all recoverable failures:

| Error type | Purpose |
|---|---|
| `NetworkError` | Timeout, no connectivity, HTTP errors, rate limiting |
| `DecodeError` | Invalid response format, serialization failure, missing fields |
| `MediaError` | Unavailable, not found, geo-restricted, expired |
| `AuthError` | Invalid credentials, session expired, account suspended |
| `DownloadError` | Insufficient storage, missing subtitle, queue full |
| `LibraryError` | Not supported, already exists, not found |
| `QualityError` | Unsupported, no fallback, below minimum |
| `PaymentLimitError` | Concurrent stream limit, subscription required |

Errors are caught at the shell layer (Retrofit call site, Room operation) and wrapped in the matching `DomainError` subtype. `Result<T>` propagates typed errors upward. Pure domain code never catches exceptions from I/O operations.

## References

- `docs/codestyle.md`: definitive FC/IS rules, sealed types, pure functions, error handling.
- `docs/agent/fc-is-architecture.md`: architecture overview, port/adapter pattern, module dependencies.
- `core/database/src/main/kotlin/net/subsloth/database/`: Room entities, DAOs, database class.
- `core/network/src/main/kotlin/net/subsloth/core/network/media/api/`: Retrofit API interface and DTOs.
- `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/Mapper.kt`: DTO-to-domain mapper.
- `core/preferences/src/main/kotlin/net/subsloth/preferences/`: DataStore preferences, account profile store, credential store.
- `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`: port interfaces.
- `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`: sealed error hierarchy.
