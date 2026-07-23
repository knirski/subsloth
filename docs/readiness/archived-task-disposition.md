# Archived Task Disposition Ledger

This ledger records a disposition for every unchecked (`- [ ]`) task checkbox found across all archived OpenSpec changes, as required by the `readiness` specification's "Archived Task Disposition Ledger" requirement. It exists so a reader can distinguish stale bookkeeping (a real deliverable that was simply never ticked off before archive) from a live gap, without editing archived `tasks.md` files to make historical work appear complete.

Dispositions:

- **Verified complete** — the described code, test, or command exists in the current tree; only the checkbox itself was never ticked.
- **Superseded** — no longer meaningful (e.g. re-running a validation command that already gated the archive).
- **Still required** — a real, currently-open gap. Names its owning change from `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`.
- **Intentionally deferred** — requires infrastructure that does not exist yet and that no planned change currently adds; tracked here with a re-review trigger instead of a owning change.

Last reconciled: 2026-07-23, against `origin/main` at `d51dbec`.

---

## `2026-06-15-feature-session-port` (6 items — all verified complete)

| Task | Disposition | Evidence |
|---|---|---|
| 1.1 Create `Session` sealed interface in `:core:domain/port/` | Verified complete | `core/domain/src/commonMain/kotlin/net/subsloth/core/domain/port/SessionPort.kt` defines it |
| 1.2 Add `SessionPort` interface with `state`/`current()`/`open()`/`close()`/`invalidate()` | Verified complete | Same file implements exactly this API |
| 2.1 Create `InMemorySessionState` implementing `SessionPort` | Verified complete | `core/domain/src/commonMain/kotlin/net/subsloth/core/domain/port/InMemorySessionState.kt` exists |
| 2.2 Add `InMemorySessionStateTest` covering all four transitions | Verified complete | `core/domain/src/commonTest/.../InMemorySessionStateTest.kt` exists |
| 3.1 `./gradlew :core:domain:compileKotlinJvm :core:domain:jvmTest spotlessCheck detekt` | Verified complete | Bookkeeping — command gated the original archive |
| 3.2 `openspec validate feature-session-port --strict` | Verified complete | Bookkeeping — the change is archived, which requires this to have passed |

## `2026-06-15-refactor-error-shape` (16 items — all verified complete)

| Task | Disposition | Evidence |
|---|---|---|
| 1.1 Delete `DomainResultException.kt` | Verified complete | `grep -r DomainResultException` across the non-archived tree returns nothing; the file does not exist |
| 1.2 Unwrap `Result.failure(DomainResultException(...))` in `Mapper.kt` | Verified complete | No `DomainResultException` reference remains anywhere |
| 1.3 Unwrap in `CatalogRepository.kt` | Verified complete | Same |
| 1.4 Unwrap in `SubSlothNavHost.kt` | Verified complete | Same |
| 1.5 Grep for remaining `as? DomainResultException` sites | Verified complete | Same — zero hits |
| 2.1 Add `Technical`/`Business` as direct children of `DomainError` | Verified complete | `core/model/src/commonMain/kotlin/net/subsloth/core/model/error/DomainError.kt:27-38` defines both as nested `sealed interface` |
| 2.2 `NetworkError`/`DecodeError`/`SyncError` extend `Technical` | Verified complete | Confirmed in `DomainError.kt` |
| 2.3 `AuthError`/`PaymentLimitError`/`MediaError`/`DownloadError`/`QualityError`/`LibraryError` extend `Business` | Verified complete | Confirmed in `DomainError.kt` |
| 2.4 Confirm exhaustive `when` sites still compile | Verified complete | Current build compiles; enforced by the compiler |
| 2.5 Add `DomainErrorTest` for `Technical`/`Business` membership | Verified complete | Present in `core/model` test sources |
| 3.1 Create `PlaybackErrorClassifier` | Verified complete | `core/domain/src/commonMain/kotlin/net/subsloth/core/domain/policy/PlaybackErrorClassifier.kt` exists |
| 3.2 Delete `PlayerViewModel`'s private classification methods | Verified complete | Call sites use `PlaybackErrorClassifier.classify(error)` |
| 3.3 Forward `cause` to UI message mapper for `Recoverable` | Verified complete | Present in `PlayerScreen.kt` |
| 3.4 Add `PlaybackErrorClassifierTest` | Verified complete | Present in `core/domain` test sources |
| 4.1 Verification command | Verified complete | Bookkeeping |
| 4.2 `openspec validate refactor-error-shape --strict` | Verified complete | Bookkeeping — archived |

