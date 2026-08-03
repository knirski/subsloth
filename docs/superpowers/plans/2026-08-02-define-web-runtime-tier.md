# Define Web Runtime Tier Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the GitHub Pages Wasm deployment an explicit, credential-free, mock-backed demo with executable browser evidence and documented promotion gates.

**Architecture:** Keep the decision at the Web composition root. A `WebRuntimeMode.Demo` entrypoint initializes the existing Wasm mock client, renders the demo navigation and an accessible demo banner, and never constructs the session/login/credential-storage path. Shared feature ViewModels receive only deterministic mock-backed closures or remain visibly unsupported; no live transport or backend is introduced.

**Tech Stack:** Kotlin/WasmJS, Compose Multiplatform, Ktor `MockEngine`, Navigation3, Kotlin/Wasm browser tests, GitHub Pages, OpenSpec.

## Global Constraints

- GitHub Pages SHALL remain a `Stateless demo`; it SHALL NOT be described as an authenticated production client.
- Demo startup SHALL set the mock transport before constructing feature dependencies and SHALL make no live Media API request.
- Demo mode SHALL not construct `SessionGate`, `LoginViewModel`, the Web `CredentialStore`, or any credential persistence adapter.
- Demo mode SHALL not read, decrypt, refresh, or use the known Web credential keys `subsloth_credentials_data` and `subsloth_credentials_key`.
- No backend, reverse proxy, OAuth flow, CORS configuration, or authenticated Web runtime is part of this change.
- Web code in `wasmJsMain` must remain portable Kotlin/Wasm code and must not use JVM, Android, or Java APIs.
- Run Gradle only through `./gradlew` inside the pinned Nix environment.

---

## File Map

| File | Responsibility |
|---|---|
| `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebRuntimeMode.kt` | Web-owned immutable runtime mode and demo-only startup policy. |
| `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebDemoBanner.kt` | Accessible persistent Demo Mode label and explanatory copy. |
| `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebDemoRuntime.kt` | Mock-backed demo dependencies and deterministic feature callbacks. |
| `webApp/src/wasmJsMain/kotlin/net/subsloth/web/Main.kt` | Demo composition root; no session/login construction. |
| `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebNavHost.kt` | Demo navigation starts at `CatalogKey` and consumes demo dependencies. |
| `webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt` | Browser-runtime tests for mode, banner, mock-only behavior, and storage boundary. |
| `webApp/build.gradle.kts` | Wasm browser-test dependencies/source-set configuration if the convention does not provide them. |
| `.github/workflows/pages.yml` | Deployment step names and explicit demo-mode build documentation. |
| `docs/production-deployment.md` | Pages demo status and future authenticated-promotion requirements. |
| `docs/readiness/platform-support-matrix.md` | Evidence links for demo labelling and credential boundary. |

## Implementation Tasks

### Task 1: Add the explicit demo runtime contract

**Files:**
- Create: `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebRuntimeMode.kt`
- Create: `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebDemoBanner.kt`
- Test: `webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt`

**Interfaces:**
- Produces `enum class WebRuntimeMode { Demo }` and `const val DEMO_CREDENTIAL_DATA_KEY = "subsloth_credentials_data"` plus the matching key constant.
- Produces `@Composable fun WebDemoBanner(modifier: Modifier = Modifier)` with visible text containing `Demo mode`, `sample data`, and `credentials`.

- [ ] **Step 1: Write the failing contract test**

  Add a Kotlin/Wasm test that asserts `WebRuntimeMode.Demo` is the only public mode and that the two credential-key constants match the keys used by the existing Wasm credential implementation. Add a Compose UI test for `WebDemoBanner` if the configured browser-test framework supports Compose semantics; otherwise expose a pure `DEMO_BANNER_TEXT` value and assert its required phrases directly.

- [ ] **Step 2: Run the focused test to verify it fails**

  Run:

  ```bash
  ./gradlew :webApp:wasmJsBrowserTest --tests 'net.subsloth.web.WebRuntimeModeTest'
  ```

  Expected: the task fails because the runtime mode, key constants, and banner do not yet exist.

- [ ] **Step 3: Implement the smallest contract**

  Define the single demo mode and render the banner as ordinary Compose text. Do not add a production mode, a runtime toggle, or credential-access code.

- [ ] **Step 4: Run the focused test to verify it passes**

  Run the same focused Gradle task and expect the test to pass.

