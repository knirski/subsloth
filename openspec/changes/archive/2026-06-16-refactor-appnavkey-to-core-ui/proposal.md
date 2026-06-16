# Refactor: extract AppNavKey to :core:ui

## Why

The typed navigation keys (AppNavKey and the 11 concrete subtypes)
were duplicated three times: once per app (androidApp, desktopApp,
webApp). All three copies were byte-equivalent except for a single
`@file:OptIn` directive on the web target. Every change to a
navigation route required touching three files.

The keys themselves are app-graph contracts that don't depend on any
Android-, JVM-, or Wasm-specific API beyond `androidx.navigation3` and
`kotlinx.serialization`, both of which :core:ui already uses for
its other navigation types. Extracting them to :core:ui gives a single
source of truth and removes the duplicate per-app registration.

## Scope

- New: :core:ui/.../AppNavKey.kt with the interface and all 11
  subtypes. Package: `net.subsloth.core.ui`.
- Removed: per-app duplicates in
  - androidApp/src/main/java/net/subsloth/navigation/NavKeys.kt
  - desktopApp/src/main/kotlin/net/subsloth/navigation/NavKeys.kt
  - webApp/src/wasmJsMain/kotlin/net/subsloth/navigation/NavKeys.kt
- Updated import sites:
  - androidApp/SubSlothNavHost.kt
  - desktopApp/DesktopNavHost.kt
  - webApp/WebNavHost.kt
  - desktopApp/.../NavConfig.kt
  - webApp/.../NavConfig.kt
- :core:ui gains the `kotlinx.serialization` plugin and the
  `kotlinx-serialization-core` dependency.
- Version catalog: new alias `kotlinx-serialization` (core artifact).

## Out of scope

- The per-app `subslothNavConfig` (the `SavedStateConfiguration`
  builder that registers the polymorphic serializers). Each app keeps
  its own because the configuration is created once at process start
  and the imports now resolve to `:core:ui` types. The duplicates in
  the SavedStateConfiguration body were already there before this
  refactor and are not a routing-contract concern.
- New `PreferenceValue` sealed type. That belongs to a follow-up
  focused on the preferences layer; no such contract exists in the
  current code base.

## Risk

- Imports change in 5 files; the new package is `net.subsloth.core.ui`,
  the same as the rest of :core:ui.
- Each app's build.gradle.kts already lists :core:ui as a dependency,
  so no new wiring is needed.
- The deserializer classes (`LoginKey.serializer()`, etc.) come for
  free with `@Serializable` and resolve identically to the prior
  locations.