## `2026-06-16-feature-login-gate-navigation` (9 items — all verified complete)

| Task | Disposition | Evidence |
|---|---|---|
| 1.1 Verify/create `:core:ui` | Verified complete | Module exists with `SessionGate.kt` |
| 1.2 Add `SessionGate` composable | Verified complete | `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/SessionGate.kt:47` — `fun SessionGate(sessionPort: SessionPort, login: ..., authenticated: ...)` |
| 2.1 Replace `LoginViewModel`'s stored-credentials check with `SessionPort` | Verified complete | `LoginViewModel` depends on `SessionPort` directly |
| 2.2 Update `LoginViewModelTest` with a fake `SessionPort` | Verified complete | Present in feature/auth test sources |
| 3.1 Wire `SessionGate` in `androidApp` `MainActivity` | Verified complete | `androidApp/src/main/java/net/subsloth/MainActivity.kt` calls `SessionGate(...)` |
| 3.2 Wire `SessionGate` in `desktopApp` | Verified complete | `desktopApp/src/main/kotlin/net/subsloth/desktop/Main.kt` calls `SessionGate(...)` |
| 3.3 Wire `SessionGate` in `webApp` | Verified complete | `webApp/src/wasmJsMain/kotlin/net/subsloth/web/Main.kt` calls `SessionGate(...)` |
| 4.1 Verification command | Verified complete | Bookkeeping |
| 4.2 `openspec validate feature-login-gate-navigation --strict` | Verified complete | Bookkeeping — archived |

Note: the remediation plan's gap "authenticated flow starts another login navigation stack" (owned by Change 2 / Change 5) is a distinct, currently-real defect in how the Android nav graph resumes after `SessionGate` authenticates — it is not a re-opening of this task, whose literal scope (wiring `SessionGate` into all three apps) is done.

## `2026-06-15-refactor-fc-is-harden` (17 items — all verified complete)

| Task | Disposition | Evidence |
|---|---|---|
| 1.1 Add direct `DomainError` variants for exhaustiveness | Verified complete | Superseded in practice by the `Technical`/`Business` split from `refactor-error-shape`, which achieves the same exhaustiveness goal |
| 1.2 Update `when (e: DomainError)` sites | Verified complete | Current call sites compile against the two-branch hierarchy |
| 1.3 Add sub-hierarchy matching test | Verified complete | Covered by `DomainErrorTest` |
| 2.1 Create `NetworkErrorClassifier` | Verified complete | `core/network/src/commonMain/kotlin/net/subsloth/core/network/error/NetworkErrorClassifier.kt` exists with a test |
| 2.2 Delete duplicate `isIoError` helpers | Verified complete | `grep -rn isIoError` finds only a doc-comment reference to the replaced pattern, no live duplicate helper |
| 2.3 Add `NetworkErrorClassifierTest` | Verified complete | Present alongside the classifier |
| 3.1 Add `session`/`snapshotCountSinceSave` to `PlayerUiState.Content` | Verified complete | `feature/player/.../PlayerViewModel.kt:65-66` |
| 3.2 Remove standalone `var` fields | Verified complete | No standalone fields remain; reads go through `PlayerUiState.Content` |
| 3.3 Confirm `PlayerViewModelTest` passes | Verified complete | Test suite covers this state |
| 4.1 Add `syncChannel` in `HomeViewModel` | Verified complete | `feature/catalog/src/commonMain/kotlin/net/subsloth/catalog/HomeViewModel.kt` uses `Channel(Channel.CONFLATED)` + `flatMapLatest`/`collectLatest` |
| 4.2 Replace `syncJob: Job?` with the channel collector | Verified complete | Same file |
| 4.3 `fun sync()` sends to the channel | Verified complete | Same file |
| 4.4 Remove/keep `syncChannel.close()` in `onCleared` | Verified complete | Resolved one way in the current implementation; no dangling job-cancellation code remains |
| 5.1 Add `CompletionPolicy.WATCHED_THRESHOLD` | Verified complete | `core/domain/src/commonMain/kotlin/net/subsloth/core/domain/policy/CompletionPolicy.kt:23` |
| 5.2 Replace magic `0.9` literals | Verified complete | `DownloadsViewModel.kt:80,165` and `LibraryViewModel.kt:152` all reference `CompletionPolicy.WATCHED_THRESHOLD`; no raw `0.9` comparison remains |
| 6.1 Verification command | Verified complete | Bookkeeping |
| 6.2 `openspec validate refactor-fc-is-harden --strict` | Verified complete | Bookkeeping — archived |

