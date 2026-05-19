## 1. Library

- [ ] 1.1 Add LibraryViewModel tests for Continue Watching, favorites, watch later, Available Offline, and logged-out Offline Library constraints.
- [ ] 1.2 Implement logged-in library rows and filters.
- [ ] 1.3 Implement logged-out Offline Library browsing, playback entry, storage usage, and delete-download actions only.

## 2. Downloads and Storage

- [ ] 2.1 Add Downloads screen tests for active, queued/paused, failed/unavailable, completed, and season per-episode statuses.
- [ ] 2.2 Implement central Downloads screen rows, groups, state-specific actions, and queue recovery.
- [ ] 2.3 Implement storage usage, sorting/filtering helpers, delete actions, and explicit confirmations.
- [ ] 2.4 Implement TV Downloads layout, focus order, focus restoration, overscan-safe spacing, and simplified destructive actions.

## 3. Settings

- [ ] 3.1 Add SettingsViewModel tests for subtitle, quality, playback speed, download, and logout cleanup controls.
- [ ] 3.2 Implement settings screen and account-scoped preference updates.
- [ ] 3.3 Implement logout cleanup confirmation and scoped cleanup execution.
- [ ] 3.4 Verify no new-episode notification settings exist in v1.

## 4. Diagnostics

- [ ] 4.1 Add diagnostics tests for allowed fields, redacted fields, `FLAG_SECURE`, and absence of export/share/copy actions.
- [ ] 4.2 Implement local-only view-only diagnostics.
- [ ] 4.3 Verify diagnostics do not expose raw request shape, credentials, URLs, account identifiers, profile keys, or media file paths.

## 5. Verification

- [ ] 5.1 Run `./gradlew :feature:library:test :feature:settings:test :app:assembleDebug`.
- [ ] 5.2 Run TV focus tests for Downloads after UI test infrastructure exists.
- [ ] 5.3 Run `openspec validate library-settings-diagnostics --strict`.

