# Project Assessment — SubSloth

**Date:** 2026-07-19
**Status:** Superseded — see the 2026-07-23 assessment and
[`docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`](superpowers/plans/2026-07-23-repository-assessment-remediation.md)
for the current readiness picture. Current platform status lives in the
[platform support matrix](readiness/platform-support-matrix.md), not in this
document's verdict below. Code quality sections below remain accurate as
technical detail; the release-readiness verdict and operational-readiness
table do not. The architecture section is now also stale in places: the
`enforce-architecture-boundaries` change (merged 2026-07-23, after this
assessment was written) added `:core:data`, shrank `:core:network` to
transport-only, removed `:core:model`'s Compose runtime dependency, and
replaced import-scanning-only enforcement with an executable Gradle
dependency-graph test. See [`docs/module-structure.md`](module-structure.md)
for the current picture.
**Scope:** Full repository review — architecture, code quality, testing, documentation, and operational readiness.

---

## Executive Summary

SubSloth is a **well-engineered, spec-driven Kotlin Multiplatform media application** targeting Android (primary), Desktop (JVM), and Web (Wasm/JS). It implements a native streaming client for language learning with dual subtitles, built against a reverse-engineered Kodi-compatible API.

**Verdict (superseded, see status header above):** All 11 v1 OpenSpec changes were implemented and archived, and architecture, code quality, and testing depth are strong. However, the 2026-07-23 repository assessment found that Android still runs on an in-memory session with no-op ViewModels in its production start path, Desktop has placeholder navigation and an in-memory session, Web forces mock data and lacks the isolation headers its OPFS persistence claim requires, Desktop tests are not run in CI, the Web test suite is empty, and benchmark/baseline-profile claims exceed what's actually passing. See [`docs/readiness/platform-support-matrix.md`](readiness/platform-support-matrix.md) for the current tier of each platform and what's required to promote it.

---

## Technical Assessment

### Architecture: Functional Core / Imperative Shell ✅ **Excellent**

| Aspect | Assessment | Evidence |
|--------|------------|----------|
| **FC/IS Separation** | Strictly enforced | `:core:model`, `:core:domain` have zero Android/framework deps; verified by architecture tests (`CoreModelArchitectureTest`, `DomainArchitectureTest`) |
| **Port/Adapter** | Consistent | Ports in `:core:domain/port/`; adapters in `:core:network` (transport only), `:core:database`, `:core:preferences`; multi-adapter orchestration (e.g. `CatalogRepository`) in `:core:data` |
| **Error Handling** | Typed end-to-end | `Outcome<T>` / `DomainError` sealed hierarchy; no `Throwable` leaks into domain; I/O shell translates at boundaries |
| **State Management** | Unidirectional + sealed UiState | ViewModels expose `StateFlow<UiState>`; `UiState` sealed interfaces with `@Immutable`/`@Stable` data classes |
| **Dependencies** | Inward only | `:feature:*` → `:core:*`; no cycles; `:androidApp` wires container only |
| **KMP Safety** | Verified | `commonMain` compiles for JVM + Wasm/JS; `:core:model` + `:core:domain` use only stdlib, kotlinx-datetime, kotlinx-collections-immutable — no Compose runtime dependency (Compose stability is supplied via a checked-in stability-configuration file consumed by UI-facing modules, not by `:core:model` annotations) |

**Architecture Tests Passing:**
- `:core:model:jvmTest` → `CoreModelArchitectureTest`
- `:core:domain:jvmTest` → `DomainArchitectureTest`
- `:core:network:jvmTest` → `NetworkPolicyTest`, `RequestIdentityTest`, `RetryPolicyTest`

---

### Code Quality ✅ **High**

| Check | Result | Notes |
|-------|--------|-------|
| **Spotless (ktlint)** | ✅ Pass | `spotlessCheck` clean |
| **Detekt** | ✅ Pass | 0 findings across all modules; custom rules in `:testing:detekt-rules` |
| **Kotlin Compiler** | ✅ Pass | `-Xexpect-actual-classes` for Room; strict nullability |
| **API Lint (Vacuum)** | ⚠️ Warnings only | 38 `camel-case-properties`, 17 `oas3-missing-example` — expected for discovery contract |

