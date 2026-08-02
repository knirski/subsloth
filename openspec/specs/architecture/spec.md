# Architecture Specification

## Purpose
This specification defines the architectural boundaries and data integrity requirements for the core domain and network layers, ensuring a clean separation from platform-specific frameworks and external API implementation details.
## Requirements
### Requirement: Functional Core Boundary
The system SHALL keep domain models and pure decisions in Kotlin core modules with no Android framework, Room, DataStore, Retrofit, OkHttp, Media3, WorkManager, filesystem, or notification dependencies. `:core:model` and `:core:domain` SHALL NOT depend on a Compose runtime artifact; any Compose stability annotation needed by consuming UI modules SHALL be supplied through a Compose stability configuration file consumed by those modules, not through a compile-time dependency in `:core:model` or `:core:domain`.

#### Scenario: Core imports are checked
- **WHEN** architecture tests inspect `:core:model` and `:core:domain`
- **THEN** Android shell, network implementation, and Compose runtime packages are absent from those modules across every source set (`commonMain`, `androidMain`, `jvmMain`, `wasmJsMain`), not only `commonMain` — verified by `CoreModelArchitectureTest`'s source-import scan, a separate mechanism from the Executable Dependency Graph Invariants requirement below

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

### Requirement: Pure Time Abstraction
Domain code that needs the current time SHALL depend on the `CurrentTimePort` abstraction (formerly `ClockPort`). The port SHALL provide both `now(): Instant` for type-safe time arithmetic and `millisNow(): Long` for millisecond-precision epoch timestamps (cache ages, token expiries, retry-after). Implementations live in the platform shell; the Android impl uses `System.currentTimeMillis()` and `kotlin.time.Clock.System.now()`.

#### Scenario: ViewModel reads the current epoch milliseconds
- **WHEN** a ViewModel needs to know "how many milliseconds since the cache was last refreshed"
- **THEN** it calls `currentTimePort.millisNow()` and the result is a `Long` suitable for arithmetic against stored `Long` epoch-millisecond values

#### Scenario: Test substitutes a fixed clock
- **WHEN** a unit test needs a deterministic time
- **THEN** the test injects a `CurrentTimePort` whose `now()` and `millisNow()` return the chosen values, and no platform time is consulted

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
The project SHALL provide a `SessionPort` abstraction that exposes the current session state to the UI and lets the network shell signal login/logout/session-expiry events. The session is the single source of truth for "is the user logged in?"; the UI SHALL observe `state` and route to the login screen when the state transitions to `Anonymous`. `open`, `close`, and `invalidate` SHALL be `suspend` functions so an adapter can await a network validation call or encrypted-storage access before returning.

#### Scenario: Network shell opens a session
- **WHEN** a user successfully submits credentials to the network shell
- **THEN** the shell calls `sessionPort.open(credentials)` and the `SessionPort.state` StateFlow emits `Session.Authenticated(userId, openedAtEpochSeconds, credentials)`

#### Scenario: Network shell invalidates the session on 401
- **WHEN** the network shell receives a 401 from the upstream API
- **THEN** the shell calls `sessionPort.invalidate()` and the `SessionPort.state` StateFlow emits `Session.Anonymous`. The UI SHALL route to the login screen.

#### Scenario: UI observes the state
- **WHEN** the UI is in any authenticated-only screen
- **THEN** it collects `sessionPort.state` and re-routes to the login screen on every transition to `Session.Anonymous`

#### Scenario: Adapter awaits validation before returning
- **WHEN** a production `SessionPort` implementation validates credentials against a real network call
- **THEN** `open` suspends until the call completes and only then returns `Outcome.Success` or `Outcome.Failure`, never optimistically returning before validation finishes

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

### Requirement: prefer `enum class` for data-only variant sets
The system MUST express variant sets with no per-variant state (e.g. `Queued`, `Applied`, `WifiOnly`) as `enum class`, not as `sealed interface` with `data object` branches. Rationale: `enum class` exposes `entries` and `values()` for iteration and exhaustiveness checks; the compiler enforces the variant set; it removes the detekt-flagged `'is' over enum entry` antipattern; and it is uniform with `QualityDescriptor` and other existing enums in the project.

