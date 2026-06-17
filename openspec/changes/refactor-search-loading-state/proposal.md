# Refactor: SearchUiState drop isLoading side-channel

## Why

`SearchUiState.Results` carried a `Boolean isLoading` field that
encoded transient in-flight state. The codestyle §8.5 forbids this
pattern: 3 valid states (idle, loading, results) collapsed into
1 data class with 2 boolean axes. Auditing ViewModels (audit D
follow-up) called this out as the same antipattern we removed in
PR #146 from `HomeUiState.Content.isSyncing`.

Splitting the shape into a sealed hierarchy makes invalid
combinations unrepresentable and lets the UI dispatch on the
sealed type instead of branching on a flag.

## Scope

- `SearchUiState` becomes `Idle | Loading(query) | Results(query, items)`.
- `SearchViewModel.searchInternal` emits `Loading` then `Results`,
  with a `yield()` between to surface the Loading state through
  the StateFlow (the original boolean field was the only thing
  that distinguished the in-flight state from the conflated final
  state on synchronous dispatchers).
- `SearchScreen` handles the new `Loading` variant with a spinner.
- `SearchViewModelTest`: drop the now-meaningless `isLoading.isFalse()`
  assertion; add a positive `search emits loading state before
  results` test.

## Out of scope

- The existing `isSyncing: Boolean` field on `HomeUiState.Content`
  is the same pattern, but the home refactor (#146) just landed
  it. Re-examining it now would re-open a freshly-closed PR.

## Risk

- The `Loading` variant is now visible on synchronous dispatchers
  thanks to the explicit `yield()`. In production with real
  network calls, the inner flow already suspends on
  `listCatalog().getOrDefault(...)`, so the spinner was already
  showing in those cases; the explicit `yield()` just makes the
  fast-path (cache hit, local DB) show the spinner too, which is
  the intended UX.
