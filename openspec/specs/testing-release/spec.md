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

### Requirement: Release Please
The repository SHALL use release-please with `release-type: simple`, `version.txt`, `CHANGELOG.md`, and tags in the form `vX.Y.Z`.

#### Scenario: Release is created after app scaffold exists
- **WHEN** release-please creates a GitHub Release and the app scaffold exists
- **THEN** the workflow builds `assembleDebug` and uploads `subsloth-vX.Y.Z-debug-<shortsha>.apk`

#### Scenario: App scaffold does not yet exist
- **WHEN** release-please creates a release before the app scaffold exists
- **THEN** the workflow produces a changelog-only release with no APK build or upload

#### Scenario: Android version derives from version.txt
- **WHEN** the `:app` Gradle build resolves version metadata
- **THEN** `versionName` is read from `version.txt` and `versionCode` is derived deterministically from the SemVer components using multipliers that accommodate at least three digits per component (e.g., 1,000,000 for Major and 1,000 for Minor)

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
