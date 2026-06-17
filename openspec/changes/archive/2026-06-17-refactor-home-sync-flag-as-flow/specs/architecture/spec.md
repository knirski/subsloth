# Architecture Specification (delta)

## What Changes

- `HomeViewModel.isSyncingActive: Boolean` is removed; the
  `isSyncing` flag is now a private `MutableStateFlow<Boolean>`
  that participates in the `combine` of `catalogItems("movie")`,
  `catalogItems("show")`, and `isSyncing` directly. The
  `setSyncing(value)` helper that mirrored the flag to
  `_uiState.update { ... copy() }` is removed.
- The public `HomeUiState.Content.isSyncing: Boolean` field
  remains the single source of truth for the UI; the internal
  state of the syncing flag is now exclusively a private
  `MutableStateFlow` rather than a `private var` plus a
  parallel `_uiState.update` codepath.

## ADDED Requirements

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
