# FC/IS Data Layer

Data access conventions — Room database, Retrofit 3.0.0, DataStore preferences, DTO-to-domain mapper boundary, offline-first caching, account-scoped isolation. The data layer IS the Imperative Shell.

## Module Structure

| Module | Role |
|---|---|
| `:core:model` | Domain types, value classes, sealed ADTs (no deps) |
| `:core:domain` | Port interfaces, policy objects (depends on `:core:model`) |
| `:core:database` | Room entities, DAOs, `SubSlothDatabase` |
| `:core:network` | Retrofit API interfaces, DTOs, mapper functions |
| `:core:preferences` | DataStore preferences, account profile store, credential store |

Dependencies flow inward. No data layer module leaks Android framework into domain code.

## Room Database

`SubSlothDatabase` at `core/database/src/main/kotlin/net/subsloth/database/`. Entity pattern: `data class` with `@PrimaryKey(autoGenerate = true) val id: Long = 0`. Account-scoped entities have composite unique index on `(profileKey, contentId)`. All 11 entities in a single file.

DAO pattern: `interface` with `@Dao`. Methods return `Flow<List<T>>` for reactive reads. Write methods are `suspend`. Upsert uses `@Insert(onConflict = OnConflictStrategy.REPLACE)`.

## Retrofit 3.0.0

`Api` interface at `core/network/src/main/kotlin/net/subsloth/core/network/media/api/Api.kt`. All methods are `suspend` returning DTO types (no `Call<T>` or `Response<T>`). DTOs use `@Serializable` + `@SerialName` for snake_case mapping. Singleton Retrofit instance with kotlinx.serialization converter.

## DataStore Preferences

`UserPreferences` at `core/preferences/src/main/kotlin/net/subsloth/preferences/UserPreferences.kt`. Reads return `Flow<T>`, writes use `dataStore.edit {}`. Keys namespaced by `AccountProfileKey`. `clearProfilePreferences()` removes all keys for a given profile.

`AccountProfileStore` derives non-reversible HMAC-SHA256 hash from login. `CredentialStore` encrypts with AES/GCM/NoPadding backed by Android Keystore.

## DTO-to-Domain Mapper

`Mapper` at `core/network/src/main/kotlin/net/subsloth/core/network/media/mapper/Mapper.kt`. Pure `object`, stateless, deterministic. Accepts DTOs, returns `Result<T>` carrying domain types or `DomainError`. `DomainResultException` wraps domain errors as `Throwable` for `Result` boundaries.

## Port/Adapter Repositories

Eight port interfaces in `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`:

| Port | Key methods |
|---|---|
| `CatalogPort` | `listCatalog()`, `getDetails(id)` |
| `LibraryPort` | `listLibrary()`, `addToLibrary(item)`, `removeFromLibrary(mediaId)` |
| `DownloadsPort` | `listDownloads()`, `enqueue(mediaId)`, `cancel(localId)`, `remove(localId)` |
| `PlaybackPort` | `prepareSource(mediaId)`, `play(source, pos)`, `pause()`, `seek(pos)` |
| `StoragePort` | `availableBytes()`, `reserveBytes()` |
| `ConnectivityPort` | `isOnline()`, `isMetered()` |
| `ClockPort` | `currentEpochSeconds()` |
| `CredentialsPort` | `save(login, password)`, `read()`, `clear()` |

All fallible methods return `Result<T>`. Adapters catch network/database errors and wrap in `DomainError`.

## Offline-First Pattern

1. Domain calls port method → adapter returns cached data from Room as `Flow<List<T>>`
2. Adapter triggers network refresh in parallel
3. On success, maps response to domain types, upserts to Room
4. Room `Flow` auto-emits updated data; UI reacts

Cache TTL in `UserPreferences` (`catalogCacheTimestamp`, `detailCacheTimestamp`).

## Account-Scoped Data Isolation

Room entities carry `profileKey` column. DAOs filter by `profileKey`. DataStore keys include profile prefix. Offline download entities are shared across accounts (no `profileKey`, content-based unique index).

## Error Boundary

`DomainError` sealed hierarchy at `core/model/src/main/kotlin/net/subsloth/core/model/error/DomainError.kt`: `NetworkError`, `DecodeError`, `MediaError`, `AuthError`, `DownloadError`, `LibraryError`, `QualityError`, `PaymentLimitError`. Caught at shell layer, wrapped in `DomainError`, propagated via `Result<T>`.

## References

- `docs/codestyle.md`: FC/IS rules, sealed types, pure functions, error handling
- `docs/agent/fc-is-architecture.md`: architecture overview, port/adapter, module deps
- Core database/network/preferences modules for entity/DAO/API patterns
- `core/domain/src/main/kotlin/net/subsloth/core/domain/port/`: port interfaces
