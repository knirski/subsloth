## 1. `SessionPort` becomes `suspend`

- [ ] 1.1 Change `SessionPort.open`, `close`, and `invalidate` in `core/domain/src/commonMain/kotlin/net/subsloth/core/domain/port/SessionPort.kt` to `suspend fun`.
- [ ] 1.2 Update `InMemorySessionState` (`core/domain/.../port/InMemorySessionState.kt`) to match the new signatures (no internal behavior change needed).
- [ ] 1.3 Update `feature/auth/.../LoginViewModel.kt`'s `logout()` to call `sessionPort.close()` inside `viewModelScope.launch { }` (it currently calls it outside any coroutine).
- [ ] 1.4 Update `androidApp/src/androidTest/.../LoginFlowTest.kt`'s `FailingSessionPort` fake and any other `SessionPort` test fakes to the new signatures.
- [ ] 1.5 `./gradlew :core:domain:compileKotlinJvm :core:domain:jvmTest :core:ui:compileKotlinJvm :feature:auth:compileKotlinJvm :feature:auth:jvmTest :core:database:compileKotlinJvm :core:database:jvmTest test`

## 2. Android Keystore-backed `CredentialStore`

- [ ] 2.1 Switch `core/preferences/build.gradle.kts` from `subsloth.kmp.library` to `subsloth.kmp.android.library` (mirroring `core/database/build.gradle.kts`); add the `androidMain` source set.
- [ ] 2.2 Add `core/preferences/src/androidMain/kotlin/net/subsloth/preferences/CredentialStore.android.kt`: an `actual class CredentialStore` using `android.security.keystore.KeyGenParameterSpec` (API 26+, AES/GCM) to encrypt/decrypt the login/password pair; ensure the app's manifest/backup rules exclude the underlying storage file from Auto Backup and device-to-device transfer.
- [ ] 2.3 `./gradlew :core:preferences:compileKotlinJvm :core:preferences:assembleDebug`
- [ ] 2.4 Add an instrumented test proving save/read/clear round-trips through real Keystore-backed encryption (not a mock) and that cleared credentials are unrecoverable.
- [ ] 2.5 `./gradlew :core:preferences:connectedDebugAndroidTest` (or the equivalent device-test task the convention plugin exposes)

## 3. Android `SessionPort` adapter

- [ ] 3.1 Add an Android `SessionPort` implementation (e.g. `androidApp/.../AndroidSessionState.kt`) that: on construction, reads any persisted `CredentialsPort` credentials and attempts Kodi-compatible validation before emitting its initial state (cold-start recovery, bounded by `ClientFactory`'s existing timeout configuration); `open()` runs the validation call and on success persists credentials via `CredentialsPort.save` and emits `Session.Authenticated`; on failure returns `Outcome.Failure(AuthError.InvalidCredentials)` without mutating stored state; `close()`/`invalidate()` call `CredentialsPort.clear()` and emit `Session.Anonymous`.
- [ ] 3.2 `./gradlew :androidApp:compileDebugKotlin`

## 4. Authenticated client lifecycle in `AppContainer`

- [ ] 4.1 Replace `AppContainer.api`'s no-argument `ClientFactory.create()` with a construct that rebuilds `Api`/`ClientFactory.create(login, password, ...)` (and the `CatalogRepository` wrapping it) whenever the session adapter's credentials change, and reverts to an unauthenticated client on logout/invalidate.
- [ ] 4.2 `./gradlew :androidApp:compileDebugKotlin :androidApp:assembleDebug`

## 5. Wire `MainActivity` to the real session adapter

- [ ] 5.1 Replace `MainActivity.kt`'s bare `viewModel()` factory for `RootContainerViewModel` with a factory that injects the Android `SessionPort` adapter from `AppContainer` instead of the in-memory default.
- [ ] 5.2 `./gradlew :androidApp:assembleDebug`

## 6. Fix the authenticated nav host's start destination

- [ ] 6.1 In `SubSlothNavHost.kt`, change `rememberNavBackStack(LoginKey)` to `rememberNavBackStack(CatalogKey)` and remove the now-dead `entry<LoginKey> { }` body.
- [ ] 6.2 Fix the stale-capture race task 4's review surfaced: `entry<CatalogKey>`'s `HomeViewModelFactory(container.catalogRepository)` reads `container.catalogRepository` once at Compose `viewModel()` factory-invocation time, but `AppContainer` now rebuilds `catalogRepository` asynchronously in response to session changes (task 4). If `CatalogKey` composes before a fresh login's rebuild finishes, `HomeViewModel` permanently captures a stale (anonymous or previous-account) `CatalogRepository` for the rest of that `ViewModelStoreOwner`'s lifetime. Fix by having the factory read `container.catalogRepository` at `create()`-time (inside `HomeViewModelFactory.create()`, not captured in its constructor) so each ViewModel construction sees the current value — construction still only happens once per key/owner, so this doesn't fully eliminate a race against an in-flight rebuild, but it removes the guaranteed-stale-forever failure mode where the factory was built before login and never re-reads afterward. If a stronger guarantee is needed (e.g. blocking `CatalogKey`'s composition until any in-flight rebuild completes), note it as an open concern rather than over-engineering a fix beyond this task's scope.
- [ ] 6.3 `./gradlew :androidApp:assembleDebug`