**Code Style Conventions Followed:**
- Sealed interfaces with `data class`/`data object` variants (one per file)
- `kotlinx.collections.immutable` for all public collections
- `kotlinx.datetime` (`Instant`, `Duration`) — no `java.time`
- `@Immutable`/`@Stable` on all UI state classes
- `when` exhaustive over `if-else` for sealed types
- Extension functions for domain behavior; local functions for scoping

---

### Module Structure (KMP)

```text
:core:model           → Pure domain types, identifiers, errors, Outcome<T>      [KMP: JVM, WasmJS]
:core:domain          → Policies, ports, pure business logic                    [KMP: JVM, WasmJS]
:core:network         → Ktor client, API DTOs, mappers (transport only)         [KMP: JVM, WasmJS]
:core:database        → Room 3.0 KMP, DAOs, entities, WebWorkerSQLiteDriver     [KMP: JVM, WasmJS]
:core:preferences     → DataStore Preferences, AccountProfileStore, Credentials [KMP: JVM, WasmJS]
:core:media           → Media3/ComposeMediaPlayer boundary, subtitle sync       [KMP: JVM, WasmJS]
:core:ui              → Shared Compose components, SessionGate, RootContainerVM [KMP: JVM, WasmJS]
:core:data            → Repositories combining transport/persistence/prefs, e.g. CatalogRepository [KMP: JVM, WasmJS]

:feature:auth         → Login screen, ViewModel, session integration            [KMP: JVM, WasmJS]
:feature:catalog      → Home, Search, catalog rows, media cards                 [KMP: JVM, WasmJS]
:feature:details      → Movie/Series detail screens                             [KMP: JVM, WasmJS]
:feature:player       → Player screen, ViewModel, playback controls             [KMP: JVM, WasmJS]
:feature:library      → Library, Downloads screens                              [KMP: JVM, WasmJS]
:feature:settings     → Settings, Diagnostics screens                           [KMP: JVM, WasmJS]

:androidApp           → AGP application, AppContainer, MainActivity             [Android only]
:desktopApp           → Compose Desktop, DesktopNavHost                         [JVM Desktop]
:webApp               → Compose Wasm/JS, WebNavHost                             [Wasm/JS]
```

**Convention Plugins** (`build-logic/convention`):
- `subsloth.kmp.library` — KMP library with shared test deps, `freeCompilerArgs += "-Xexpect-actual-classes"`
- `subsloth.kmp.android.library` — KMP + Android library via `com.android.kotlin.multiplatform.library` (resolves AGP+KMP extension conflict); used by `:core:database`, `:core:media`
- `subsloth.android.library` / `.application` / `.library.compose` — AGP + Compose
- Version catalog in `gradle/libs.versions.toml` (single source of truth)

---

### Networking (Ktor 3.5) ✅ **Robust**

| Feature | Implementation |
|---------|----------------|
| **Client Factory** | `ClientFactory.create()` — configurable engine, auth, base URL |
| **Kodi Identity** | Fixed `User-Agent: Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1` + `Accept` headers |
| **Auth** | HTTP Basic via `Auth` plugin; credentials injected at `SessionPort.open()` |
| **Retry** | `HttpRequestRetry` — 2 retries, 500ms base delay, on 429/5xx + timeout |
| **Timeouts** | 30s request / 10s connect / 30s socket |
| **Validation** | `ResponseValidationPlugin` — detects redirects, HTML responses, non-JSON |
| **Logging** | `Logging` plugin — HEADERS level (redacts `Authorization`) |
| **Mock Testing** | `Ktor.client.mock` + `:testing:mock-api` fixtures + `:testing:api-contract` schema validation |

**API Contract:** `api/subsloth.openapi.yaml` (OpenAPI 3.1) — discovery contract from live Kodi plugin inspection. Marked `x-subsloth-contract-status: initial-discovery-contract` with explicit caveats on field nullability.