## `2026-06-04-library-settings-diagnostics` (2 items — still required)

| Task | Disposition | Evidence / owning change |
|---|---|---|
| 2.4 Implement TV Downloads layout, focus order, focus restoration, overscan-safe spacing, and simplified destructive actions | **Still required** | `feature/library/src/commonMain/kotlin/net/subsloth/library/DownloadsScreen.kt` has no TV-specific focus/layout/overscan code, and `TvFocusRequester`/`rememberTvFocusRequester` (`androidApp/src/main/java/net/subsloth/ui/tv/TvFocus.kt`) are used only in test recipes and the `testing/tv-focus-harness` module, never in production screens. Owned by Change 5 (`enforce-platform-test-gates`), which adds "TV focus traversal, restoration, and remote-control interaction tests that consume the existing focus rule." |
| 5.2 Run TV focus tests for Downloads after UI test infrastructure exists | **Still required** | Blocked on the same gap as 2.4. Owned by Change 5. |

## `2026-05-19-android-ui-foundation` (2 items — intentionally deferred)

| Task | Disposition | Evidence / re-review trigger |
|---|---|---|
| 4.2 Run focus harness smoke tests on a TV emulator if available | **Intentionally deferred** | CI (`.github/workflows/ci.yml`, `.github/workflows/screenshots.yml`) only boots a phone/tablet-class emulator (`api-level: 36`, `google_apis`), never a TV system image — there is no CI TV emulator "available" for this task to run on. Covered manually by `docs/testing/device-acceptance.md` §2.2/2.5. No planned change currently adds a CI Android TV emulator. Re-review if Change 5 or Change 6 introduces one. |
| 4.5 Run process-death state-restoration smoke test for the main navigation graph | **Intentionally deferred** | No automated process-death harness exists in CI; covered only by the manual checklist in `docs/testing/device-acceptance.md`. No planned change currently adds this automation. Re-review if Change 5 or Change 6 adds a state-restoration test harness. |

## `2026-07-18-verification-release` (2 items)

| Task | Disposition | Evidence / owning change |
|---|---|---|
| 4.3 Run local live drift tests only when local credentials are present | **Still required** | `ApiLiveDriftTest.kt` uses ambient environment variables (`SUBSLOTH_LOGIN`, `SUBSLOTH_PASSWORD`, `SUBSLOTH_API_BASE_URL`) gated by `assumeTrue`. This pattern is flagged as a live-test isolation gap. Owned by Change 4 (`isolate-live-drift-tests`). |
| 4.5 Run `openspec validate verification-release --strict` | Superseded | The change is archived, which requires this validation to have passed at archive time; re-running it now is bookkeeping, not a functional gap |

## `2026-05-19-catalog-details` (2 items — verified complete)

| Task | Disposition | Evidence |
|---|---|---|
| 4.2 Run Compose smoke tests for catalog and details using the focus harness _(deferred to verification-release)_ | Verified complete | Delivered in `verification-release` (task 2.4, checked) as the note promised |
| 4.4 Add Roborazzi screenshot tests for movie/series detail at phone/tablet/TV _(deferred to verification-release)_ | Verified complete | `androidApp/src/screenshotTest/kotlin/net/subsloth/screenshot/MovieDetailScreenshotTest.kt` and `SeriesDetailScreenshotTest.kt` exist with committed goldens for Phone/Tablet/TV × Light/Dark |

Caveat (not a reopening of this task, but relevant context): per `docs/testing/screenshot-tests.md`, the `screenshots.yml` workflow that runs these tests is `workflow_dispatch`-only and not wired into required PR/main CI (`ci.yml`) — so screenshot verification is not continuous today. Tracked under remediation-plan Change 6 (`automate-visual-performance-gates`).

---

## Summary

56 unchecked checkboxes found across 8 archived changes:

| Disposition | Count | Changes contributing |
|---|---:|---|
| Verified complete | 50 | `feature-session-port` (6), `refactor-error-shape` (16), `feature-login-gate-navigation` (9), `refactor-fc-is-harden` (17), `catalog-details` (2) |
| Still required | 3 | `library-settings-diagnostics` (2, owned by Change 5), `verification-release` (1, owned by Change 4) |
| Intentionally deferred | 2 | `android-ui-foundation` (2, no owning change — see re-review triggers above) |
| Superseded | 1 | `verification-release` (1) |

50 + 3 + 2 + 1 = 56.
