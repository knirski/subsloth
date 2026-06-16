# Architecture Specification (delta)

## What Changes

- `HomeUiState.Content` gains a `Boolean isSyncing` field that is
  the single source of truth for the sync-in-progress indicator.
  The previous standalone `HomeViewModel.isSyncing: StateFlow<Boolean>`
  is removed.
- `HomeViewModel.syncErrors` switches from `replay = 1` to
  `extraBufferCapacity = 1`. Late subscribers no longer receive
  a replayed error from before their subscription.

## ADDED Requirements

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