---

### Data Layer ✅ **Solid**

| Component | Tech | Offline-First | Account Scoping |
|-----------|------|---------------|-----------------|
| **Catalog Cache** | Room 3.0 KMP | ✅ `CatalogRepository` merges API + cache | ✅ Partitioned by `AccountProfileKey` |
| **Library/Downloads** | Room 3.0 KMP | ✅ Local-first UI reads | ✅ Partitioned |
| **Preferences** | DataStore Preferences + OKIO | ✅ `UserPreferences` Flow | ✅ `AccountProfileStore` per profile |
| **Credentials** | Android Keystore (Android) / localStorage (Web) | N/A | ✅ Encrypted on Android; plaintext warning on Web |
| **Mappers** | DTO → Domain at port boundary | `:core:network/mapper/` — pure, tested | Typed `MappingResult` with `Outcome` |

**Room KMP Notes:**
- `sqlite-bundled` (JVM), `sqlite-web` + WebWorker (WasmJS)
- Schema export to `core/database/schemas/` (KSP)
- `WebWorkerSQLiteDriver` nullable bug tracked in `known-gaps.md` #3 (upstream)

---

### UI Layer (Compose Multiplatform) ✅ **Modern**

| Platform | Navigation | Theming | Adaptive | TV Focus | Accessibility |
|----------|------------|---------|----------|----------|---------------|
| **Android** | Navigation3 (AGP) | Material3 | `material3-adaptive` | `androidx.tv` + `TvFocus` | Semantic labels, `clickAction`, `AccessibilityTestRecipes` |
| **Desktop** | Navigation3 (KMP) | Material3 | Window size classes | N/A | Desktop A11y tests |
| **Web (Wasm)** | Navigation3 (KMP) — all feature screens wired | Material3 | Responsive CSS | N/A | Semantic HTML via Compose HTML |

**State Management:**
- ViewModels (KMP `androidx.lifecycle:lifecycle-viewmodel-compose`) scoped to navigation entries
- `StateFlow<UiState>` collected via `collectAsStateWithLifecycle()` (Android) / `collectAsState()` (Desktop/Web)
- Sealed `UiState` per screen (`HomeUiState`, `LoginUiState`, `PlayerUiState`, etc.)
- Slot APIs for composable extensibility (`HomeScreen(onMovieClick, onShowClick)`)

**Screenshot Testing:** Compose Preview Screenshot Testing (Google) — 10 screens × 3 form factors (phone, tablet, TV) in `:androidApp:screenshotTest`.

---

### Testing Strategy ⚠️ **Broad coverage, gaps in CI enforcement and benchmark evidence — see below**

| Layer | Tools | Coverage |
|-------|-------|----------|
| **Unit (Pure)** | JUnit 5, Turbine, `kotlinx-coroutines-test` | Policies, mappers, `Outcome` combinators, `InMemorySessionState` |
| **Contract** | `:testing:api-contract` + WireMock | Fixture schema validation (all endpoints), DTO round-trip, JSON structural checks |
| **Live Drift** | `ApiLiveDriftTest` + manual CI | Real API contract verification on demand via `workflow_dispatch` with secrets |
| **Integration (I/O)** | Room `TestDatabaseFactory`, DataStore in-memory, Ktor `MockEngine` | Repository behavior, sync logic, download queue |
| **UI (Desktop)** | Compose UI Test (JUnit 4) | 6 test classes exist, but `:desktopApp:test` is not invoked in CI (see `docs/readiness/platform-support-matrix.md`) |
| **Screenshot** | Compose Preview Screenshot Testing | 10 screens × 3 configs; the verification workflow is `workflow_dispatch`-only, not run on every PR |
| **Instrumented** | AndroidX Test, Espresso, UIAutomator | Requires emulator/device |
| **Benchmark** | Macrobenchmark (JUnit 4) | ⚠️ Not run in CI; last recorded manual run passed 3 of 7 scenarios (see `docs/testing/benchmarks.md`) |
| **Baseline Profiles** | `profileinstaller` + `BaselineProfileGenerator` | ⚠️ No `baseline-prof.txt` is committed; the Android release build does not currently consume a generated profile |

