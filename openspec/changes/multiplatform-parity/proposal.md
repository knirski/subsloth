## Why

subsloth is already a Kotlin Multiplatform project with Android, desktop (JVM), and web (Wasm JS) targets, but requirements and implementation effort have focused entirely on Android. Desktop and web apps exist as shells without guaranteed feature parity. Without an explicit parity requirement, the non-Android targets will lag behind indefinitely, increasing the cost of eventual catch-up and diluting the value of the shared Compose Multiplatform UI.

## What Changes

- Declare Android, desktop, and web as supported platforms that SHALL maintain feature parity.
- Define feature parity as: every user-visible capability available on one supported platform is also available on every other supported platform, adapted to platform conventions where necessary.
- Declare iOS and macOS as explicitly exempt from the parity requirement (no hardware for testing).
- Update the project spec to reflect multiplatform scope instead of Android-only language.
- Add a canonical platform-support table to the project spec.

## Capabilities

### New Capabilities

- `platform-parity`: multiplatform target definitions, feature parity requirements, and platform exemption rules.

### Modified Capabilities

- `project`: update scope from "greenfield native Android app" to "KMP multiplatform app with Android, desktop, and web targets".

## Impact

- Affects `:core:*` modules for potential multiplatform alignment, `:androidApp`, `:desktopApp`, `:webApp` entrypoints, and the canonical project spec at `openspec/specs/project/spec.md`.
- Dependencies: multiplatform infrastructure already exists (Compose Multiplatform, shared `:core:*` modules). No new build tooling is required.
- Desktop and web features already share `:core:model`, `:core:domain`, `:core:network`, `:core:preferences`, `:core:ui`, `:feature:catalog`, `:feature:details`, `:feature:player`, and `:feature:settings`. The parity gap is in entrypoint wiring, platform-specific integrations, and edge behavior.
- Android-exclusive feature changes (`auth-persistence-shell`, `offline-downloads`, `library-settings-diagnostics`) may need platform-conditional patterns or no-ops on desktop/web.
