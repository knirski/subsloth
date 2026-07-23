## Context

Change 0 (`reconcile-readiness-baseline`) established one non-contradictory baseline for release/toolchain/platform-readiness claims. This change (Change 1 in `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`) implements the architecture-boundary decisions that baseline adopted (decisions 3-4), and is a dependency for Changes 2 (Android runtime), 3A (Desktop runtime), and 3B (Web runtime), which are expected to reuse the composition contracts this change establishes rather than inventing their own.

## Goals / Non-Goals

Goals:

- `:core:network` owns HTTP transport and DTO-to-domain mapping only.
- A new `:core:data` module owns repository/orchestration classes that combine transport, persistence, and preferences behind domain ports.
- Feature modules depend only on `:core:model`, `:core:domain` (ports), and shared UI (`:core:ui`, `:core:media`) — never on `:core:network`, `:core:database`, `:core:preferences`, or `:core:data` directly. `:core:media` is a shared playback/UI-bridging module (not a concrete IO adapter) and stays a permitted direct dependency, e.g. for `:feature:player`.
- The allowed dependency graph is enforced by an executable Gradle-graph test, not documentation or a source-import regex scan.
- `:webApp` receives the same Spotless/Detekt policy as every other module.
- Composition-root responsibility is documented.

Non-Goals:

- Do not wire Desktop or Web composition roots to real adapters — that's Changes 3A and 3B. This change only makes the domain-facing contracts available for them to consume; it does not implement Desktop/Web session, credential, or navigation wiring.
- Do not touch Android's authentication flow, ViewModel wiring, or navigation-restart bug — that's Change 2.
- Do not add iOS targets. The architecture verification commands explicitly cover only the configured JVM and Wasm targets; `platform-parity`'s iOS exemption is unaffected.
- Do not change `NetworkPolicyTest` (Ktor plugin wiring, reflection-based endpoint/field checks) — it tests network policy, not module boundaries, and stays as-is.
- Do not restructure `:core:media`'s existing dependency on `:core:database`/`:core:preferences` (in `androidMain`) — the assessment and remediation plan did not flag `:core:media` as a boundary violation, and it is Android-only wiring for downloads/playback persistence, not a feature module. Out of scope unless a later review finds otherwise.

## Decisions

