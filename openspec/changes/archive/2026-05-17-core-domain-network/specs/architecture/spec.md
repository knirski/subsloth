## ADDED Requirements

### Requirement: Functional Core Boundary
The system SHALL keep domain models and pure decisions in Kotlin core modules with no Android framework, Compose, Room, DataStore, Retrofit, OkHttp, Media3, WorkManager, filesystem, or notification dependencies.

#### Scenario: Core imports are checked
- **WHEN** architecture tests inspect `:core:model` and `:core:domain`
- **THEN** Android shell and network implementation packages are absent from those modules

### Requirement: Strong Domain Types
The system SHALL model media, progress, library, download, quality, subtitle, availability, and expected error states with sealed ADTs, immutable values, and value classes.

#### Scenario: Upcoming episode is rendered
- **WHEN** an episode has future premiere metadata
- **THEN** the domain represents it as upcoming and prevents playable or downloadable intents

### Requirement: Typed Error Composition
Recoverable domain and application failures SHALL use Kotlin `Result<T>` with sealed typed error models.

#### Scenario: Expected failure is returned
- **WHEN** auth, payment/free-limit, decode, unavailable media, low storage, missing subtitle, or unsupported quality occurs
- **THEN** the caller receives a typed error result instead of relying on unchecked exceptions or string matching

### Requirement: Capability Ports
Use cases SHALL depend on focused `suspend` capability ports for effectful dependencies.

#### Scenario: Use case needs catalog data
- **WHEN** a domain use case reads catalog or detail data
- **THEN** it calls an abstract port that can be interpreted by tests or by the Android/network shell

### Requirement: Network Boundary Isolation
Generated or handwritten network DTOs SHALL remain inside `:core:network` and SHALL be manually mapped into stable domain models.

#### Scenario: DTO changes
- **WHEN** OpenAPI discovery changes a DTO field name or nullability
- **THEN** only network DTO and mapper tests require updates before domain and UI models stay stable

### Requirement: Kodi Request Identity
The network layer SHALL apply Kodi-compatible API host, prefix, Basic auth style, JSON headers, and Kodi-style `User-Agent` metadata for production Media requests.

#### Scenario: Production request is built
- **WHEN** an Media request leaves the network shell
- **THEN** it uses the documented Kodi-compatible identity and excludes browser/WebView, headless, automation, test, emulator-debug, OkHttp/Dalvik, and Android-browser identity

### Requirement: Network Cadence Policy
The network layer SHALL keep v1 traffic user-driven, low-concurrency, single-flight for identical in-flight requests, and bounded by retry budgets that respect `429` and `Retry-After`.

#### Scenario: Duplicate request is already in flight
- **WHEN** the same catalog/detail/library key is requested concurrently
- **THEN** the network layer coalesces the duplicate request instead of sending another Media call

#### Scenario: Non-retryable response occurs
- **WHEN** the server returns auth failure, payment/free-limit, malformed ID, decode failure, unexpected redirect, HTML, or non-JSON body
- **THEN** the network layer returns a typed recoverable state without retry loops

### Requirement: Raw URL Redaction
The system SHALL NOT persist or expose raw auth headers, stream URLs, download URLs, subtitle URLs, artwork URLs, cookies, or credentials in domain state, fixtures, logs, diagnostics, or local storage.

#### Scenario: Mapper receives media URLs
- **WHEN** a network DTO contains stream, download, subtitle, or artwork URLs
- **THEN** the mapper keeps them only in the active request/playback/download path and never stores them in persistent domain records

### Requirement: Server Library Mutation Gate
Server-side favorites, watch-later, watched, subscription, and progress mutations SHALL remain disabled unless Kodi plugin parity proves the exact endpoint, method, payload, headers, and triggering context.

#### Scenario: Kodi parity is absent
- **WHEN** the user toggles a library state before server mutation support is proven
- **THEN** the app records only the local account-scoped or shared offline state required by the current context
