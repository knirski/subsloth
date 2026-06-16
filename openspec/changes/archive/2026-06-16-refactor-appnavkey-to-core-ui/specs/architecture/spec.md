# Architecture Specification (delta)

## What Changes

- `AppNavKey` and its 11 concrete subtypes are extracted to
  `:core:ui` (package `net.subsloth.core.ui`). The three per-app
  copies in `androidApp`, `desktopApp`, and `webApp` are removed.

## ADDED Requirements

### Requirement: shared navigation types live in `:core:ui`
The system MUST place typed `NavKey` subtypes and any common navigation
contracts in `:core:ui` so they are not duplicated per app. The
per-app `SavedStateConfiguration` builders (the polymorphic
serializer registration) may remain per app because they are wired
at process start, but the key types themselves MUST be defined once.

#### Scenario: AppNavKey is sourced from :core:ui
- **WHEN** a navigation route is added or removed
- **THEN** the change happens in a single file under `:core:ui`
- **AND** the per-app NavKeys.kt files do not exist