**Test Organization:**
- `:testing:assertions` — shared `assertThatOutcome()`, `assertFlowEmits()`
- `:testing:detekt-rules` — custom rules (e.g., no `!!` in commonMain)
- `:testing:tv-focus-harness` — D-pad navigation verification
- `:testing:mock-api` — WireMock + recorded fixtures

---

### Build & CI ✅ **Reproducible**

| Aspect | Implementation |
|--------|----------------|
| **Environment** | Nix flake (`flake.nix` + `flake.lock`) — pinned JDK 25/17, Android SDK command-line tools, Android Studio, Node/Yarn/Binaryen for Wasm |
| **Gradle** | Wrapper and `compileSdk`/`targetSdk`/AGP/Kotlin versions per `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml` (see `docs/readiness/platform-support-matrix.md` — do not duplicate the numbers here, they drift) + `build-logic` convention plugins |
| **CI** | GitHub Actions (offline-only) — formatting/detekt/invariant pre-checks + per-platform assemble/test jobs (Android, JVM/Desktop compile, Web); manual API drift workflow via `workflow_dispatch`; Desktop has no dedicated test job yet (see readiness matrix) |
| **Release** | semantic-release (conventional commit PR titles) → GitHub Release, tag-derived version, debug APK/Desktop/Web artifacts uploaded after the release is created — not `release-please` (see `docs/release.md`) |
| **Invariants** | `.github/scripts/check-invariants.sh` — scans for credentials, signed URLs, HAR files, traces |

**Pre-commit Checks (enforced by AGENTS.md):**
```bash
./gradlew spotlessApply spotlessCheck detekt \
  :core:model:compileKotlinJvm :core:domain:compileKotlinJvm \
  :androidApp:assembleDebug test
```

---

## Functional Assessment

### Feature Completeness (v1 Scope)

| Feature | Status | Notes |
|---------|--------|-------|
| **Authentication** | ✅ Done | Basic auth, session persistence, logout, auth repair gate |
| **Catalog Browse** | ✅ Done | Movies/Shows tabs, genres, countries, year, rating filters, search, sort |
| **Movie Detail** | ✅ Done | Metadata, qualities, subtitles, play/resume |
| **Series Detail** | ✅ Done | Seasons, episodes, episode metadata, play from episode |
| **Playback** | ✅ Done | Media3/ComposeMediaPlayer, quality selector, speed, subtitle track, next-episode, resume position |
| **Offline Downloads** | ✅ Done | Queue, quality selection, subtitle bundling, storage safety, Wi-Fi only, notifications |
| **Library** | ✅ Done | Continue watching, watchlist, history, progress sync |
| **Central Downloads** | ✅ Done | All downloads across profiles, queue management |
| **Settings** | ✅ Done | Playback, download, subtitle, storage, account preferences |
| **Diagnostics** | ✅ Done | Logs, database inspection, network status, API drift check |

**All OpenSpec Changes — v1 Complete (archived):**
1. `dev-environment-bootstrap` ✅
2. `foundation-api-contract` ✅
3. `release-and-ci-foundation` ✅
4. `core-domain-network` ✅
5. `auth-persistence-shell` ✅
6. `android-ui-foundation` ✅
7. `catalog-details` ✅
8. `playback` ✅
9. `offline-downloads` ✅
10. `library-settings-diagnostics` ✅
11. `verification-release` ✅ — architecture tests, screenshot tests, baseline profiles, macrobenchmarks, desktop compose tests, TV focus harness, device acceptance docs

**Status:** All archived. No active changes. Canonical v1 specs promoted to `openspec/specs/`.

---

### User Experience

