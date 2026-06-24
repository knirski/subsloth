# Architecture Specification (delta)

## MODIFIED Requirements

### Requirement: Pure Time Abstraction
Domain code that needs the current time SHALL depend on the `CurrentTimePort` abstraction (formerly `ClockPort`). The port SHALL provide both `now(): Instant` for type-safe time arithmetic and `millisNow(): Long` for second-precision timestamps (cache ages, token expiries, retry-after). Implementations live in the platform shell; the Android impl uses `System.currentTimeMillis()` and `kotlin.time.Clock.System.now()`.

#### Scenario: ViewModel reads the current epoch milliseconds
- **WHEN** a ViewModel needs to know "how many milliseconds since the cache was last refreshed"
- **THEN** it calls `currentTimePort.millisNow()` and the result is a `Long` suitable for arithmetic against stored `Long` epoch values

#### Scenario: Test substitutes a fixed clock
- **WHEN** a unit test needs a deterministic time
- **THEN** the test injects a `CurrentTimePort` whose `now()` and `millisNow()` return the chosen values, and no platform time is consulted

## ADDED Requirements

### Requirement: In-Memory Mock API at the Testing Layer
The project SHALL provide a deterministic in-memory `MockApi` (in `:testing:mock-api`) that implements the same domain ports the production network shell does (`CatalogPort`, `CatalogSyncPort`, `CatalogCachePort`, `LibraryPort`, `DownloadsPort`, `CredentialsPort`). The mock is the target of the screenshot test suite and the dev/demo build flavours; it is not used in production.

#### Scenario: MockApi serves the seed catalog
- **WHEN** a port consumer calls `MockApi.listCatalog()`
- **THEN** it receives a non-empty `List<Media>` containing the 10 seed movies and 5 seed shows

#### Scenario: MockApi rejects requests after session expiry
- **WHEN** the test calls `mockApi.expireSession()`
- **THEN** the next call to any port returns `Result.failure(AuthError.SessionExpired)` or a mapped `NetworkError.HttpError(401, ...)` depending on the port's contract

#### Scenario: MockApi library mutations are observable
- **WHEN** the test calls `mockApi.addToLibrary(LibraryItem(...))` then `mockApi.listLibrary()`
- **THEN** the library list contains the added item in the correct collection