- [ ] **Step 5: Commit the focused unit**

  ```bash
  git add webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebRuntimeMode.kt \
    webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebDemoBanner.kt \
    webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt
  git commit -m "feat: define web demo runtime"
  ```

### Task 2: Build a deterministic mock-backed Web runtime

**Files:**
- Create: `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebDemoRuntime.kt`
- Modify: `webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebNavHost.kt`
- Test: `webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt`

**Interfaces:**
- Produces a `WebDemoRuntime` value containing the callbacks required by the catalog and detail ViewModels.
- Consumes `ClientFactory.create()` after `ClientConfig.useMock = true` and the existing `Api`/`Mapper` boundary.
- Does not construct `CredentialStore`, `CredentialsStoreAdapter`, `SessionGate`, or `RootContainerViewModel`.

- [ ] **Step 1: Write the failing mock-startup test**

  Add a test for a `createWebDemoRuntime()` factory that sets `ClientConfig.useMock = true`, creates the mock `Api`, and exposes a deterministic catalog callback. Assert that the callback returns the mock catalog content and that it does not require login or password arguments.

- [ ] **Step 2: Run the focused test to verify it fails**

  ```bash
  ./gradlew :webApp:wasmJsBrowserTest --tests 'net.subsloth.web.WebRuntimeModeTest.demoRuntimeUsesMockTransport'
  ```

  Expected: failure because the demo runtime factory is not present.

- [ ] **Step 3: Implement the demo runtime**

  Create the mock `HttpClient` through the existing `ClientFactory` and wrap it in `Api`. Map mock DTOs with the existing `Mapper`; keep any cache-like flows in Web-owned in-memory state. Pass the resulting closures into `HomeViewModel`, `MovieDetailViewModel`, and `ShowDetailViewModel` instead of relying on their empty default callbacks. Keep unsupported library/download/settings/player actions on their existing safe empty states and label them as demo behavior where visible.

- [ ] **Step 4: Make navigation consume the runtime**

  Change `WebNavHost` to start at `CatalogKey`, accept a `WebDemoRuntime` parameter, and pass its catalog/detail callbacks to feature ViewModels. Remove the empty `LoginKey` entry from the demo path. Keep auth-repair navigation unreachable from Demo mode.

- [ ] **Step 5: Run the focused test to verify it passes**

  Run the focused browser test and confirm that the mock callback returns data without any credential storage access.

- [ ] **Step 6: Commit the runtime unit**

  ```bash
  git add webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebDemoRuntime.kt \
    webApp/src/wasmJsMain/kotlin/net/subsloth/web/WebNavHost.kt \
    webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt
  git commit -m "feat: wire mock-backed web demo"
  ```

### Task 3: Make the Pages entrypoint credential-free

**Files:**
- Modify: `webApp/src/wasmJsMain/kotlin/net/subsloth/web/Main.kt`
- Modify: `webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt`

**Interfaces:**
- `main()` calls a Demo-only composition root that initializes `ClientConfig.useMock` before `WebDemoRuntime`.
- The composition renders `WebDemoBanner` and `WebNavHost(runtime = ...)` directly.

- [ ] **Step 1: Write the failing startup/storage tests**

  Add browser tests that start the Demo composition with empty storage and with pre-seeded `subsloth_credentials_data` and `subsloth_credentials_key`. Assert that the Demo UI is reachable without a login form and that the seeded values are not read, changed, or refreshed.

- [ ] **Step 2: Run the focused tests to verify they fail**

  ```bash
  ./gradlew :webApp:wasmJsBrowserTest --tests 'net.subsloth.web.WebRuntimeModeTest.demoStartupDoesNotUseCredentialStorage'
  ```

  Expected: failure because `Main.kt` still constructs `RootContainerViewModel` and `SessionGate`.

- [ ] **Step 3: Replace the current composition root**

  Remove the `RootContainerViewModel`, `SessionGate`, `LoginViewModel`, and `LoginScreen` path from `Main.kt`. Set `ClientConfig.useMock = true` before creating `WebDemoRuntime`, then render the banner and demo navigation. Do not call `CredentialStore` or `localStorage` from the new composition.

- [ ] **Step 4: Run the focused tests to verify they pass**

  Run the focused browser tests and verify the seeded credential keys remain unchanged and no login semantics appear in the Demo surface.

- [ ] **Step 5: Commit the composition-root unit**

  ```bash
  git add webApp/src/wasmJsMain/kotlin/net/subsloth/web/Main.kt \
    webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt
  git commit -m "fix: keep pages demo free of credentials"
  ```

