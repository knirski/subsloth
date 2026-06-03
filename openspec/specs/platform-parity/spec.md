# platform-parity Specification

## Purpose
Define the supported platform targets for subsloth (Android, desktop, and web), establish feature parity expectations across them, outline exemptions for iOS and macOS, and define boundaries for platform-exclusive features that must degrade gracefully on unsupported targets.
## Requirements
### Requirement: Supported Platforms

The project SHALL support three platform targets: Android, desktop (JVM/Compose Desktop), and web (Wasm JS/Compose for Web). Each supported platform SHALL be a first-class target for development, testing, and release.

#### Scenario: Entrypoint modules exist
- **WHEN** `./gradlew projects` is executed
- **THEN** the listed modules include `:androidApp`, `:desktopApp`, and `:webApp`

#### Scenario: Shared modules compile on all targets
- **WHEN** `:core:model`, `:core:domain`, `:core:network`, `:core:preferences`, `:core:ui`, `:feature:catalog`, `:feature:details`, `:feature:player`, and `:feature:settings` are compiled for each supported platform
- **THEN** compilation succeeds without platform-specific errors

#### Platform Support Table

| Platform | Target | Entrypoint Module | Build System | Status |
|---|---|---|---|---|
| Android phone | Android 16 | `:androidApp` | Gradle + AGP | Supported, parity required |
| Android tablet | Android 13 + adaptive | `:androidApp` | Gradle + AGP | Supported, parity required |
| Android TV | Android TV 8 | `:androidApp` | Gradle + AGP | Supported, parity required |
| Desktop | JVM / Compose Desktop | `:desktopApp` | Gradle + Kotlin JVM | Supported, parity required |
| Web | Wasm JS / Compose for Web | `:webApp` | Gradle + Kotlin Wasm | Supported, parity required |
| iOS | — | — | — | Exempt (no hardware) |
| macOS | — | — | — | Exempt (no hardware) |

---

### Requirement: Feature Parity

All supported platforms SHALL provide functionally equivalent user-facing capabilities. When a screen, flow, action, or setting is available on one supported platform, every other supported platform SHALL provide a version adapted to platform conventions and input modality.

#### Scenario: Catalog screen is available on all platforms
- **WHEN** the app is built for Android, desktop, and web
- **THEN** each entrypoint renders a working catalog screen with movie and series listings

#### Scenario: Detail view is available on all platforms
- **WHEN** a user opens a movie or series detail on any supported platform
- **THEN** the detail view renders the same content fields and actions, adapted to platform layout conventions

#### Scenario: Settings are available on all platforms
- **WHEN** the user opens settings on any supported platform
- **THEN** each settings screen from the shared `:feature:settings` module is rendered with equivalent functionality

#### Scenario: Platform-specific notification has no parity obligation
- **WHEN** Android shows a system notification
- **THEN** desktop and web are not required to show a functionally equivalent notification

#### Scenario: Platform-specific input modality is adapted
- **WHEN** Android TV uses D-pad navigation for the catalog
- **THEN** desktop uses mouse/keyboard and web uses browser-native pointer navigation, but both provide the same catalog screen and actions

---

### Requirement: iOS and macOS Exemption

iOS and macOS SHALL be exempt from all feature parity requirements. No parity obligation exists for Apple platforms until a future OpenSpec change introduces support and lifts the exemption.

#### Scenario: iOS build target is absent
- **WHEN** the project build configuration is inspected
- **THEN** no iOS-specific Kotlin source set (`iosMain`) or iOS build target is configured

#### Scenario: macOS build target is absent
- **WHEN** the desktop app build configuration is inspected
- **THEN** no macOS-specific native distribution format (Dmg) is required for feature parity validation, though Dmg packaging may still be configured

---

### Requirement: Android-Exclusive Feature Boundaries

Features that depend on Android-specific APIs (auth persistence, offline downloads, TV focus) SHALL use platform-conditional patterns and SHALL document their non-Android behavior. Non-Android platforms SHALL provide degraded-but-functional or no-op stubs for Android-exclusive features rather than crashing or silently omitting functionality.

#### Scenario: Android-exclusive feature degrades gracefully on desktop
- **WHEN** the desktop app is compiled and run
- **THEN** features using `androidx.security.crypto`, `androidx.work.WorkManager`, or `androidx.media3` degrade to a non-functional but non-crashing stub state

#### Scenario: Android-exclusive feature degrades gracefully on web
- **WHEN** the web app is compiled and run
- **THEN** features using Android-specific APIs degrade to a non-functional but non-crashing stub state with appropriate messaging

