## Context

Change 1 established the composition-contract pattern (construct concrete adapters at a composition root, inject ports into feature ViewModels) and documented that Android's `AppContainer` only does this for data/catalog adapters today. This change (Change 2 in `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`) is the first to wire session/auth and the remaining feature ViewModels to real Android adapters, and is a dependency for Change 5 (`enforce-platform-test-gates`), which will add composition-root tests rejecting mock/no-op bindings across all platforms.

Research against the current tree found that most of the "wiring" work is cheaper than the plan's wording implies: `LibraryPortAdapter` (`:core:database`) and `DownloadController` (`:core:media`'s `androidMain`, built in the archived `2026-06-03-offline-downloads` change) are both complete, tested-elsewhere, production-shaped port implementations that no composition root has ever constructed. The genuinely new work is the credential/session adapter (nothing like it exists yet) and the `SessionPort` interface change it requires.

## Goals / Non-Goals

Goals:

- A fresh Android install can authenticate against the configured Media service using real Keystore-backed encrypted credential storage, not an in-memory accept-anything default.
- Every ViewModel reachable from Android's authenticated nav host receives real adapters from `AppContainer`; none falls back to its no-op/default port parameters in a production build.
- The authenticated nav host's back stack starts at one authoritative destination (`CatalogKey`); no reachable nav entry is an empty placeholder body.
- Logout, cold start, and expired-session repair behave per the already-canonical `auth-security` requirements (Logout Cleanup Scopes, Auth Failure Repair, Persistence Scope Separation) — this change makes those requirements true, it does not relax or reinterpret them.

Non-Goals:

- Do not touch Desktop or Web composition roots (Changes 3A/3B).
- Do not build a new downloads engine or library adapter — `DownloadController` and `LibraryPortAdapter` already exist; this change only constructs and wires them.
- Do not add TV focus traversal tests, baseline profiles, or release-pipeline changes (Changes 5, 6, 8).
- Do not change the shape of any `feature/*` ViewModel's port parameters — they already accept the real port signatures via default lambda parameters; this change supplies real arguments instead of relying on the defaults.
- Do not implement biometric re-auth, multi-account simultaneous sessions, or server-side session revocation — out of scope for this change; "account switching" here means logout-then-login-as-a-different-account, which the existing `Persistence Scope Separation` requirement already governs.

## Decisions

