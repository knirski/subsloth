## 1. Create `:core:data` and move `CatalogRepository`

- [x] 1.1 Add `include(":core:data")` to `settings.gradle.kts`.
- [x] 1.2 Create `core/data/build.gradle.kts` using the `subsloth.kmp.library` convention, depending on `:core:model`, `:core:domain`, `:core:network`, `:core:database`, `:core:preferences`.
- [x] 1.3 Move `core/network/src/commonMain/kotlin/net/subsloth/core/network/media/CatalogRepository.kt` to `core/data/src/commonMain/kotlin/net/subsloth/core/data/media/CatalogRepository.kt` (package rename `net.subsloth.core.network.media` → `net.subsloth.core.data.media`), and its test file to the equivalent `core/data` test source set.
- [x] 1.4 Update `androidApp/src/main/java/net/subsloth/AppContainer.kt`'s import and `:androidApp` `build.gradle.kts` to depend on `:core:data` for `CatalogRepository`.
- [x] 1.5 `./gradlew :core:data:compileKotlinJvm :core:data:jvmTest :androidApp:assembleDebug`

## 2. Shrink `:core:network` to transport only

- [x] 2.1 Remove `implementation(project(":core:database"))` and `implementation(project(":core:preferences"))` from `core/network/build.gradle.kts`.
- [x] 2.2 `./gradlew :core:network:compileKotlinJvm :core:network:jvmTest` — confirm it compiles with no remaining reference to `:core:database`/`:core:preferences` types.

## 3. Move UI error mapping to `:core:ui`

- [x] 3.1 Move `core/network/src/commonMain/kotlin/net/subsloth/core/network/error/UiErrorMapping.kt` to `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/error/UiErrorMapping.kt` (adjust package).
- [x] 3.2 Update `feature/details/src/commonMain/kotlin/net/subsloth/details/DetailViewModels.kt:24`'s import.
- [x] 3.3 `./gradlew :core:ui:compileKotlinJvm :feature:details:compileKotlinJvm :feature:details:jvmTest`

## 4. Remove `feature/auth`'s concrete `UserPreferences` import

- [x] 4.1 Move the default API base URL constant (`UserPreferences.DEFAULT_API_BASE_URL`) to a `:core:domain` object (e.g. `net.subsloth.core.domain.LoginDefaults`), and have `:core:preferences`'s `UserPreferences` reference it from there.
- [x] 4.2 Update `feature/auth/src/commonMain/kotlin/net/subsloth/auth/LoginViewModel.kt:22,58,65` to import the new `:core:domain` constant instead of `net.subsloth.preferences.UserPreferences`.
- [x] 4.3 `./gradlew :core:domain:compileKotlinJvm :core:preferences:compileKotlinJvm :feature:auth:compileKotlinJvm :feature:auth:jvmTest`

## 5. Remove unused feature→adapter build-graph edges

- [x] 5.1 Remove `project(":core:network")` from `feature/catalog/build.gradle.kts`; `./gradlew :feature:catalog:compileKotlinJvm :feature:catalog:jvmTest`.
- [x] 5.2 Remove `project(":core:network")` from `feature/details/build.gradle.kts` (after task 3); `./gradlew :feature:details:compileKotlinJvm :feature:details:jvmTest`.
- [x] 5.3 Remove `project(":core:network")` from `feature/player/build.gradle.kts`; `./gradlew :feature:player:compileKotlinJvm :feature:player:jvmTest`.
- [x] 5.4 Remove `project(":core:database")` and `project(":core:preferences")` from `feature/library/build.gradle.kts`; `./gradlew :feature:library:compileKotlinJvm :feature:library:jvmTest`.
- [x] 5.5 Remove `project(":core:preferences")` from `feature/settings/build.gradle.kts` (after task 4 confirms `feature/auth` is the only consumer needing the moved constant); `./gradlew :feature:settings:compileKotlinJvm :feature:settings:jvmTest`.

