# Known Gaps & Deferred Work

This document tracks items identified during the KMP restructuring (desktop + web
targets) that were intentionally deferred or remain as known gaps. Each entry
includes the reason, impact, and what would be needed to close it.

---

## 1. `androidTarget()` in KMP Convention

**Status:** Deferred

The `subsloth.kmp.library` convention does not declare `androidTarget()`.
Android modules use separate AGP-based conventions (`subsloth.android.library`,
`subsloth.android.application`) and consume KMP modules as JVM bytecode.

Adding `androidTarget()` would:
- Allow KMP modules to produce native Android artifacts directly
- Enable `androidMain` source sets for Android-specific `expect`/`actual`
- Remove the dual-build-system split for some modules

**Impact:** Low. The current approach works (Android → JVM bytecode → AGP). The
main cost is that Android-only libraries (WorkManager, Media3) require separate
AGP modules rather than `androidMain` in shared modules.

**To close:** Add `androidTarget()` to `subsloth.kmp.library` convention, migrate
`androidMain` source sets where applicable, and update `:app` consumption.

---

## 2. Compose Hot Reload

**Status:** Deferred

The `org.jetbrains.compose.hot-reload` Gradle plugin was not available at
version `1.12.0-alpha01` (the Compose Multiplatform version used by the
project). It requires:

1. The `org.jetbrains.compose.hot-reload` plugin published at a matching version
2. The JetBrains Space Maven repository configured in `pluginManagement`

**Impact:** Medium. Hot reload speeds up Compose UI iteration significantly,
especially for desktop development.

**To close:**
- Add `maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")` to
  root `settings.gradle.kts` `pluginManagement` (already present for
  dependencies)
- Add the plugin and agent to the version catalog at a resolvable version
- Apply to `:desktopApp` and optionally `:androidApp`

---

## 3. Split `:core:datasource-ktor`

**Status:** Will not do (by design)

This module implements a Media3 `BaseDataSource` that delegates to Ktor for
HTTP. The Ktor code is tightly coupled to Media3's Android data-source
abstraction — it extends Android classes and uses Android-specific threading.
The module is correctly scoped as an Android-specific integration.

**Impact:** None. The module is properly designed.

**To close:** N/A — not a gap.

---

## 4. iOS-Specific Tests

**Status:** Skipped per product direction

No `iosTest` source sets or iOS-specific test tasks exist. All KMP tests run
on JVM via `jvmTest`.

**Impact:** Low while iOS is not a target; would need to be addressed before
shipping iOS.

**To close:** Add `iosTest` source sets and configure KMP test execution on iOS
simulator/devices.

---

## 5. Navigation3 on Desktop & Web

**Status:** Known gap

The Android app uses Navigation3 (`NavDisplay`, `rememberNavBackStack`,
`entryProvider`). Desktop and web apps use simple state-based navigation
(`mutableStateOf` + `when` branches).

The KMP Navigation3 runtime (`org.jetbrains.androidx.navigation3`) is not fully
published for all targets (the `navigation3-runtime` KMP artifact did not
resolve). Additionally, the KMP `ViewModelProvider.Factory` API differs from
AndroidX, causing type-mismatch issues with Navigation3's `entry<PlayerKey>`.

**Impact:** Medium. Feature screens (PlayerScreen, CatalogScreen, etc.) are
shared, but the navigation structure is duplicated. Adding new routes or
changing navigation logic requires updating three nav hosts.

**To close:**
- Wait for the KMP Navigation3 runtime to be published for all targets
- Or define a shared navigation abstraction in a KMP module
- Upgrade desktop/web nav hosts to Navigation3 when available

---

## 6. Full Feature Integration on Desktop & Web

**Status:** Known gap

Desktop and web apps show a placeholder screen with a "Player Demo" button
rather than the full Catalog / Login / Settings flows. This is because:

- Catalog, Login, and Library screens are not yet wired into the nav hosts
- Auth and Library depend on `:core:database` and `:core:preferences` which
  don't compile for wasmJs (no Room/DataStore wasm support)
- Desktop uses `:core:preferences` (JVM-compatible) and could support login

**Impact:** Medium. User-visible gap — desktop/web apps show a demo screen, not
a functional app.

**To close:**
- Wire CatalogScreen into `DesktopNavHost` and `WebNavHost` when available
- Add login flow for desktop (preferences work on JVM)
- For web, provide stub/storage-free implementations of database/preferences
  ports, or use browser-localStorage-based alternatives

---

## 7. WorkManager in `:feature:library`

**Status:** Removed from KMP module

`:feature:library` was converted from an Android-only module to a KMP module.
The `work-runtime-ktx` dependency was removed because WorkManager is published
as an Android AAR and cannot be resolved by KMP's `jvmMain` classpath (which
expects standard JARs).

**Impact:** Low — no code yet uses WorkManager in this module. When background
sync logic is added, it must be abstracted behind an `expect`/`actual` or
moved to an `androidMain` source set (requires `androidTarget()` in convention).

**To close:**
- Abstract WorkManager behind a port interface (e.g. `SyncScheduler`)
- Provide `androidMain` actual using WorkManager
- Provide `jvmMain`/`wasmJsMain` no-op or alternative actual

---

## 8. `local.properties` Hardcoded Nix SDK Path

**Status:** Known gap

The `local.properties` file points to a specific Nix store path
(`5bmn18cl0nk3ndihjqnfj1shbi4zxvdp-androidsdk`). This path changes after
`nix-collect-garbage` or flake updates.

**Impact:** Low for active development; breaks CI after Nix GC.

**To close:**
- Generate `local.properties` dynamically via `direnv` or a Nix shell hook
- Or use the `ANDROID_SDK_ROOT` environment variable with a fallback in
  `build.gradle.kts`
- Or add `android.sdk.path` to a shared `gradle.properties` template