#### Scenario: data-only sealed type is expressed as `enum class`
- **WHEN** a variant set with no per-variant state is added to `:core:model` or `:core:domain`
- **THEN** it SHALL be declared as `enum class`
- **AND** SHALL use `Foo.Bar` reference syntax at call sites
- **AND** exhaustive `when` SHALL match without `is` keywords

### Requirement: shared navigation types live in `:core:ui`
The system MUST place typed `NavKey` subtypes and any common navigation
contracts in `:core:ui` so they are not duplicated per app. The
per-app `SavedStateConfiguration` builders (the polymorphic
serializer registration) may remain per app because they are wired
at process start, but the key types themselves MUST be defined once.

#### Scenario: AppNavKey is sourced from :core:ui
- **WHEN** a navigation route is added or removed
- **THEN** the change happens in a single file under `:core:ui`
- **AND** the per-app NavKeys.kt files do not exist

### Requirement: ViewModel session flags live in the UI state
The system MUST model transient session flags (e.g. `isSyncing`,
`isLoading`, `isRefreshing`) as fields of the UI state data class
and MUST NOT expose them as separate `StateFlow` properties on the
ViewModel. The single `StateFlow<UiState>` is the source of truth;
consumers derive boolean flags from the state class.

#### Scenario: isSyncing is read from the UI state
- **WHEN** a screen needs to show a sync progress indicator
- **THEN** it reads `uiState.isSyncing` (or `uiState.content.isSyncing` for sealed types)
- **AND** does not subscribe to a separate `viewModel.isSyncing` flow

#### Scenario: one-shot error events do not replay
- **WHEN** a ViewModel exposes a SharedFlow of one-shot error events
- **THEN** it MUST use `extraBufferCapacity >= 1`, not `replay = 1`,
  so that a late subscriber does not see an event from before
  their subscription

### Requirement: forbid force-unwrap in production code
Production code SHALL NOT use the Kotlin force-unwrap operator
`!!`. Every nullable expression SHALL be handled at the type level
— by modelling the null case as a sealed variant, by lifting the
call into a typed `Outcome<T>` / `Result<T>` / sealed `DomainError`,
or by an explicit `requireNotNull(x) { "..." }` / `checkNotNull(x) { "..." }`
with a descriptive message.

Test source sets (any path matching `**/src/<X>Test/`) MAY use `!!`
to assert preconditions.

#### Scenario: detekt reports !! in production
- **WHEN** a `!!` postfix expression appears in `src/main/`,
  `src/commonMain/`, `src/jvmMain/`, `src/androidMain/`, or any
  other production source set
- **THEN** detekt reports it under the `subsloth.NoForceUnwrap` rule
- **AND** CI fails on the finding

#### Scenario: detekt does not report !! in tests
- **WHEN** a `!!` postfix expression appears in any `*Test`
  source set
- **THEN** detekt does not report it

### Requirement: in-flight state is a sealed variant, not a Boolean field
The system MUST model transient in-flight UI states (loading,
refreshing, syncing) as a distinct sealed variant of the
ViewModel's UI state, not as a `Boolean isLoading: Boolean = false`
field on the result data class. The previous pattern, which
exponentiates the state space (idle × loading × result = 8
combinations) is forbidden. Valid combinations are restricted
to the closed variant set of the sealed hierarchy.

#### Scenario: search is in flight
- **WHEN** a search has been triggered and the result list is not
  yet available
- **THEN** the UI state is `SearchUiState.Loading(query)`
- **AND** the spinner is shown

#### Scenario: search has results
- **WHEN** the search has completed and a result list is available
- **THEN** the UI state is `SearchUiState.Results(query, items)`
- **AND** the empty / populated branch of the UI is selected by
  `items.isEmpty()`, not by a boolean flag

