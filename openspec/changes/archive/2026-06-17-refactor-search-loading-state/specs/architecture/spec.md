# Architecture Specification (delta)

## What Changes

- `SearchUiState` becomes a sealed interface with three variants:
  `Idle` (no query), `Loading(query: String)` (in-flight), and
  `Results(query: String, items: ImmutableList<Media>)` (loaded).
  The previous `isLoading: Boolean` field on `Results` is removed.
- `SearchViewModel.searchInternal` calls `yield()` between the
  Loading and Results emissions so the StateFlow surfaces the
  in-flight state on synchronous dispatchers (cache hits, local
  DB) in addition to the asynchronous (network) path.

## ADDED Requirements

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
