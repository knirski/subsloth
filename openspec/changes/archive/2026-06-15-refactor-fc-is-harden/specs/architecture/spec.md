# Architecture Specification (delta)

## MODIFIED Requirements

### Requirement: Typed Error Composition
Recoverable domain and application failures SHALL use Kotlin `Result<T>` with sealed typed error models. The `when` expression over a `DomainError` sub-hierarchy SHALL be exhaustive, with the compiler enforcing coverage of all variants.

#### Scenario: Classifier maps a transport failure
- **WHEN** a Ktor or IO throwable reaches a port or adapter boundary
- **THEN** the network layer routes it through a single `NetworkErrorClassifier` and returns a typed `DomainError` (no string matching on exception messages)

#### Scenario: HTTP status code is preserved
- **WHEN** a `ResponseException` carries a 4xx or 5xx status code
- **THEN** the classifier preserves the code in `NetworkError.HttpError(code, message)` so downstream classifiers can dispatch 401 to `AuthRequired` and 404 to `NotFound`

## ADDED Requirements

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