### Requirement: polymorphic notifications use sealed types, not optional fields
The system MUST model polymorphic notification shapes (e.g. a
notice that is "resource-bound" vs "raw") as a sealed interface
with a small set of named variants. Modeling them as a `data class`
with N optional fields (where each field's presence depends on
the others), or as a `String resKey` discriminator that is
matched in a `when` at the consumer site, is forbidden.

#### Scenario: a Localized notice has a typed variant
- **WHEN** a notice is bound to a string resource
- **THEN** it is one of the named variants of
  `Notice.Localized` (e.g. `NoSubtitles`, `SubtitleIn`,
  `QualityReduced`)
- **AND** the consumer site uses an exhaustive `is` check;
  adding a new variant is a compile error in the screen
  until a matching branch is added

#### Scenario: a Raw notice has a non-empty message
- **WHEN** a notice is already resolved
- **THEN** it is `Notice.Raw(message)` and the message is
  non-empty

### Requirement: ViewModel session flags live in a StateFlow, not a private mutable
The system MUST model internal ViewModel session flags
(`isSyncing`, `isLoading`, `isRefreshing`) as a
`MutableStateFlow<Boolean>` that participates in the same
flow combinator that produces the public UI state. A
`private var Boolean` plus a parallel `_uiState.update`
codepath that mirrors the var to the public state is
forbidden — the two-track design has to be kept in sync
manually and is the kind of subtle bug that the StateFlow
machinery is meant to eliminate.

#### Scenario: a flag-only change re-emits the public state
- **WHEN** an internal session flag is flipped
- **THEN** the public `StateFlow<UiState>` re-emits a fresh
  state value that reflects the new flag
- **AND** no `_uiState.update { ... copy(...) }` call is
  needed in the flip site

### Requirement: dead UI state is removed in the same change
A new field on a UI state data class MUST be wired through to
the screen and (where applicable) a test that exercises the
non-default path in the same change that introduces the field.
A field that is never set by the ViewModel and never read by
the screen MUST NOT be introduced — the codestyle §8.6 "dead
code" rule applies and the field will be removed in review.

#### Scenario: a default-false UI flag is removed
- **WHEN** a `Boolean: Boolean = false` field on a UI state
  data class has no call site that sets it to `true` and no
  screen branch that reads it
- **THEN** the field is removed in the next refactor
- **AND** the corresponding "default is false" test is removed

### Requirement: Transport-Only Network Module
`:core:network` SHALL depend only on `:core:model` and `:core:domain` as project-module dependencies (external libraries such as Ktor and kotlinx-serialization are unaffected by this rule). It SHALL NOT depend on `:core:database` or `:core:preferences` — this specific exclusion is what the executable dependency-graph test verifies; the broader "depend only on model and domain" claim is a documented convention, not independently enforced against arbitrary other project modules. Repository and orchestration classes that combine HTTP transport with persistence or preferences SHALL live in `:core:data`, which depends on `:core:network`, `:core:database`, and `:core:preferences` and implements the domain ports those repositories fulfill.

#### Scenario: Network module has no persistence dependency
- **WHEN** the dependency graph of `:core:network` is resolved
- **THEN** no configuration includes `:core:database` or `:core:preferences`

#### Scenario: A repository combines transport and persistence
- **WHEN** a class needs to coordinate an API call with a Room cache or DataStore preference
- **THEN** it is implemented in `:core:data`, not `:core:network`

### Requirement: Feature Adapter Isolation
`:feature:*` modules SHALL depend only on `:core:model`, `:core:domain` (for domain ports), and shared UI modules (`:core:ui`, `:core:media`). They SHALL NOT depend on `:core:network`, `:core:database`, `:core:preferences`, or `:core:data`. Concrete adapter instances for transport, persistence, and preferences are constructed only at each platform's composition root and injected into feature ViewModels through domain port constructor parameters. `:core:media` is a shared playback/UI-bridging module, not a concrete IO adapter, and remains a permitted direct dependency for feature modules that need it (e.g. `:feature:player`); its own use of `:core:database`/`:core:preferences` is unchanged by this requirement.

#### Scenario: A feature module's dependency graph is resolved
- **WHEN** the dependency graph of any `:feature:*` module is resolved
- **THEN** no configuration includes `:core:network`, `:core:database`, `:core:preferences`, or `:core:data`

