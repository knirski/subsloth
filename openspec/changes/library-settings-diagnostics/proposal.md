## Why

Library, central Downloads, storage management, settings, and diagnostics are user-facing management surfaces that depend on the auth and offline data model but should be implemented after core playback/download execution exists.

## What Changes

- Add library rows for Continue Watching, favorites, watch later, and Available Offline.
- Add central Downloads screen groups, actions, per-episode season queue visibility, and TV remote-friendly behavior.
- Add explicit storage management and destructive confirmation rules.
- Add settings for subtitles, quality, playback speed, downloads, and logout cleanup.
- Add view-only local diagnostics with strict redaction and no export/share/copy behavior.

## Capabilities

### New Capabilities

- `library-settings-diagnostics`: Library, Downloads screen, storage management, settings, logout cleanup UI, and diagnostics.

### Modified Capabilities

- None.

## Impact

- Affects `:feature:library`, `:feature:settings`, app navigation, storage/delete use cases, diagnostics ViewModels, TV focus tests, and settings tests.
- Depends on auth/persistence and playback/offline/download state models.

