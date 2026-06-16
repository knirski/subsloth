# Refactor: collapse isSyncing into HomeUiState.Content

## Why

`HomeViewModel` exposed three observables for what is one cohesive
piece of state: `_uiState: StateFlow<HomeUiState>`,
`_isSyncing: StateFlow<Boolean>`, and `_syncErrors: SharedFlow<SyncError>`.
The two flows could drift — there was nothing enforcing that
`isSyncing == true` implies the Content state had the corresponding
data, and a state observer had to combine two sources to know whether
to show a spinner. This violates the FC/IS rule in
`docs/codestyle.md §8.1` ("session flags belong in the state class,
not in `var` fields").

The `syncErrors` SharedFlow also used `replay = 1`, which means a
new collector would receive the *most recent* error on subscription
even if the user had already dismissed it. That's a one-shot event
that should not be replayed. The fix is `extraBufferCapacity = 1`:
the emit never blocks but no past event is delivered to a late
subscriber.

## Scope

- `HomeUiState.Content` gains an `isSyncing: Boolean = false` field
  (`@Immutable` data class).
- `HomeViewModel._isSyncing` is removed. The sync coroutine
  mutates `isSyncing` via `updateContent { it.copy(isSyncing = ...) }`.
- `HomeScreen` reads `isSyncing` from the Content state
  (`(state as? HomeUiState.Content)?.isSyncing == true`).
- `HomeViewModelTest`: the `isSyncing` test reads from `uiState`
  using Turbine.
- `MutableSharedFlow<SyncError>(replay = 1)` becomes
  `MutableSharedFlow<SyncError>(extraBufferCapacity = 1)`.

## Out of scope

- Folding `syncErrors` into the state. The error is a one-shot
  event with snackbar semantics; putting it in the state would
  require a "consumed" flag and a `viewModel.consumeError()` call
  that has no good time to fire. The SharedFlow is the right model.
- The same state-shape refactor for the other ViewModels (Library,
  Downloads, etc.). Each is a separate PR.

## Risk

- One screen-level reads of `isSyncing` change; the rest of the
  view layer (the `CatalogContent` block) is unaffected.
- The `syncErrors` semantics change for late subscribers: they
  no longer see a replayed error from before their subscription.
  This is the correct behavior for a one-shot event.
- The `isSyncing` test now must navigate from `Loading` to the
  first `Content` emission in Turbine. The test loops on the
  sealed type.