## 6. Attempt `:core:model`'s Compose-dependency removal

- [x] 6.1 Add a checked-in Compose stability configuration file (e.g. `compose-stability.conf`) listing the fully-qualified class names of the 17 `@Stable`/`@Immutable` classes in `core/model/src/commonMain`.
- [x] 6.2 Reference the stability file via `composeCompiler { stabilityConfigurationFile.set(...) }` in every module that already applies the Compose compiler plugin (`:core:ui`, `feature:*`, `:androidApp`, `:desktopApp`, `:webApp`).
- [x] 6.3 Remove `@Stable`/`@Immutable` annotations from the 17 files in `core/model/src/commonMain` and remove `api(libs.compose.runtime)` from `core/model/build.gradle.kts`.
- [x] 6.4 `./gradlew :core:model:compileKotlinJvm :core:model:compileKotlinWasmJs test` — confirm the full suite passes and no module needing Compose stability regresses.
- [x] 6.5 **If 6.1-6.4 do not hold up** (compile failure, or verified recomposition-skipping regression): revert this section's changes and instead add a narrow, justified exception for `:core:model`'s Compose dependency to `openspec/specs/architecture/spec.md`'s "Functional Core Boundary" requirement text in this change's delta spec, with a one-line rationale referencing what was tried and why it didn't hold up.

## 7. Add the dependency-graph invariant test

- [x] 7.1 Add a Gradle-API-driven JUnit test (in an existing or new `:testing:*` module) that resolves each `:feature:*` module's dependency configurations and fails if `:core:network`, `:core:database`, `:core:preferences`, or `:core:data` appears (`:core:media` is a permitted feature dependency — see design.md); and resolves `:core:network`'s configurations and fails if `:core:database` or `:core:preferences` appears.
- [x] 7.2 Add a fixture or test mutation that introduces a forbidden edge and proves the new test fails on it (then remove the mutation).
- [x] 7.3 `./gradlew :testing:<module>:test` (module name per task 7.1's placement).

## 8. `:webApp` convention-plugin parity

- [x] 8.1 Create `build-logic/convention/src/main/kotlin/subsloth.web.library.gradle.kts`, mirroring `subsloth.jvm.library.gradle.kts`'s Spotless/Detekt configuration for `:webApp`'s Wasm/Compose-for-Web target.
- [x] 8.2 Update `webApp/build.gradle.kts` to apply `subsloth.web.library` instead of raw `kotlin("multiplatform")`, keeping the Kotlin serialization/Compose plugin aliases already applied directly (matching the existing `feature/*` pattern of convention plugin + Compose plugin alias side-by-side).
- [x] 8.3 `./gradlew :webApp:spotlessCheck :webApp:detekt :webApp:compileKotlinWasmJs`

## 9. Document composition-root ownership

- [x] 9.1 Write `docs/architecture/composition-roots.md`: Android's `AppContainer` as the reference composition root (what it constructs, where); Desktop/Web's current lack of one and their fallback to `RootContainerViewModel`'s in-memory `SessionPort` default (explicitly non-production); note that Changes 3A/3B build the real Desktop/Web composition roots following this same pattern.
- [x] 9.2 Add `docs/architecture/composition-roots.md` to `docs/agent/README.md`'s routing table.

## 10. Spec and verification

- [x] 10.1 Write `specs/architecture/spec.md` and `specs/project/spec.md` deltas (adjust the Compose-dependency requirement text per task 6.5's outcome if the removal didn't hold up).
- [x] 10.2 `openspec validate enforce-architecture-boundaries --strict`
- [x] 10.3 `./gradlew spotlessApply spotlessCheck detekt`
- [x] 10.4 `./gradlew :core:model:compileKotlinJvm :core:domain:compileKotlinJvm`
- [x] 10.5 `./gradlew :core:model:compileKotlinWasmJs :core:domain:compileKotlinWasmJs`
- [x] 10.6 `./gradlew test`
- [x] 10.7 `openspec validate --all --strict`
