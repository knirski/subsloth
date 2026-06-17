# Architecture Specification (delta)

## What Changes

- The two `HomeRow` data classes `ContinueWatching` and
  `AvailableOffline` are removed; the `buildContinueWatchingItems`
  and `buildOfflineItems` stubs and their dead `takeIf` call
  sites in `buildHomeContent` are removed. The home screen
  rows are now only `Recency`, `Movies`, and `Shows`.
- `SettingsUiState.Content.showNewEpisodeNotifications: Boolean = false`
  is removed; the corresponding test case is removed.

## ADDED Requirements

### Requirement: dead UI state is removed in the same change
A new field on a UI state data class MUST be wired through to
the screen and (where applicable) a test that exercises the
non-default path in the same change that introduces the field.
A field that is never set by the ViewModel and never read by
the screen MUST NOT be introduced — the codestyle §8.6 "dead
code" rule applies and the field will be removed in review.

#### Scenario: a default-false UI flag is removed
- **WHEN** a `Boolean: Boolean = false` field on a UI state
  data class has no call site that sets it to `true` and no
  screen branch that reads it
- **THEN** the field is removed in the next refactor
- **AND** the corresponding "default is false" test is removed
