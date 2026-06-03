## Why

Downloads, season queues, storage limits, metered networks, and download foreground services have separate behavioral risk than playback; isolating them keeps both changes at manageable size and unblocks parallel development.

## What Changes

- Add offline home mode and local-file playback resilience.
- Add app-private download storage with opaque paths and backup exclusion.
- Add shared offline assets and metadata retention rules.
- Add item downloads and subtitle sidecars.
- Add confirmed season queues with per-episode quality/subtitle fallback policy.
- Add queue persistence, low-storage refusal, and metered-network safety.
- Add `dataSync` foreground service and minimal active-download notifications.

## Capabilities

### New Capabilities

- `offline-downloads`: Offline home mode, local playback resilience, app-private downloads, subtitle sidecars, item downloads, confirmed season queues, storage safety, metered-network handling, queue persistence, and operational notifications.

### Modified Capabilities

- None.

## Impact

- Affects `:core:domain` (download policies), `:core:media` (download controller), `:feature:library` and `:feature:downloads` for queue UI, app-private filesystem, WorkManager (where used), `dataSync` foreground service, manifest permissions, and download tests.
- Depends on auth/persistence scope boundaries (`auth-persistence-shell`) and core domain policies (`core-domain-network`).
- Can be developed in parallel with `playback`.
