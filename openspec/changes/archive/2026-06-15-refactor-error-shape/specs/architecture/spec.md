# Architecture Specification (delta)

## MODIFIED Requirements

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

## ADDED Requirements

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
