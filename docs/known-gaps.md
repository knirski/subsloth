# Known Gaps & Deferred Work

This document tracks items identified during the KMP restructuring (desktop + web
targets) that were intentionally deferred or remain as known gaps. Each entry
includes the reason, impact, and what would be needed to close it.

---

## 1. SQLite web worker bundling

**Status:** Known gap

`:webApp` doesn't bundle the `sqlite-wasm-worker` NPM package.
The database wasm builder in `core/database/src/wasmJsMain/` creates
`Worker("sqlite-wasm-worker/worker.js")` which needs to be available in the
webpack bundle.

The `sqlite-web` library's worker (`@androidx/sqlite-web-worker`) is a **local
npm package** in the AndroidX repo, not published to npm.

**To close:**
- Add `implementation(npm("@sqlite.org/sqlite-wasm", "3.51.2-build5"))` to
  `:core:database` or `:webApp` wasmJs dependencies
- Create a worker JS file that imports `@sqlite.org/sqlite-wasm`
- Add webpack config via `webpack.config.d/` or the `commonWebpackConfig` DSL
- Set COOP/COEP headers (`Cross-Origin-Opener-Policy: same-origin`,
  `Cross-Origin-Embedder-Policy: require-corp`) for OPFS support

---

## 2. `androidTarget()` in KMP Convention

**Status:** Deferred

The `subsloth.kmp.library` convention does not declare `androidTarget()`.
Android modules use separate AGP-based conventions (`subsloth.android.library`,
`subsloth.android.application`) and consume KMP modules as JVM bytecode.

Adding `androidTarget()` would:
- Allow KMP modules to produce native Android artifacts directly
- Enable `androidMain` source sets for Android-specific `expect`/`actual`
- Remove the dual-build-system split for some modules

**Blocked by:** Both `com.android.library` and `org.jetbrains.kotlin.multiplatform`
register a `kotlin` extension, causing a conflict in the precompiled script
plugin. Possible approaches:
- Create a separate `subsloth.kmp.android.library` convention
- Add `androidTarget()` per-module (doesn't need convention changes)
- Wait for Gradle/AGP/KMP compatibility improvements

**Impact:** Low. The current approach works (Android → JVM bytecode → AGP). The
main cost is that Android-only libraries (WorkManager, Media3) require separate
AGP modules rather than `androidMain` in shared modules.

**To close:** Resolve the AGP+KMP plugin conflict in the convention, add
`androidTarget()` to `subsloth.kmp.library`, migrate `androidMain` source sets
where applicable, and update `:androidApp` consumption.

---

## 3. `WebWorkerSQLiteDriver` nullable bug

**Status:** Blocked on upstream

`sqlite-web:2.7.0-alpha05` has a bug where `isNull` caches the column
type from the first row only. Documented in
`linhvnguyen9/room3-sqlite-web-nullable-npe-repro`.

**Impact:** Affects wasmJs database queries with nullable columns. May cause
incorrect results or crashes when `isNull` returns a cached type from a
different row.

**To close:** Upgrade `sqlite-web` when a fix is published.

---

## 4. Web Crypto `@JsFun` Promise interop

**Files:** `core/preferences/src/wasmJsMain/AccountProfileStore.wasm.kt`

**Status:** Known gap

`webCryptoHmacHex` JS function returns a Promise (because `crypto.subtle.sign`
is async), but Kotlin/Wasm `@JsFun` returns the Promise object synchronously.
Proper `Promise.await()` interop for wasmJs needs `kotlinx.coroutines` support
that isn't available for wasmJs.

**Impact:** At runtime, the function receives a Promise object instead of the
hex string. Profile key derivation will fail on wasmJs. Functionality degrades
gracefully — other platforms (JVM, iOS) are unaffected.

**To close:** Wait for Kotlin/Wasm Promise interop support, or implement a
JS-side synchronous wrapper using `crypto.subtle` synchronously (not possible
with current Web Crypto API design).

---

## 5. `ViewModelInjection` detekt suppressions

**Files:** `desktopApp/DesktopNavHost.kt`, `webApp/WebNavHost.kt`

**Status:** Accepted

Correct `@Suppress` usage — necessary because `viewModel(key = contentId)`
derives the ViewModel key from composable state. No cleaner approach exists
with current Navigation3 + KMP lifecycle API.

**Impact:** None. Suppressions are scoped narrowly and correctly.

**To close:** N/A — not a gap.

---

## 6. iOS-Specific Tests

**Status:** Skipped per product direction

No `iosTest` source sets or iOS-specific test tasks exist. All KMP tests run
on JVM via `jvmTest`.

**Impact:** Low while iOS is not a target; would need to be addressed before
shipping iOS.

**To close:** Add `iosTest` source sets and configure KMP test execution on iOS
simulator/devices.

---

## 7. Navigation3 on Desktop & Web

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

## 8. Full Feature Integration on Desktop & Web

**Status:** Known gap

Desktop and web apps show a placeholder screen with a "Player Demo" button
rather than the full Catalog / Login / Settings flows.

**Impact:** Medium. User-visible gap — desktop/web apps show a demo screen, not
a functional app.

**To close:**
- Wire CatalogScreen into `DesktopNavHost` and `WebNavHost` when available
- Add login flow for desktop (preferences work on JVM)
- For web, provide stub/storage-free implementations of database/preferences
  ports, or use browser-localStorage-based alternatives

---

## 9. WorkManager in `:feature:library`

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

## Resolved

The following items were previously tracked but are now resolved:

| Item | Resolution |
|---|---|
| **Compose Hot Reload** | Bundled and enabled by default since CMP 1.10.0. Project is on `1.12.0-alpha01`. No action needed. |
| **`local.properties` hardcoded Nix path** | Auto-generated from `$ANDROID_HOME` by `flake.nix` shellHook on every `direnv allow` entry. |
| **DataStore on wasmJs** | `LocalStorageDataStore` backs `DataStore<Preferences>` with browser `localStorage`. Persists across page reloads. |
| **Navigation3 on desktop** | `SavedStateConfiguration` + `androidx.savedstate:savedstate:1.5.0`. ✅ |
| **Navigation3 on web (wasmJs)** | `savedstate:1.5.0` + `savedstate-compose` (wasm support since 1.3.2). ✅ |
| **Room 3.0 on wasmJs** | `sqlite-web` + `WebWorkerSQLiteDriver` via `kotlinx-browser`. ✅ |
| **Crypto CSPRNG** | Browser `crypto.getRandomValues()` via `@JsFun`. ✅ |
| **Normalization order** | `trim → NFC → lowercase` matching JVM/iOS. ✅ |
| **CredentialStore localStorage security** | Removed "encrypted" claim, added plaintext warning. ✅ |
| **Database inMemory → persistent** | Now uses `databaseBuilder` with Worker persistence. ✅ |
| **`parseMediaId` truncation** | `toIntOrNull()` instead of `toLong().toInt()`. ✅ |