- **`SessionPort.open`/`close`/`invalidate` become `suspend fun`.** The Android adapter's `open()` must await a Kodi-compatible validation network call before it can honor success/failure, and `close()`/`invalidate()` must await encrypted-storage clearing. This is a source-breaking change to a canonical `:core:domain` interface; every implementation and call site updates in the same change: `InMemorySessionState` (trivial — no actual suspension needed, but the signature must match), any test fakes (e.g. `LoginFlowTest`'s `FailingSessionPort`), and `LoginViewModel` (its `login()` already calls `sessionPort.open(...)` inside `viewModelScope.launch { }`, so adding `suspend` is a no-op at that call site; `logout()` currently calls `sessionPort.close()` *outside* a `launch` block and must move inside one).
- **New `:core:preferences` `androidMain` source set**, switching its convention plugin from `subsloth.kmp.library` to `subsloth.kmp.android.library` (the same plugin `:core:database` already uses for its own `androidMain`). Add an `androidMain` `CredentialStore` actual using `android.security.keystore.KeyGenParameterSpec` (API 26+, `KeyProperties.PURPOSE_ENCRYPT or PURPOSE_DECRYPT`, AES/GCM) to encrypt the login/password pair before writing them via the same DataStore-adjacent file mechanism the `jvmMain` actual uses, marked to exclude from `android:allowBackup`/`android:fullBackupContent`/`android:dataExtractionRules` per the `Credential Storage Protection` requirement. Reuses `CredentialsStoreAdapter`'s existing `CredentialsPort` wiring — no change needed there.
- **New Android `SessionPort` implementation** (e.g. `AndroidSessionState`, living in `androidApp` since it needs `Api`/`ClientFactory` from `:core:network` and `CredentialsPort` from `:core:preferences` — both already permitted composition-root dependencies): on construction, reads any persisted `CredentialsPort` credentials and attempts the Kodi-compatible validation call before emitting its initial `state` value (cold-start recovery); `open(credentials)` runs the validation call, and on success calls `CredentialsPort.save(...)` and emits `Session.Authenticated`; on failure returns `Outcome.Failure(AuthError.InvalidCredentials)` without touching stored state; `close()`/`invalidate()` call `CredentialsPort.clear()` and emit `Session.Anonymous`.
- **`AppContainer` gains authenticated-client lifecycle.** Today `api` is a `by lazy` property built with no credentials. Replace it with a construct that rebuilds `Api`/`ClientFactory.create(login, password, ...)` (and the `CatalogRepository` wrapping it) whenever the session adapter's credentials change, and reverts to an unauthenticated client on logout/invalidate — mirroring the pattern `ClientFactory.create` already supports (lines 37-48) but `AppContainer` never exercises.
- **`SubSlothNavHost` starts at `CatalogKey`, not `LoginKey`.** `rememberNavBackStack(LoginKey)` becomes `rememberNavBackStack(CatalogKey)`; the `entry<LoginKey> { }` body is removed entirely from this nav host (login lives one level up, in `SessionGate`'s `login = { ... }` slot in `MainActivity.kt`, which already works correctly).
- **`MovieDetailKey`/`ShowDetailKey` wire the existing `MovieDetailViewModel`/`ShowDetailViewModel`** from `feature/details/.../DetailViewModels.kt` — these are complete state machines; the gap is purely that no nav entry constructs them.
- **`AuthRepairKey` gets a real `AuthRepairScreen`**, backed by the existing `LoginViewModel.retryAuth()`/`dismissNeedsAuthRepair()`/`LoginUiState.AuthRepair` state machine (`feature/auth`) — offering retry-login and dismiss actions per the `Auth Failure Repair` requirement, rather than staying an empty placeholder.
- **`LibraryViewModel`/`DownloadsViewModel` wire `LibraryPortAdapter`/`DownloadController`** (both already exist, both already implement the right port interfaces) constructed in `AppContainer` from the Room DAOs it already owns (`database`) plus the session adapter (for `LibraryPortAdapter`'s profile-key scoping) and platform ports (`StoragePort`/`ConnectivityPort` for `DownloadController`, constructed the same way the archived `offline-downloads` change's own Android wiring notes describe).
- **`SettingsViewModel`'s lambda parameters wire directly to `UserPreferences`/Room/`CredentialsPort` calls** in `AppContainer` (no new adapter class needed — it already takes plain read/write lambdas, not a port object) — including the three independent logout cleanup lambdas (`deleteAllDownloads`, `clearPreferences`, `clearLibrary`, `clearCredentials`), each touching only its own canonical scope per the existing `Logout Cleanup Scopes` requirement.
- **`PlayerViewModel`'s remaining port parameters wire to `CatalogRepository`/`DownloadController`-backed real lookups** instead of their no-op defaults — video source resolution, episode listing, and progress save all have real backing data available in `AppContainer` by this point in the change.
- **Account switching gets no new mechanism.** `AndroidSessionState.close()` clears credentials and emits `Anonymous`; a subsequent `open()` with a different account's credentials derives a different profile key (via the already-implemented `AccountProfileStore` HMAC derivation `LibraryPortAdapter.profileKey()` already reads through `SessionPort.current().userId`) — this change verifies that real wiring exercises the existing derivation correctly, it does not add a new derivation path.

## Risks / Trade-offs

- **`SessionPort` becoming `suspend` is the highest-blast-radius single change** — it touches a canonical `:core:domain` interface consumed across `feature/auth`, `core/database` (`LibraryPortAdapter`), and every test fake. Mitigated by doing this as its own task with a full-suite compile+test pass before any adapter wiring begins, so a signature mistake is caught in isolation rather than entangled with the Keystore/navigation work.
- **Android Keystore behavior varies across OEM implementations and emulator API levels.** Mitigated by targeting API 26+ (already the documented minimum) and testing on the same emulator configuration CI's existing `instrumented-android-tests` job already uses, rather than introducing new device matrix requirements in this change.
- **Cold-start silent re-authentication adds a network call to app launch.** If the validation request is slow or the device is offline, this must not block the UI indefinitely — the adapter's cold-start recovery attempt needs a bounded timeout (reusing `ClientFactory`'s existing `HttpTimeout` configuration) and a clear fallback to the login screen (or, if playable offline downloads exist, the existing Offline Library entry point) rather than a hang.
- **Wiring `DownloadController` into `AppContainer` requires constructing `StoragePort`/`ConnectivityPort` and the DAOs it depends on** — these should already exist as Android platform adapters from the archived `offline-downloads` change; if any is missing or shaped differently than expected, that surfaces as a compile error in the wiring task, not a design-time unknown, since the interfaces themselves are unchanged by this proposal.

## Migration Plan

1. Change `SessionPort.open`/`close`/`invalidate` to `suspend fun`; update `InMemorySessionState`, `LoginViewModel.logout()`, and existing test fakes; run the full `:core:domain`/`:core:ui`/`feature:*`/`:core:database` test suite before proceeding.
2. Add `:core:preferences`'s `androidMain` source set (convention-plugin switch) and the Keystore-backed `CredentialStore` actual; add instrumented Keystore save/read/clear tests.
3. Build the Android `SessionPort` adapter (cold-start recovery, Kodi-compatible validation, typed failure, logout/invalidate clearing).
4. Wire `AppContainer`'s authenticated-client lifecycle (rebuild/teardown `Api`/`CatalogRepository` on credential change).
5. Wire `MainActivity`'s `RootContainerViewModel` to the real session adapter instead of the bare `viewModel()` factory default.
6. Fix `SubSlothNavHost`'s start destination and remove the dead `LoginKey` entry.
7. Wire `MovieDetailKey`/`ShowDetailKey` to the existing detail ViewModels; build and wire `AuthRepairScreen`.
8. Wire `LibraryViewModel`/`DownloadsViewModel` to `LibraryPortAdapter`/`DownloadController`; wire `SettingsViewModel`'s lambdas; wire `PlayerViewModel`'s remaining ports.
9. Add instrumented tests: valid/invalid login, cold start with session, expired-credential repair, logout partitioning, account switching, and a production-startup test proving no in-memory/no-op binding is reachable.
10. Update `docs/architecture/composition-roots.md` and `docs/readiness/platform-support-matrix.md` to reflect Android's completed session/auth wiring (Desktop/Web rows are unaffected).
11. Write delta specs and validate.

## Open Questions

- Whether the Android `SessionPort` adapter lives in `androidApp` directly or in a new small Android-only module — default to `androidApp` (it already houses `AppContainer`, the sole consumer) unless the implementer finds a concrete reuse reason (e.g. a future Change 3A wanting to share adapter code, which it cannot anyway since Desktop's credential policy is OS-keyring-based, not Android Keystore).
- Exact bounded timeout value for cold-start silent re-authentication — an implementation-time call within `ClientFactory`'s existing timeout configuration; no product requirement pins a specific number.
