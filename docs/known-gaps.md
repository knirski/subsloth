# Known Gaps & Deferred Work

This document tracks items identified during the KMP restructuring (desktop + web
targets) that were intentionally deferred or remain as known gaps. Each entry
includes the reason, impact, and what would be needed to close it.

---

## 1. SQLite web worker bundling

**Status:** Resolved ✅

A local `sqlite-wasm-worker/` package with the AndroidX reference worker
implementation and `@sqlite.org/sqlite-wasm` dependency is bundled in
`:webApp`. The worker is declared as `implementation(npm("sqlite-wasm-worker", "file:${project.projectDir}/sqlite-wasm-worker"))` (absolute path to avoid
Yarn resolution issues from the generated `package.json` directory), and
`@sqlite.org/sqlite-wasm` is declared as an npm dependency.

The full `wasmJsBrowserDistribution` pipeline (including webpack bundling)
passes. The Kotlin/Wasm toolchain (Node.js, Yarn, Binaryen) is provisioned
from the Nix environment via `KOTLIN_NODEJS_HOME`, `KOTLIN_YARN_HOME`, and
`KOTLIN_BINARYEN_HOME` env vars in `flake.nix`, with Ivy repos in
`settings.gradle.kts` as a fallback.

**Remaining:**
- Cross-Origin headers (`Cross-Origin-Opener-Policy: same-origin`,
  `Cross-Origin-Embedder-Policy: require-corp`) are set on the
  webpack-dev-server via `webApp/webpack.config.d/opfs-headers.js`.
  Production deployments must also set these headers at the reverse proxy
  or CDN level.

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

**Files:** `core/preferences/src/wasmJsMain/kotlin/net/subsloth/preferences/AccountProfileStore.wasm.kt`

**Status:** Resolved

`webCryptoHmacHex` now returns `Promise<JsString>` from `@JsFun`, and the
`suspend` function calls `.await()` from `kotlinx.coroutines` (1.11.0 supports
wasmJs). Addressed by replacing the `String` return type with `Promise<JsString>`,
adding `kotlinx-coroutines-core` to wasmJsMain dependencies, and calling
`.await()` on the Promise.

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

**Status:** Resolved

All three platforms (Android, Desktop, Web) use `NavDisplay`/`entryProvider`/
`rememberNavBackStack` from `androidx.navigation3.runtime` with the same
`subslothNavConfig` and navigation key hierarchy. `DesktopNavHost.kt` and
`WebNavHost.kt` mirror the Android nav host structure.

---

## 8. WorkManager in `:feature:library`

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
| **SQLite web worker bundling** | Local `sqlite-wasm-worker` package in `:webApp` with `@sqlite.org/sqlite-wasm` npm dep. Full `wasmJsBrowserDistribution` passes. ✅ |
| **OPFS headers** | `webApp/webpack.config.d/opfs-headers.js` sets COOP/COEP on dev-server. Production deployment must replicate at reverse proxy. ✅ |
| **Nix/Gradle webpack environment** | `PREFER_SETTINGS` + Ivy repos in `settings.gradle.kts` + Nix `nodejs`/`yarn`/`binaryen` packages + `KOTLIN_*_HOME` env vars. ✅ |
| **Navigation3 desktop & web** | Both `DesktopNavHost` and `WebNavHost` use `NavDisplay`/`entryProvider` matching Android. ✅ |
| **Web Crypto Promise interop** | `@JsFun` returns `Promise<JsString>` + `.await()` from `kotlinx.coroutines` 1.11.0-wasmJs. ✅ |
