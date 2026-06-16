# Architecture Specification

## Purpose
This specification defines the architectural boundaries and data integrity requirements for the core domain and network layers, ensuring a clean separation from platform-specific frameworks and external API implementation details.
## Requirements
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
Recoverable domain and application failures SHALL use a typed value (the project-local `Outcome<T>` sealed wrapper) for `Result`-like APIs. The `DomainError` hierarchy SHALL distinguish `Technical` failures (network, decode, sync) from `Business` failures (auth, payment, media, download, quality, library) so the UI can dispatch by category at the type level.

#### Scenario: Technical failure is identified at the type level
- **WHEN** a `NetworkError`, `DecodeError`, or `SyncError` is returned
- **THEN** it is a `DomainError.Technical` and the consumer can `when (e: DomainError.Technical)` exhaustively

#### Scenario: Business failure is identified at the type level
- **WHEN** an `AuthError`, `PaymentLimitError`, `MediaError`, `DownloadError`, `QualityError`, or `LibraryError` is returned
- **THEN** it is a `DomainError.Business` and the consumer can `when (e: DomainError.Business)` exhaustively

#### Scenario: Domain port returns Outcome at the boundary
- **WHEN** a `:core:domain` port signals a recoverable failure
- **THEN** the port return type is `Outcome<T>` and the failure variant carries a typed `DomainError` (not a `Throwable` wrapper)

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

### Requirement: ViewModel State Discipline
Session-scoped flags and per-playback counters SHALL be fields of the UI state data class, not standalone `var` properties on a ViewModel.

#### Scenario: Player session flag is observed
- **WHEN** a `PlayerViewModel` mutates its session-scoped `session` or `snapshotCountSinceSave`
- **THEN** the change is observable through the published `StateFlow<PlayerUiState>` and the ViewModel holds no standalone mutable state for these fields

#### Scenario: Counter increment is race-free
- **WHEN** `PlayerViewModel.onPlayerSnapshot` increments `snapshotCountSinceSave`
- **THEN** the increment is computed inside the `_uiState.update` lambda so the new value is based on the latest snapshot, not a stale pre-update read

#### Scenario: Sync is cancelled by a new call
- **WHEN** a user invokes `HomeViewModel.sync()` while a previous sync is in flight
- **THEN** the previous in-flight sync is cancelled automatically through `Channel<SyncRequest>(CONFLATED) + collectLatest` and no manual `Job.cancel()` is required

### Requirement: Threshold Constants
The domain layer SHALL expose named constants for completion and watch thresholds so ViewModels never inline numeric magic values.

#### Scenario: Watch threshold is reused
- **WHEN** a ViewModel decides whether a media item counts as "watched" for library or download completion
- **THEN** it compares against `CompletionPolicy.WATCHED_THRESHOLD` and not against a magic literal

### Requirement: Network Boundary Returns Typed DomainError
The network shell SHALL map engine exceptions (Ktor `ResponseException`, `HttpRequestTimeoutException`, `IOException` subclasses) into typed `NetworkError` variants at the I/O boundary. Engine exceptions SHALL NOT leak past the network shell into `:core:domain` or `:feature:*` consumers.

#### Scenario: Ktor ResponseException preserves HTTP status
- **WHEN** a Ktor `ResponseException` carries a 4xx or 5xx status
- **THEN** `NetworkErrorClassifier.classifyToNetwork` returns `NetworkError.HttpError(code, message)` so the typed classifier can dispatch 401 to `AuthFailure` and 404 to `NotFound`

#### Scenario: Non-Ktor exception falls through to UnexpectedResponse
- **WHEN** an engine-internal exception (DNS failure, socket reset) that is not a public Ktor type reaches the classifier
- **THEN** the classifier returns `NetworkError.NoConnectivity` (conservative connectivity-loss treatment) without string-matching the exception message

### Requirement: Pure Error Classifiers in Domain
The `:core:domain` module SHALL host pure classifiers that map typed `DomainError` values to other domain types. ViewModels SHALL call these classifiers rather than match on `Throwable.message` or exception text.

#### Scenario: PlaybackErrorClassifier maps HTTP status codes
- **WHEN** the player receives `NetworkError.HttpError(401)` from the network shell
- **THEN** `PlaybackErrorClassifier.classify` returns `PlaybackError.AuthFailure`

#### Scenario: PlaybackErrorClassifier maps 403 to StreamUrlExpired
- **WHEN** the player receives `NetworkError.HttpError(403)` from the network shell
- **THEN** `PlaybackErrorClassifier.classify` returns `PlaybackError.StreamUrlExpired`

#### Scenario: Unclassified errors are wrapped as Recoverable
- **WHEN** the player receives any other `DomainError`
- **THEN** `PlaybackErrorClassifier.classify` returns `PlaybackError.Recoverable(cause = error)` so the UI can display a generic message and surface the typed cause

### Requirement: Session Port
The project SHALL provide a `SessionPort` abstraction that exposes the current session state to the UI and lets the network shell signal login/logout/session-expiry events. The session is the single source of truth for "is the user logged in?"; the UI SHALL observe `state` and route to the login screen when the state transitions to `Anonymous`.

#### Scenario: Network shell opens a session
- **WHEN** a user successfully submits credentials to the network shell
- **THEN** the shell calls `sessionPort.open(credentials)` and the `SessionPort.state` StateFlow emits `Session.Authenticated(userId, openedAtEpochSeconds, credentials)`

#### Scenario: Network shell invalidates the session on 401
- **WHEN** the network shell receives a 401 from the upstream API
- **THEN** the shell calls `sessionPort.invalidate()` and the `SessionPort.state` StateFlow emits `Session.Anonymous`. The UI SHALL route to the login screen.

#### Scenario: UI observes the state
- **WHEN** the UI is in any authenticated-only screen
- **THEN** it collects `sessionPort.state` and re-routes to the login screen on every transition to `Session.Anonymous`

### Requirement: Session Gate as Navigation Root
The root composable of every app (`androidApp`, `desktopApp`, `webApp`) SHALL wrap its content in a `SessionGate` composable that observes `SessionPort.state`. When the state is `Session.Anonymous`, the gate SHALL render the login screen. When the state is `Session.Authenticated`, the gate SHALL render the gated content. The gate is the only entry point into the app; the user cannot reach a screen that requires authentication without first being authenticated.

#### Scenario: User opens the app for the first time
- **WHEN** the app starts and `SessionPort.state.value` is `Session.Anonymous`
- **THEN** the gate renders the login screen and no other screen is reachable

#### Scenario: User successfully logs in
- **WHEN** the user submits valid credentials and `SessionPort.open(credentials)` returns `Outcome.Success(Unit)`
- **THEN** `SessionPort.state` emits `Session.Authenticated` and the gate switches to render the gated content

#### Scenario: Network shell invalidates the session
- **WHEN** the network shell calls `SessionPort.invalidate()` (e.g. on a 401)
- **THEN** `SessionPort.state` emits `Session.Anonymous` and the gate switches to render the login screen

#### Scenario: User logs out
- **WHEN** the user triggers logout and `SessionPort.close()` is called
- **THEN** `SessionPort.state` emits `Session.Anonymous` and the gate switches to render the login screen

