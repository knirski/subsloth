# Refactor: HomeViewModel isSyncing as StateFlow, not private mutable

## Why

`HomeViewModel.isSyncingActive` was a `private var Boolean`
field that the public `isSyncing` flag in `HomeUiState.Content`
was synced against via a manual `_uiState.update { ... copy() }`
codepath. The pre-commit check #146 (PR #146) added the public
field as the source of truth, but the private mutable stayed
because the `combine(catalogItems("movie"), catalogItems("show"))`
flow didn't re-emit on flag-only changes.

This is a real two-track state design: one track is the public
sealed `HomeUiState.Content.isSyncing`; the other is the
private mutable used to seed the `combine` block. A future
reader of the code has to understand both tracks and the
synchronization between them.

Fold the syncing flag into a third `combine` argument so the
flow is naturally reactive and the private mutable goes away.
The `isSyncing` flag is now a private `MutableStateFlow<Boolean>`
that participates in the `combine` with the two catalog flows;
flag flips cause the combine to re-emit a fresh
`HomeUiState.Content` with the new flag, atomically and
without any external `_uiState.update` codepath.

## Scope

- `HomeViewModel`: replace `private var isSyncingActive: Boolean`
  with `private val isSyncing = MutableStateFlow(false)`.
- Add `isSyncing` to the `combine` in the `init` block.
- Remove the `setSyncing(value: Boolean)` helper that mirrored
  the flag to `_uiState`.
- Existing test `isSyncing transitions true during sync then
  false after` is unchanged; it asserts the same behaviour
  through the new flow path.

## Out of scope

- The `HomeUiState.Content.isSyncing: Boolean` field stays
  as a member of the Content data class (the same shape as
  the home refactor #146).

## Risk

- The `combine` is now over three flows; if any of them emits
  faster than another, the combine buffers. The pre-existing
  test exercises the flag transition end-to-end and still
  passes, so the buffering is acceptable.
- No new public API; no UI changes.