- **New module: `:core:data`.** Uses the `subsloth.kmp.library` convention (same as `:core:network`/`:core:domain`/`:core:preferences` — pure Kotlin, no Android target required since Room 3.0 KMP DAOs are already consumed from `commonMain` today inside `:core:network`). Depends on `:core:model`, `:core:domain`, `:core:network`, `:core:database`, `:core:preferences`. `CatalogRepository` (and its existing tests) move here unchanged in behavior — only the module and package location change (package stays `net.subsloth.core.network.media` → moves to a `:core:data`-owned package, e.g. `net.subsloth.core.data.media`, to avoid implying it's still network-owned).
- **`:core:network` shrinks to transport only.** After the move, `core/network/build.gradle.kts` drops `project(":core:database")` and `project(":core:preferences")`. Remaining contents: `Api`, `ClientFactory`, `CatalogPortAdapter` (already a clean network-only adapter per the research — no change needed), DTO types, and mappers.
- **UI error mapping moves to `:core:ui`.** `UiErrorMapping.kt`'s `toUiError()` functions are UI-facing (they translate `DomainError`/`Throwable` into user-displayable state), so per the architecture decision ("keep UI error mapping in UI-facing modules") they belong in `:core:ui`, which every feature already depends on. `feature/details` updates its import; no behavior change.
- **`feature/auth` stops importing `UserPreferences`.** `LoginViewModel` only needs `UserPreferences.DEFAULT_API_BASE_URL` as a constant default. Move that constant to `:core:domain` (a `LoginDefaults` object or similar, alongside other domain-level constants like `CompletionPolicy`), and have `:core:preferences`'s `UserPreferences` reference it from there instead of the reverse. This removes `feature/auth`'s only concrete-adapter import without changing behavior.
- **Remove now-provably-unused feature→adapter build-graph edges.** `feature/catalog`, `feature/details` (after the `toUiError` move), `feature/player` drop `project(":core:network")`; `feature/library` and `feature/settings` drop `project(":core:database")`/`project(":core:preferences")`. These are safe because grep found zero source references inside each module to the corresponding concrete package — the edges are currently dead weight the dependency-graph invariant test (below) would otherwise need to special-case. Each removal is verified by a full compile + test of that module before moving to the next.
- **`:core:model`'s Compose dependency: attempt removal via stability configuration file, with a documented fallback.** The 17 usages are annotation-only (`@Stable`/`@Immutable` on data classes); no actual Compose UI/state/runtime types are used. Compose Multiplatform supports marking external classes as stable via a `stabilityConfigurationFile` consumed by the Compose compiler plugin in *consuming* modules, without the annotated module needing a compile-time Compose dependency. Plan: remove `api(libs.compose.runtime)` and the 17 annotations from `:core:model`; add a checked-in stability list (e.g. `compose-stability.conf` at repo root, one fully-qualified class name per line) and reference it via `composeCompiler { stabilityConfigurationFiles.add(...) }` in every module that already applies the Compose compiler plugin (`:core:ui`, `feature:*`, `androidApp`, `desktopApp`, `webApp`). If this does not produce equivalent recomposition-skipping behavior (verified by the existing Compose UI tests and, where available, a recomposition-count test), revert `:core:model`'s dependency and instead add a narrow, justified exception to the `architecture` spec's "Functional Core Boundary" requirement — the plan explicitly allows this fallback, and it must not block the rest of this change.
- **Dependency-graph invariant test lives in a shared test-support location**, not inside any one module, since it must inspect multiple modules' resolved configurations. Implemented as a Gradle-API-driven JUnit test (likely a new `:testing:architecture-rules` module or an addition to an existing `:testing:*` module) that walks `project(":feature:*").configurations.getByName("commonMainImplementation")` (or the resolved compile classpath) and asserts no forbidden module appears, plus an equivalent check for `:core:network`. A fixture/mutation test (a throwaway module or a test-only Gradle project mutation) proves the invariant actually fails on a forbidden edge, per the plan's verification requirement.
- **New `subsloth.web.library` convention plugin** mirrors `subsloth.jvm.library`'s Spotless/Detekt configuration, adapted for `:webApp`'s Wasm/Compose-for-Web target set. `webApp/build.gradle.kts` applies it instead of raw `kotlin("multiplatform")` + raw plugin aliases; the Kotlin serialization/Compose plugin aliases it currently applies directly stay (matching the existing pattern where `feature/*` modules apply a convention plugin *and* the Compose plugin alias side-by-side).
- **Composition-root ownership doc** goes at `docs/architecture/composition-roots.md`, referenced from `docs/agent/README.md`'s routing table. Documents: Android's `AppContainer` as the reference manual composition root; that Desktop/Web currently have none and fall back to `RootContainerViewModel`'s in-memory `SessionPort` (explicitly a non-production default per its own doc comment); and that building real Desktop/Web composition roots is Changes 3A/3B's scope, which should follow the same "construct concrete adapters at the composition root, inject ports into ViewModels" pattern `AppContainer` already demonstrates.

## Risks / Trade-offs

- Moving `CatalogRepository` touches every call site that constructs it (`AppContainer.kt` today; any test fixtures). Mitigated by moving the file with its package renamed in one commit and updating all references before running the full test suite, rather than a partial move.
- The Compose-stability-config removal is genuinely uncertain until tried — Compose Multiplatform's `stabilityConfigurationFile` support and exact DSL surface can vary by Compose compiler version. The explicit fallback (keep the dependency, document a narrow spec exception) means a failed attempt here does not block or revert the rest of the change.
- Removing feature→adapter build-graph edges based on "zero source references today" could regress if a feature's DI wiring (not caught by the source-import grep) implicitly needs the transitive classpath. Each removal is verified by a targeted compile (`./gradlew :feature:X:compileKotlinJvm` or equivalent) immediately after, not batched.

## Migration Plan

1. Add `:core:data`, move `CatalogRepository` and its tests, wire `AppContainer` to the new location.
2. Shrink `:core:network`'s dependencies; move `UiErrorMapping.kt` to `:core:ui`; update `feature/details`.
3. Move the default-base-URL constant out of `:core:preferences` into `:core:domain`; update `feature/auth`.
4. Remove unused feature→adapter build-graph edges one module at a time, compiling after each.
5. Attempt the `:core:model` Compose-dependency removal; fall back to a documented spec exception if it doesn't hold up.
6. Add the Gradle dependency-graph invariant test and its forbidden-edge fixture/mutation proof.
7. Add `subsloth.web.library` and migrate `:webApp` to it.
8. Write the composition-root ownership doc and link it from `docs/agent/README.md`.
9. Update `openspec/specs/project/spec.md`'s module lists (via this change's delta spec, promoted on archive).

## Open Questions

- Exact location/module for the dependency-graph invariant test (new `:testing:architecture-rules` module vs. an addition to an existing `:testing:*` module) is an implementation-time call — either satisfies the requirement; prefer reusing an existing module if one already has Gradle-API test infrastructure, to avoid adding a module for a single test class.
