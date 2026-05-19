## Why

Playback combines Media3, resumable progress, ephemeral URLs, subtitles, quality fallback, and watched-state scoping; isolating it from offline-downloads keeps each change at a manageable size and lets them be developed in parallel.

## What Changes

- Add playback, resume, completion, next-episode, subtitle, quality, and playback speed behavior.
- Add signed media URL refresh and current-playback quality fallback.
- Add auth failure handling during playback.
- Add explicit watched toggle scoping (account-scoped vs. shared offline progress).

## Capabilities

### New Capabilities

- `playback`: Media playback, resume, completion, next episode, subtitles, quality, playback speed, URL refresh, current-playback quality fallback, auth failure during playback, and explicit watched toggle scoping.

### Modified Capabilities

- None.

## Impact

- Affects `:core:domain` (playback policies), `:core:media` (Media3 boundary), `:feature:player` (UI/ViewModels), playback foreground service, manifest permissions for `mediaPlayback`, media tests, and player ViewModel tests.
- Depends on auth/persistence scope boundaries (`auth-persistence-shell`) and core domain policies (`core-domain-network`).
- Can be developed in parallel with `offline-downloads`.
