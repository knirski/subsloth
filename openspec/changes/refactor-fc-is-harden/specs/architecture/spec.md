# Architecture Specification (delta)

## MODIFIED Requirements

### Requirement: Typed Error Composition
Recoverable domain and application failures SHALL use Kotlin `Result<T>` with sealed typed error models. The `DomainError` root sealed interface SHALL declare a direct `data object` per error category so `when (e: DomainError)` is exhaustive at the root.

#### Scenario: Error category is added
- **WHEN** a new error category is introduced (for example, a new transport or storage failure)
- **THEN** the compiler forces every existing exhaustive `when (e: DomainError)` site to add a branch for the new direct variant

#### Scenario: Classifier maps a transport failure
- **WHEN** a Ktor or IO throwable reaches a port or adapter boundary
- **THEN** the network layer routes it through a single `NetworkErrorClassifier` and returns a typed `DomainError` (no string matching on exception messages)

### Requirement: ViewModel State Discipline
Session-scoped flags and per-playback counters SHALL be fields of the UI state data class, not standalone `var` properties on a ViewModel.

#### Scenario: Player session flag is observed
- **WHEN** a `PlayerViewModel` mutates its session-scoped `session` or `snapshotCountSinceSave`
- **THEN** the change is observable through the published `StateFlow<PlayerUiState>` and the ViewModel holds no standalone mutable state for these fields

#### Scenario: Sync is cancelled by a new call
- **WHEN** a user invokes `HomeViewModel.sync()` while a previous sync is in flight
- **THEN** the previous in-flight sync is cancelled automatically through `Channel<Unit>(CONFLATED) + flatMapLatest` and no manual `Job.cancel()` is required

### Requirement: Threshold Constants
The domain layer SHALL expose named constants for completion and watch thresholds so ViewModels never inline numeric magic values.

#### Scenario: Watch threshold is reused
- **WHEN** a ViewModel decides whether a media item counts as "watched" for library or download completion
- **THEN** it compares against `CompletionPolicy.WATCHED_THRESHOLD` and not against a magic literal