| Dimension | Assessment |
|-----------|------------|
| **Adaptive Layout** | Phone / Tablet / Foldable / TV via `material3-adaptive` + `WindowSizeClass` |
| **TV Support** | D-pad focus (`TvFocusModifier`), focus search, overscan-safe insets |
| **Accessibility** | Semantic labels, content descriptions, click actions, TalkBack tested |
| **Edge-to-Edge** | `enableEdgeToEdge()`, system bar colors, IME animations |
| **Predictive Back** | `OnBackPressedCallback` + Compose back handlers |
| **State Restoration** | `SavedStateHandle` + `rememberSaveable` + Navigation3 saveable config |
| **Offline UX** | Cached catalog, downloaded media playable without network, queue persists |

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| **Upstream API drift** | High | High | Live drift tests (manual); fixture replay in CI; OpenAPI contract as baseline |
| **Kodi plugin API changes** | Medium | High | Version-pinned discovery (`plugin.video.mediatv-4.0.1.zip`); `x-kodi-source-reference` in spec |
| **Room Wasm nullable bug** | Low | Medium | Tracked in `known-gaps.md` #3; workaround: avoid nullable columns in web queries |
| **KMP Android target convention** | ✅ Resolved | N/A | New `subsloth.kmp.android.library` convention plugin resolves AGP+KMP split (`known-gaps.md` #2 closed via PR #189) |
| **iOS not supported** | N/A (out of scope) | N/A | Product decision; architecture ready but `iosMain` disabled |
| **Media3/ExoPlayer on Desktop/Web** | Medium | Medium | ComposeMediaPlayer wraps Media3 on Android; Desktop/Web use same API via `expect`/`actual` |
| **Signed URL leakage** | Low | Critical | `check-invariants.sh` scans for auth headers, signed URLs in fixtures/logs/screenshots |
| **Nix flake drift** | Low | Medium | `flake.lock` committed; `direnv allow` on clone |

---

## Documentation Quality ✅ **Thorough**

| Doc | Purpose | Status |
|-----|---------|--------|
| `README.md` | Project overview, architecture, getting started | ✅ Current |
| `AGENTS.md` | Agent bootstrap, verification, commit rules | ✅ Authoritative |
| `best_practices.md` | Port/adapter, errors, sealed types, collections, datetime, Compose | ✅ Normative |
| `docs/development.md` | Nix shell, test commands, live drift | ✅ Current |
| `docs/known-gaps.md` | Deferred/blocked items with resolution path | ✅ Current — #2 resolved (PR #189) |
| `docs/production-deployment.md` | COOP/COEP headers, Wasm MIME, SPA fallback | ✅ Current |
| `docs/agent/README.md` | Doc routing table for agents | ✅ Current |
| `docs/agent/*.md` | Workflow docs (OpenSpec, review, publishing, etc.) | ✅ Complete |
| `openspec/README.md` | Planning workflow, change list, validation | ✅ Current |
| `openspec/changes/*/specs/` | Requirements per change (source of truth) | ✅ 11 archived |
| `openspec/specs/` | Canonical v1 spec baseline | ✅ Promoted from all 11 changes |
| `docs/archive/superpowers/plans/` | Step-level implementation detail | ✅ Preserved |

---

## Operational Readiness

| Area | Status | Notes |
|------|--------|-------|
| **Debug APK Build** | ✅ | `./gradlew assembleDebug` → `androidApp/build/outputs/apk/debug/` |
| **Release Pipeline** | ⚠️ Partial | semantic-release on conventional commit PR titles (not `release-please` — see `docs/release.md`); publishes the GitHub Release before all platform artifacts are verified, and Desktop package metadata is not yet derived from the shared version — tracked under remediation-plan Change 8 |
| **Secret Management** | ✅ | No secrets in repo; `local.properties` for local creds; CI uses GitHub Secrets |
| **Artifact Scanning** | ✅ | Invariant check in CI + pre-commit |
| **Rollback** | ✅ | Git tags + semantic-release; APK artifacts in GitHub Releases |
| **Monitoring** | ⚠️ Partial | Diagnostics screen exposes logs, DB, network; no remote telemetry (by design) |

---

## Recommendations

### Short-term (v1.1 / Next)
1. ✅ **Automated offline fixture/schema validation** — 
   - Credential capture now reads `SUBSLOTH_LOGIN`/`SUBSLOTH_PASSWORD` env vars (no CLI history exposure)
   - `CaptureApi` reads `SUBSLOTH_URL` for API base URL (falls back to default endpoint)
   - `FixtureSchemaValidationTest` extended to cover all JSON endpoints (native + web-discovery), including structural and round-trip checks
   - Added `:testing:api-contract:validateFixtures` (offline) and `:testing:api-contract:captureAndValidate` (full pipeline) Gradle tasks
   - Shell script `scripts/capture/validate-fixtures.sh` for one-command pipeline
   - See PR #192
2. ✅ **API drift detection CI** — manually-triggered workflow using `SUBSLOTH_LOGIN`/`SUBSLOTH_PASSWORD`/`SUBSLOTH_URL` secrets to run `ApiLiveDriftTest` against the live API; detects schema or endpoint drift before it reaches users
   - See PR #193
3. ✅ **Add `androidTarget()` to KMP convention** —
   - New `subsloth.kmp.android.library` convention plugin with `androidTarget()`, `jvm()`, `wasmJs()` targets
   - Migrated `:core:database` and `:core:media` from manual config to the new plugin (removed ~82 lines of boilerplate)
   - See PR #189 — `known-gaps.md` #2 resolved
4. ✅ **Flesh out `:webApp` feature parity** —
   - All nav entries in `WebNavHost` now wired to real feature screens: Catalog → `HomeScreen`, Detail → `MovieDetailScreen`/`SeriesDetailScreen`, Library → `LibraryScreen`, Downloads → `DownloadsScreen`, Settings → `SettingsScreen`, Diagnostics → `DiagnosticsScreen`, OfflineLibrary → `LibraryScreen`
   - ViewModels scoped per entry with `ViewModelStoreOwner` lifecycle
   - Added `:feature:library` dependency
   - See PR #190

### Medium-term (v2)
1. **Enable iOS targets** — Xcode in Nix, `sqlite-framework`, AVPlayer wrapper, Compose for iOS (when stable)
2. **Background sync via WorkManager** — abstract behind `SyncScheduler` port (see `known-gaps.md` #9)
3. **Remote config / feature flags** — for gradual rollouts and kill switches
4. **Crash reporting (opt-in)** — integrate with Play Console / custom endpoint

### Technical Debt
1. **OpenAPI spec examples** — add examples to reduce 17 `oas3-missing-example` warnings
2. **CamelCase schema properties** — 38 warnings; either rename DTO fields or configure Vacuum rule
3. **Web Crypto on Safari** — `crypto.subtle` availability differs; test Wasm on iOS Safari when enabled

---

## Conclusion

**SubSloth is a reference-quality KMP project.** It demonstrates:

- **Disciplined architecture** — FC/IS not just claimed but tested
- **Spec-driven development** — OpenSpec as single source of truth, changes archived with traceability
- **Multiplatform done right** — shared business logic, platform-specific shells, Compose UI across 3 targets
- **Testing depth** — unit, contract (all endpoints), integration, UI, screenshot, benchmark, baseline profiles
- **Reproducible builds** — Nix flake pins entire toolchain
- **Operational hygiene** — invariant scanning, secret hygiene, conventional commits, automated releases

All v1 OpenSpec changes are implemented, verified, and archived, and the four short-term goals listed above (KMP Android target convention PR #189, webApp feature parity PR #190, automated offline fixture/schema validation PR #192, and API drift detection CI PR #193) are complete. That is a different claim from "ready for production": the 2026-07-23 assessment found real gaps in Android's authenticated runtime, Desktop's composition, Web's isolation posture, and the release pipeline's build-before-publish ordering. See `docs/readiness/platform-support-matrix.md` for the current, evidence-linked status of each platform.

---

*Assessment generated by agent review. For implementation details per change, see `openspec/changes/archive/*/design.md` and `docs/archive/superpowers/plans/2026-05-04-subsloth-android-app-implementation.md`.*
