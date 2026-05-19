## Context

The repo contained a top-level `api/subsloth.openapi.yaml` but no Android app scaffold. The API contract is based on Media Kodi add-on source inspection plus sanitized authenticated browser observations from May 4, 2026, imported into the OpenSpec baseline.

## Goals / Non-Goals

Goals:

- Create a Kotlin Android project baseline for phone, tablet, and Android TV.
- Lock the May 2026 modern Android stack before downstream changes depend on it.
- Keep API discovery credentialed, sanitized, and limited to Kodi-compatible endpoints.
- Make OpenAPI validation and fixture tests the contract gate before DTOs and mappers are trusted.

Non-goals:

- Do not implement app UI, playback, persistence, or auth behaviour in this change.
- Do not add browser/WebView verification, scraping, comments, or non-Kodi endpoints.
- Do not run live Media tests in CI or store Media credentials in GitHub Actions.

## Decisions

### Project structure
Multi-module Android project with `:app`, `:core:*`, and `:feature:*` modules. Gives later changes clear ownership and independent verification. `:core:model` and `:core:domain` are JVM-only to keep the functional core free of Android runtime dependencies.

### Build toolchain
- **Gradle 9.5**, **AGP 9.2**, **Kotlin 2.3.21**
- **`compileSdk 36`**, **`targetSdk 36`**, **`minSdk 26`**
- **Bytecode target: Java 17** — sufficient for all Android features at this SDK level, no desugaring overhead
- **Gradle daemon: JDK 25** — latest LTS, required floor is 21 due to Metro plugin; supplied by the Nix flake
- **Compile toolchain: JDK 17** — supplied by the Nix flake via `JAVA17_HOME`; discovered by Gradle through `org.gradle.java.installations.fromEnv`

### Convention plugins (`build-logic/`)
Precompiled `.gradle.kts` script plugins under `subsloth.*` namespace eliminate repeated `compileSdk`, `minSdk`, `compileOptions`, and toolchain config from every module. The six plugins are:

| Plugin | Used by |
|---|---|
| `subsloth.android.application` | base application config |
| `subsloth.android.application.compose` | `app` (adds Compose) |
| `subsloth.android.library` | non-Compose Android modules |
| `subsloth.android.library.compose` | Compose Android modules without nav |
| `subsloth.android.feature` | `feature/*` (adds Compose BOM + Nav3 + Lifecycle) |
| `subsloth.jvm.library` | `core:model`, `core:domain` |

`org.jetbrains.kotlin.android` is not applied — Kotlin support is built into AGP 9.

### Dependency injection — Metro
**Chosen: Metro 1.0** (`dev.zacsweers.metro`) over Hilt.

Metro uses a Kotlin compiler plugin (FIR/IR); no KAPT or KSP required, so build times stay clean. API is Kotlin-first and KMP-ready. Hilt's annotation processing approach conflicts with the project's zero-KAPT principle. Metro 1.0 went stable in April 2026. No module applies Metro yet — the plugin is declared `apply false` in the root build and will be activated when DI is needed in `core-domain-network`.

### Navigation — Navigation3
**Chosen: Navigation3 1.1** (`androidx.navigation3`) over Navigation Compose 2.x.

Navigation3 went stable (1.0.0) in November 2025 and 1.1.0 in April 2026. It is Compose-first with direct state ownership, no `NavController` boilerplate, and integrates with `lifecycle-viewmodel-navigation3`. All feature modules carry these deps via the `subsloth.android.feature` convention plugin.

### JSON serialization — kotlinx.serialization
**Chosen: `kotlinx.serialization` 1.9** over Moshi.

Moshi is absent from the entire dependency graph. Media DTOs are handwritten `@Serializable` / `@SerialName` models in `:core:network`, and Retrofit 3 ships a first-party `converter-kotlinx-serialization` converter. kotlinx.serialization is the de-facto standard across the Kotlin ecosystem in 2026.

### HTTP — Retrofit 3
**Chosen: `com.squareup.retrofit2:retrofit:3.0.0`** (released May 2025).

Despite the `retrofit2` Maven group (kept for binary compatibility), this is the Retrofit 3 line. Paired with `converter-kotlinx-serialization` and OkHttp 5.

### Error modelling — Kotlin stdlib types
No Arrow. Domain and network errors use `kotlin.Result<T>`, `sealed interface`, and direct exceptions. Arrow was removed from every module in this change; the functional programming patterns it offered are not needed at this layer.

### Testing — JUnit 4 + Truth + Turbine + Roborazzi
**Test runner: JUnit 4** (`junit:junit:4.13.2`) — the Android ecosystem baseline; Roborazzi's `@RunWith(AndroidJUnit4)` and AndroidX test infrastructure are built on JUnit 4.

**Assertions: Truth** (`com.google.truth:truth`) — matches the Now in Android reference stack. Kotest was removed entirely.

**Flow testing: Turbine** — unchanged.

**Screenshot tests: Roborazzi 1.60** — scaffolded for later screenshot test implementation.

`core:network` uses JUnit 4 `@Before` + `Assume.assumeTrue` to gate live drift tests behind `SUBSLOTH_LOGIN`/`SUBSLOTH_PASSWORD` env vars.

### TV libraries — stable
Both `androidx.tv:tv-foundation:1.0.0` and `androidx.tv:tv-material:1.1.0` reached stable on 6 May 2026. The previous release-candidate exception for `tv-foundation` is closed.

### OpenAPI generator
OpenAPI validation stays in `:core:network`, but DTOs are handwritten from the observed Media contract. Stream/download URLs are present in live models but must never be persisted (enforced by the API contract spec and the secret-scanning policy).

## Risks / Trade-offs

- Handwritten DTOs can drift if discovery changes faster than the fixtures. The gate is OpenAPI validation and fixture tests, not generated code.
- Metro 1.0 requires JDK 21+ for the Gradle plugin at build time. The Nix flake provides JDK 25 which satisfies this. Any CI runner must also have a JDK 21+ available.
- Navigation3 1.1 is production-stable but younger than Navigation Compose. The API may evolve more in 1.2+. Convention plugin isolation means a migration would touch build-logic and module build files, not business logic.

## Migration Plan

1. Scaffold the Android project and modules.
2. Establish the convention plugin build-logic.
3. Preserve `api/subsloth.openapi.yaml` as the initial contract.
4. Add sanitized fixture policy and offline schema tests.
5. Add optional local live drift tests gated by `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD`.
6. Archive this change after all tasks and verification commands pass.

## Open Questions

- Exact Media JSON field nullability and media URL shapes are confirmed by live drift tests only; offline fixtures may not cover all edge cases.
- Additional Media schema edge cases should be captured in fixtures before new DTO fields are added.