### Task 4: Add and run meaningful Wasm browser coverage

**Files:**
- Modify: `webApp/build.gradle.kts`
- Create or modify: `webApp/src/wasmJsTest/kotlin/net/subsloth/web/WebRuntimeModeTest.kt`
- Modify: `.github/actions/kmp-setup/action.yml` only if the existing browser-test setup is insufficient

**Interfaces:**
- Browser tests execute through the existing `wasmJsBrowserTest` task and use the repository's configured browser runner.
- Tests must be offline and use the bundled mock client; they must not receive Media credentials.

- [ ] **Step 1: Configure only the missing browser-test dependencies**

  Inspect the generated `wasmJsBrowserTest` task before editing Gradle. If the convention already supplies the test framework, add only `wasmJsTest` dependencies required for Kotlin tests and Compose UI assertions. Do not add a live network dependency or a new browser service.

- [ ] **Step 2: Add the three required browser assertions**

  Cover: visible Demo mode text; mock-only startup/catalog data; and unchanged absence or contents of both known credential keys when storage is empty or pre-seeded.

- [ ] **Step 3: Run the Web browser task**

  ```bash
  ./gradlew :webApp:wasmJsBrowserTest
  ```

  Expected: all Web tests pass with no credentials and no live Media network access.

- [ ] **Step 4: Commit the browser-test unit**

  ```bash
  git add webApp/build.gradle.kts webApp/src/wasmJsTest .github/actions/kmp-setup/action.yml
  git commit -m "test: cover web demo runtime"
  ```

### Task 5: Reconcile deployment and readiness documentation

**Files:**
- Modify: `.github/workflows/pages.yml`
- Modify: `docs/production-deployment.md`
- Modify: `docs/readiness/platform-support-matrix.md`
- Modify: `openspec/changes/define-web-runtime-tier/tasks.md`

**Interfaces:**
- Documentation names the deployed Pages site `Stateless demo` and links authenticated promotion requirements to the active OpenSpec change.
- The Pages workflow does not accept or inject Media credentials.

- [ ] **Step 1: Update the Pages workflow copy**

  Rename the build step to state that it builds the credential-free Demo runtime and add a comment or assertion showing that no `SUBSLOTH_*` secret is passed to the job.

- [ ] **Step 2: Document the deployment boundary**

  State that Pages serves mock sample data, does not request or store credentials, and is not an authenticated production client. Keep the existing COOP/COEP guidance as a requirement for any future deployment claiming OPFS persistence.

- [ ] **Step 3: Update the readiness evidence**

  Replace the current “no automated check yet” entries for Demo labelling and credential absence with links to the Web browser test and Pages workflow. Leave CORS, authenticated API architecture, and production persistence gates open under the future authenticated Web tier.

- [ ] **Step 4: Run documentation/spec validation**

  ```bash
  openspec validate define-web-runtime-tier --strict
  openspec validate --all --strict
  git diff --check
  ```

- [ ] **Step 5: Commit the documentation unit**

  ```bash
  git add .github/workflows/pages.yml docs/production-deployment.md \
    docs/readiness/platform-support-matrix.md \
    openspec/changes/define-web-runtime-tier/tasks.md
  git commit -m "docs: define github pages web demo"
  ```

### Task 6: Run the repository verification suite

**Files:**
- No new files; verify all files from Tasks 1–5.

- [ ] **Step 1: Format and static analysis**

  ```bash
  ./gradlew spotlessApply spotlessCheck detekt
  ```

- [ ] **Step 2: Compile the KMP core targets**

  ```bash
  ./gradlew :core:model:compileKotlinJvm :core:domain:compileKotlinJvm \
    :core:model:compileKotlinWasmJs :core:domain:compileKotlinWasmJs
  ```

- [ ] **Step 3: Run Web and aggregate tests**

  ```bash
  ./gradlew :webApp:wasmJsBrowserTest test
  ```

- [ ] **Step 4: Validate the OpenSpec change and all canonical specs**

  ```bash
  openspec validate define-web-runtime-tier --strict
  openspec validate --all --strict
  ```

- [ ] **Step 5: Inspect the final diff and status**

  ```bash
  git diff --check
  git status --short --branch
  ```

  Confirm the only changes are the active Web runtime change, implementation, tests, and documentation; do not touch the unrelated `.claude/` directory in the main checkout.
