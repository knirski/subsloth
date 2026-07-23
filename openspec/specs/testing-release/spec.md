# testing-release Specification

## Purpose
Defines the baseline requirements for the CI pipeline and release process, ensuring deterministic builds, secure artifact handling, and standardized versioning.
## Requirements
### Requirement: Offline CI
Required CI on pull requests and pushes to `main` SHALL be deterministic, offline-only, use the Gradle wrapper with JDK 17 for both the Gradle runtime and the Kotlin/Java compile toolchain, and verify wrapper integrity via `gradle/actions/wrapper-validation`.

#### Scenario: CI runs
- **WHEN** required CI executes
- **THEN** it runs wrapper validation, OpenAPI validation, `check`, `lintDebug`, `testDebugUnitTest`, `assembleDebug`, secret/artifact scanning, no-comments invariant checks, and Kodi-parity invariant tests without Media credentials or live Media calls

### Requirement: Local Live Drift Only
Live Media drift tests SHALL run only from a developer machine with local `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD`.

#### Scenario: Live drift command is documented
- **WHEN** a developer wants to verify live drift
- **THEN** docs provide a local command that skips without env vars and records only sanitized response-shape/capability summaries

### Requirement: Debug Sideload Release Scope
v1 release artifacts SHALL be debug-signed APKs for internal sideloading, with dedicated release signing deferred.

#### Scenario: Release docs are read
- **WHEN** a developer reads release docs
- **THEN** the docs explain APK naming, manual install/update, rollback, changelog expectations, internal distribution, and absence of in-app update checks

### Requirement: Sensitive Artifact Exclusion
CI and release workflows SHALL NOT upload credentials, auth headers, signed URLs, browser traces, HAR files, authenticated screenshots, signing keys, or signing passwords.

#### Scenario: Release workflow uploads artifacts
- **WHEN** a release workflow publishes artifacts
- **THEN** only the debug-signed APK and non-sensitive release notes/checksums are uploaded

#### Scenario: Gradle caches are restored or saved
- **WHEN** Gradle caches are restored or saved in CI
- **THEN** they SHALL NOT contain Media credentials, signing keys, signing passwords, build scans, browser traces, HAR files, authenticated screenshots, or live-drift artifacts

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

### Requirement: Release Mechanism
The repository SHALL use semantic-release, triggered on push to `main`, to determine the next SemVer version from conventional commits, create a `vX.Y.Z` git tag when a release-worthy commit exists, and publish a GitHub Release with auto-generated notes. The repository SHALL NOT require `release-please`, a committed `version.txt`, or a maintained `CHANGELOG.md` file as part of this mechanism.

#### Scenario: Release is created after a push to main
- **WHEN** a conventionally-titled pull request is squash-merged to `main`
- **THEN** `semantic-release.yml` runs semantic-release, which creates a `vX.Y.Z` tag when a release-worthy commit exists and publishes a GitHub Release with generated notes, without pushing any commit back to `main`

#### Scenario: Android version derives from the release tag
- **WHEN** the `:androidApp` Gradle build resolves version metadata
- **THEN** `versionName` is derived from `git describe --tags --abbrev=0 --match=v*` (falling back to `0.0.0` when no tag exists) and `versionCode` is computed deterministically from the SemVer components

#### Scenario: Release notes have no separate changelog file
- **WHEN** a developer looks for release notes
- **THEN** they are read from the GitHub Release description; the repository does not maintain a `CHANGELOG.md` file