## 7. Wire detail and auth-repair nav entries

- [ ] 7.1 Wire `entry<MovieDetailKey>`/`entry<ShowDetailKey>` in `SubSlothNavHost.kt` to construct `feature/details`'s existing `MovieDetailViewModel`/`ShowDetailViewModel` and their screens, following the same composition-root wiring pattern as `entry<CatalogKey>`.
- [ ] 7.2 Add an `AuthRepairScreen` composable (in `feature/auth` or `core/ui`, matching the project's existing screen-placement convention) backed by `LoginViewModel`'s existing `LoginUiState.AuthRepair`/`retryAuth()`/`dismissNeedsAuthRepair()`; wire it into `entry<AuthRepairKey>`.
- [ ] 7.3 `./gradlew :feature:details:compileKotlinJvm :feature:auth:compileKotlinJvm :androidApp:assembleDebug`

## 8. Wire library, downloads, settings, and player ViewModels

- [ ] 8.0 Fix a real, currently-dormant bug task 7's review surfaced and confirmed: `LoginViewModel` computes its `uiState` from `sessionPort.current()` exactly once in `init` (`checkInitialState()`) and never subscribes to `sessionPort.state` afterward. `MainActivity.kt`'s `login` slot constructs it via a keyless `viewModel { LoginViewModel(...) }`, and `SessionGate`'s `login`/`authenticated` slots share the same `ViewModelStoreOwner` (the Activity) with no nested back-stack scoping — so Compose returns the SAME cached `LoginViewModel` instance every time `SessionGate` toggles between slots. Today this is dormant because the only production call site that flips the session to `Anonymous` is `LoginViewModel.logout()` itself (which self-heals its own `uiState` in the same call). This task wires real logout cleanup (8.3) through the session/credentials ports — once that lands, an ordinary logout initiated any other way, or a future 401-triggered `invalidate()`, would leave the cached `LoginViewModel` showing stale `LoggedIn` state, rendering blank instead of the login form or auth-repair screen. Fix: give `LoginViewModel` an `init`-time `viewModelScope.launch { sessionPort.state.collect { session -> ... } }` that reacts to `Session.Anonymous` by setting `uiState` to `LoginForm` (ordinary logout/expiry) — reserve `AuthRepair` for the explicit `retryAuth()` call path already in place, don't try to distinguish "expired" from "logged out" via this collector alone unless a clean signal for that distinction already exists; if it doesn't, transitioning to `LoginForm` on any `Anonymous` emission (with `retryAuth()` remaining the only path to `AuthRepair`) is an acceptable, simple resolution — just make sure the transition doesn't fight with `logout()`'s own state-setting (avoid double-setting or flicker). Verify with a unit test (`LoginViewModelTest`, following its existing `runTest`/`UnconfinedTestDispatcher` pattern) that toggles a fake `SessionPort` to `Anonymous` from outside a `logout()` call and asserts `uiState` updates to `LoginForm`.
- [ ] 8.1 Construct `core/database/.../LibraryPortAdapter` in `AppContainer` from its existing Room DAOs and the session adapter; wire it into `entry<LibraryKey>`'s `LibraryViewModel`.
- [ ] 8.2 Construct `core/media/src/androidMain/.../DownloadController` in `AppContainer` from its existing storage/connectivity/DAO dependencies; wire it into `entry<DownloadsKey>`'s `DownloadsViewModel`.
- [ ] 8.3 Wire `entry<SettingsKey>`'s `SettingsViewModel` lambda parameters to real `UserPreferences`/Room/`CredentialsPort` calls, including the three independent logout cleanup lambdas (`deleteAllDownloads`, `clearPreferences`, `clearLibrary`, `clearCredentials`), each touching only its own canonical scope.
- [ ] 8.4 Wire `entry<PlayerKey>`'s `PlayerViewModel` port parameters that have a real, already-existing adapter to back them: `fetchEpisodes` via `container.catalogRepository.getDetails(showId)` (a `ShowDetails`'s `seasons[].episodes`, flattened), `loadPlaybackSpeed`/`savePlaybackSpeed` and `loadPreferredLanguage` via `UserPreferences.playbackSpeed`/`setPlaybackSpeed`/`subtitleLanguage` (profile-key-scoped, matching the existing account-profile pattern). Do NOT attempt to wire `fetchVideoSource`, `refreshStreamUrl`, or `saveProgress` — `core/domain/.../PlaybackPort` (the port these map to) has ZERO implementations anywhere in the current tree (verified: no adapter class implements it, and no Room DAO or other persistence exists yet for playback progress), so building one would be new feature engineering (stream-URL resolution, quality/DRM selection, progress persistence), not composition-root wiring — out of proportion for this change. Leave these three on their existing safe no-op defaults and record this as a known, larger gap for a future change (likely scoped under the `playback` capability) in this task's report and the final PR description. Investigate `onAuthFailure`/`resolveShowIdForEpisode` and wire them only if a clean existing mechanism is found (e.g. `onAuthFailure` calling `sessionPort.invalidate()` or reusing `PlayerScreen`'s existing `onNavigateToAuthRepair` callback); otherwise leave on default and note why.
- [ ] 8.5 `./gradlew :feature:library:compileKotlinJvm :feature:player:compileKotlinJvm :feature:settings:compileKotlinJvm :feature:auth:jvmTest :androidApp:assembleDebug`

