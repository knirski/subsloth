# Chore: remove dead UI state and never-reachable code paths

## Why

The codestyle §8.6 "dead code" rule says to remove unused
declarations promptly. The audit's C/D items called out a few
small concrete dead code that has been visible since the
home/state-shape refactor and the catalog slice landed:

- `HomeViewModel.buildContinueWatchingItems` and
  `buildOfflineItems` are `@Suppress("UnusedParameter")` stubs
  that always return `emptyList()`. The `takeIf { it.isNotEmpty() }`
  in `buildHomeContent` never adds the `HomeRow.ContinueWatching`
  / `HomeRow.AvailableOffline` rows. The two `HomeRow` data
  classes are also only referenced from the dead call sites
  and are not used by any screen or test.
- `SettingsUiState.Content.showNewEpisodeNotifications: Boolean = false`
  is a default-false flag, never set, never read by the UI.
  The only test that mentions it asserts it is false; the
  test name "no new-episode notification settings in v1"
  confirms it is intentionally absent in v1.

## Scope

- Delete `HomeRow.ContinueWatching`, `HomeRow.AvailableOffline`,
  `buildContinueWatchingItems`, `buildOfflineItems`.
- Delete the two dead `takeIf { it.isNotEmpty() }` branches
  in `buildHomeContent`.
- Delete `SettingsUiState.Content.showNewEpisodeNotifications`
  and the corresponding test case.

## Out of scope

- The `Suppress("UnusedParameter")` on
  `QualityPolicy.applySessionQualityChange.sessionQuality` is
  legitimate (param accepted for API consistency) — kept.
- The pre-existing test `isSyncing transitions true during sync
  then false after` exercises the home refactor's flag path
  and stays.

## Risk

- No public API changes. `HomeRow.ContinueWatching` and
  `HomeRow.AvailableOffline` are not referenced from any screen,
  preview, or test outside the dead call sites — confirmed by
  grep across the repo.
- The HomeScreen and screenshot tests only use `HomeRow.Movies`,
  `HomeRow.Shows`, and `HomeRow.Recency`.
