## ADDED Requirements

### Requirement: Core and Network Tests
The project SHALL test pure domain policies, architecture boundaries, OpenAPI fixture validation, mappers, Kodi request identity, no-comments invariants, retry policy, and unexpected response mapping.

#### Scenario: Core architecture test runs
- **WHEN** tests inspect core model and domain modules
- **THEN** Android, Compose, Room, DataStore, Retrofit, OkHttp, Media3, filesystem, and notification dependencies are absent

#### Scenario: Comments invariant runs
- **WHEN** network and mapper tests run
- **THEN** no production code path fetches or requires comments endpoints or web-only frontend comments resources

### Requirement: Android Shell Tests
The project SHALL test Room, DataStore, encrypted credentials, backup exclusion, logout cleanup, login/auth repair, ViewModels, media progress, offline playback, network-loss behavior, low-storage behavior, metered downloads, process restoration, and storage management.

#### Scenario: Credential storage is tested
- **WHEN** instrumented credential tests run on emulator/device
- **THEN** save/read/clear behavior, backup exclusion, and logout retention/cleanup boundaries are verified

#### Scenario: Logout retention partition is tested
- **WHEN** logout, reset-preferences, clear-watch-library, and delete-downloads flows are tested
- **THEN** logout retains shared downloads/progress and active-profile preferences/watch-library data; deleting downloaded videos/subtitles clears shared offline media/progress; resetting preferences clears only active-profile preferences; clearing watch/library data clears only active-profile watch/library data; and other account profiles remain untouched except for intentionally shared offline-media deletion

### Requirement: UI and Accessibility Tests
The project SHALL include Compose UI, TV D-pad focus, accessibility, and screenshot coverage for critical app flows.

#### Scenario: Detail screenshot test runs
- **WHEN** movie and series detail screenshots are captured across phone, tablet, and TV dimensions
- **THEN** required metadata and actions are visible and comments UI is absent

#### Scenario: TV focus test runs
- **WHEN** TV browse, detail, player, library, and Downloads screens are tested
- **THEN** focus order is deterministic and focus restores after navigation and dialogs

### Requirement: Performance and Device Acceptance
The project SHALL include baseline profile, macrobenchmark, and manual device acceptance coverage for Android TV 8, Android tablet 13, and Android phone 16.

#### Scenario: Device acceptance is documented
- **WHEN** `docs/testing/device-acceptance.md` is created
- **THEN** it covers login/logout, browsing, details without comments, playback/resume, subtitles, offline mode, downloads, storage management, and adaptive behavior for the required devices