## 9. Instrumented tests

- [ ] 9.1 Add instrumented tests covering: valid login, invalid login (typed error surfaced, no session opened), cold start with a persisted session, expired-credential auth repair, logout (verifying Logout Cleanup Scopes partitioning against real storage), and account switching.
- [ ] 9.2 Add a test proving production Android startup constructs no `InMemorySessionState` and no ViewModel bound to a no-op/default port parameter where a real adapter exists.
- [ ] 9.3 Verify secrets and authenticated payloads do not appear in logs or captured fixtures (check `Logging` plugin config and any test fixture capture).
- [ ] 9.4 `./gradlew :androidApp:connectedDebugAndroidTest` (or the CI instrumented-test task name if different)

## 10. Docs

- [ ] 10.1 Update `docs/architecture/composition-roots.md`'s Android section: session/auth is no longer "the exception" — state that Android now constructs real session, credential, and authenticated-client adapters; Desktop/Web rows are unchanged.
- [ ] 10.2 Update `docs/readiness/platform-support-matrix.md`'s Android rows: the Change 2 gap note (`InMemorySessionState` still wired; `LoginKey`-restart bug) is resolved; leave the Change 5/6/8 promotion rows as still-pending.

## 11. Spec and verification

- [ ] 11.1 Write `specs/auth-security/spec.md` and `specs/architecture/spec.md` deltas (already drafted; adjust if implementation details diverge).
- [ ] 11.2 `openspec validate wire-android-production-runtime --strict`
- [ ] 11.3 `./gradlew spotlessApply spotlessCheck detekt`
- [ ] 11.4 `./gradlew test`
- [ ] 11.5 `./gradlew :androidApp:assembleDebug :androidApp:connectedDebugAndroidTest`
- [ ] 11.6 `openspec validate --all --strict`
