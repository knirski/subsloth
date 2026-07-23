# platform-parity Specification (delta)

## MODIFIED Requirements

### Requirement: Supported Platforms

The project SHALL support three platform targets: Android, desktop (JVM/Compose Desktop), and web (Wasm JS/Compose for Web). Each supported platform SHALL be a first-class target for development, testing, and release. Release-readiness tier and promotion gates for each platform are defined in the `readiness` specification and published at `docs/readiness/platform-support-matrix.md`; they are not restated here. This requirement governs which platforms exist as build/test targets, not their production status — a platform listed here as supported may still be gated at a pre-production tier in the readiness matrix.

#### Scenario: Entrypoint modules exist
- **WHEN** `./gradlew projects` is executed
- **THEN** the listed modules include `:androidApp`, `:desktopApp`, and `:webApp`

#### Scenario: Shared modules compile on all targets
- **WHEN** `:core:model`, `:core:domain`, `:core:network`, `:core:preferences`, `:core:ui`, `:feature:catalog`, `:feature:details`, `:feature:player`, and `:feature:settings` are compiled for each supported platform
- **THEN** compilation succeeds without platform-specific errors

#### Platform Support Table

| Platform | Target | Entrypoint Module | Build System | Readiness tier |
|---|---|---|---|---|
| Android phone | Android 16 | `:androidApp` | Gradle + AGP | See `docs/readiness/platform-support-matrix.md` — Internal beta |
| Android tablet | Android 13 + adaptive | `:androidApp` | Gradle + AGP | See `docs/readiness/platform-support-matrix.md` — Internal beta |
| Android TV | Android TV 8 | `:androidApp` | Gradle + AGP | See `docs/readiness/platform-support-matrix.md` — Internal beta |
| Desktop | JVM / Compose Desktop | `:desktopApp` | Gradle + Kotlin JVM | See `docs/readiness/platform-support-matrix.md` — Preview |
| Web | Wasm JS / Compose for Web | `:webApp` | Gradle + Kotlin Wasm | See `docs/readiness/platform-support-matrix.md` — Stateless demo (GitHub Pages); production not yet granted |
| iOS | — | — | — | Exempt (no hardware) |
| macOS | — | — | — | Exempt (no hardware) |