#### Scenario: A ViewModel needs catalog data
- **WHEN** a feature ViewModel is constructed
- **THEN** it receives a domain port (e.g. `CatalogPort`) as a constructor parameter, and the concrete implementation is supplied by the platform composition root, not imported directly by the feature module

### Requirement: UI Error Mapping Ownership
Functions that translate a `DomainError` or `Throwable` into a user-displayable message or UI-facing error type SHALL live in a UI-facing module (`:core:ui` or a `:feature:*` module), not in `:core:network`.

#### Scenario: A feature needs to display an error
- **WHEN** a feature ViewModel maps a `DomainError` to a UI-facing message
- **THEN** the mapping function it calls is defined in `:core:ui` or the feature module itself, not imported from `:core:network`

### Requirement: Executable Dependency Graph Invariants
For every module boundary this change defines a forbidden dependency for — each `:feature:*` module (forbidding `:core:network`, `:core:database`, `:core:preferences`, `:core:data`) and `:core:network` (forbidding `:core:database`, `:core:preferences`) — the allowed dependency graph SHALL be enforced by a test that inspects that module's resolved Gradle dependency graph or configuration classpath. Source-file import-statement scanning limited to a single source set (e.g. `commonMain` only) SHALL NOT be the sole enforcement mechanism for a module-boundary rule.

#### Scenario: A forbidden edge is introduced
- **WHEN** a `:feature:*` module's `build.gradle.kts` adds a dependency on `:core:network`, `:core:database`, `:core:preferences`, or `:core:data`, or `:core:network` adds a dependency on `:core:database` or `:core:preferences`
- **THEN** the dependency-graph invariant test fails

#### Scenario: A violation exists regardless of which declared target it comes from
- **WHEN** a forbidden dependency is added to any of a checked module's declared Kotlin target source sets (not just `commonMain`)
- **THEN** the dependency-graph invariant test still detects it, because it inspects the module's resolved compile classpath per target rather than scanning `commonMain` source files only

### Requirement: Composition Root Documentation
Each platform's composition root — the class or function responsible for constructing concrete network, persistence, preferences, and platform adapters and injecting them into feature ViewModels — SHALL be documented, including which platforms currently lack a production composition root and what non-production default they fall back to.

#### Scenario: A developer looks for composition-root ownership
- **WHEN** a developer wants to know where Android, Desktop, or Web construct their real adapters
- **THEN** a checked-in doc names the responsible class per platform and states explicitly whether that platform's composition root is production-ready or falls back to a non-production default

#### Scenario: Android's session/auth wiring is documented as complete
- **WHEN** the composition-root doc describes Android
- **THEN** it states that Android's composition root constructs real session, credential, and authenticated-client adapters (no longer the in-memory default), while Desktop and Web remain documented as falling back to the non-production default until their own changes land

### Requirement: Authenticated Navigation Start Destination
The authenticated content rendered after a platform's session gate SHALL start its own navigation back stack at a real, non-placeholder destination. No navigation entry reachable from production authenticated navigation SHALL have an empty placeholder body.

#### Scenario: User is authenticated
- **WHEN** the session gate switches from the login slot to the authenticated content slot
- **THEN** the authenticated navigation host's initial back-stack entry renders real content (e.g. the catalog), not an empty or login-shaped entry

#### Scenario: A production nav entry is reached
- **WHEN** any navigation entry registered in a production app's `entryProvider` is navigated to
- **THEN** its body constructs a real ViewModel and screen rather than an empty placeholder comment

### Requirement: Composition Root Completeness
A platform's production composition root SHALL NOT construct a feature ViewModel using only its no-op/default port parameters when a real adapter for that port already exists and is reachable from the composition root.

#### Scenario: Production Android startup is inspected
- **WHEN** a test inspects the ViewModels constructed by Android's production navigation host
- **THEN** none of them is bound to `InMemorySessionState` or to a port parameter's no-op/empty-success default when a real adapter exists for that port

