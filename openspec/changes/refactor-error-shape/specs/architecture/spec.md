# Architecture Specification (delta)

## MODIFIED Requirements

### Requirement: Typed Error Composition
Recoverable domain and application failures SHALL use Kotlin `Result<T>` with sealed typed error models. The `DomainError` hierarchy SHALL distinguish `Technical` failures (network, decode, sync) from `Business` failures (auth, payment, media, download, quality, library) so the UI can dispatch by category at the type level.

#### Scenario: Technical failure is identified at the type level
- **WHEN** a `NetworkError`, `DecodeError`, or `SyncError` is returned
- **THEN** it is a `DomainError.Technical` and the consumer can `when (e: DomainError.Technical)` exhaustively

#### Scenario: Business failure is identified at the type level
- **WHEN** an `AuthError`, `PaymentLimitError`, `MediaError`, `DownloadError`, `QualityError`, or `LibraryError` is returned
- **THEN** it is a `DomainError.Business` and the consumer can `when (e: DomainError.Business)` exhaustively

### Requirement: Network Boundary Returns Typed DomainError
The network shell SHALL return a `DomainError.Technical` (or a `Result` carrying one) at the port boundary. Domain errors SHALL NOT be wrapped in a `Throwable` subclass at the I/O boundary; the typed value is the error.

#### Scenario: Result carries a DomainError directly
- **WHEN** an I/O call fails with a recoverable error
- **THEN** the call returns `Result.failure(<DomainError>)` — not `Result.failure(DomainResultException(<DomainError>))`
- **AND** consumers can do `(error as? DomainError)` to recover the typed value without unwrapping a wrapper

## ADDED Requirements

### Requirement: Pure Error Classifiers in Domain
The `:core:domain` module SHALL host pure classifiers that map typed `DomainError` values to other domain types. ViewModels SHALL call these classifiers rather than match on `Throwable.message`.

#### Scenario: PlaybackErrorClassifier maps HTTP status codes
- **WHEN** the player receives `NetworkError.HttpError(401)` from the network shell
- **THEN** `PlaybackErrorClassifier.classify` returns `PlaybackError.AuthFailure`
- **AND** the player ViewModel does not need to inspect `Throwable.message` to make this decision

#### Scenario: PlaybackErrorClassifier maps 403 to StreamUrlExpired
- **WHEN** the player receives `NetworkError.HttpError(403)` from the network shell
- **THEN** `PlaybackErrorClassifier.classify` returns `PlaybackError.StreamUrlExpired`

#### Scenario: Unclassified errors are wrapped as Recoverable
- **WHEN** the player receives any other `DomainError`
- **THEN** `PlaybackErrorClassifier.classify` returns `PlaybackError.Recoverable(cause = error)` so the UI can display a generic message and surface the typed cause
