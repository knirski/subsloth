## Why

An architecture audit found 5 small, low-risk hardenings that each prevent a real bug or contract drift, and that match patterns already documented in `docs/codestyle.md` and `docs/agent/lessons-learned.md`. The active change `verification-release` is explicitly test-only, so these belong in their own change. All five are pure refactors — no behavior change, no API break, no new dependencies.

## What Changes

- Make the `DomainError` root sealed interface usable for exhaustive `when` over the top type (currently it has zero direct variants, so `when (e: DomainError)` cannot be exhaustive and new error categories silently escape every classifier).
- Replace the two duplicate `isIoError` string-matchers in `:core:network` with a single `NetworkErrorClassifier` in `:core:domain/policy`, with a unit test.
- Move `PlayerViewModel.session` and `snapshotCountSinceSave` from standalone `var` fields into `PlayerUiState.Content` per `docs/codestyle.md` §8.1.
- Replace `HomeViewModel.syncJob: Job?` + manual cancel with `Channel<Unit>(CONFLATED) + flatMapLatest`, matching the pattern already in `SearchViewModel` (§8.3).
- Introduce a single `WATCHED_THRESHOLD` constant in `CompletionPolicy` and replace the three magic `0.9` literals in `DownloadsViewModel` and `LibraryViewModel`.

## Capabilities

### Modified Capabilities

- `architecture`: extends the FC/IS, sealed-typed-errors, and ViewModel-state-management expectations codified in `docs/codestyle.md` with concrete fixes.

## Impact

- Affects `core/model`, `core/domain`, `core/network`, `feature/player`, `feature/catalog`, `feature/library`. No module added, no dependency added, no public API removed.
- Test impact: existing tests must continue to pass; one new unit test for `NetworkErrorClassifier`.
- Risk: low. All five items are mechanical refactors with no behavior change. Rollback is a single revert per commit.
