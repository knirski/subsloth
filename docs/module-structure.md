# Module Structure

This document maps all 24 modules in the project, their responsibilities, dependency relationships, and the convention plugin each uses.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             Application Shells                              │
├───────────────────┬────────────────────┬────────────────────────────────────┤
│   :androidApp     │    :desktopApp     │            :webApp                 │
│   Android APK     │    JVM desktop     │            WasmJS                  │
│   AndroidX Compose│    CMP Compose     │            CMP Compose             │
└────────┬──────────┴─────────┬──────────┴────────────────┬───────────────────┘
         │                    │                            │
         └──────────┬─────────┴──────────┬─────────────────┘
                    │                    │
         ┌──────────▼────────────────────▼───────────────────────────────────┐
         │                         Feature Modules                           │
         ├──────────┬──────────┬──────────┬──────────┬──────────┬────────────┤
         │:feature: │:feature: │:feature: │:feature: │:feature: │:feature:   │
         │  auth    │ catalog  │ details  │  player  │ library  │  settings  │
         └──────────┴──────────┴──────────┴──────────┴──────────┴────────────┘
                                │
         ┌──────────────────────▼────────────────────────────────────────────┐
         │                          Core Modules                             │
         ├──────────┬──────────┬──────────┬──────────┬──────────┬────────────┤
         │:core:    │:core:    │:core:    │:core:    │:core:    │:core:      │
         │  model   │  domain  │ network  │ database │preferences│  media    │
         │          │          │          │          │          │           │
         │  ─ ADTs  │  ─ Ports │  ─ Ktor  │  ─ Room  │ ─ DataSt.│ ─ Playback│
         │  ─ Value │  ─ Use   │  ─ DTOs  │  ─ DAOs  │ ─ Keyst.│ ─ Download│
         │  ─ Types │  Cases   │  ─ Api   │          │          │           │
         └──────────┴──────────┴──────────┴──────────┴──────────┴────────────┘
         Also `:core:data` — repository/orchestration layer combining transport,
         persistence, and preferences (depends on network + database + preferences;
         see the Core Modules table below).
                                │
         ┌──────────────────────▼────────────────────────────────────────────┐
         │                           Core UI                                 │
         │                     :core:ui                                       │
         │          Shared Compose components, theming, nav keys             │
         └────────────────────────────────────────────────────────────────────┘
                                │
         ┌──────────────────────▼────────────────────────────────────────────┐
         │                        Testing Modules                            │
         ├──────────────┬───────────┬──────────────┬───────────┬─────────────┤
         │:testing:     │:testing:  │:testing:     │:testing:  │:testing:    │
         │ api-contract │assertions │ detekt-rules │ mock-api  │tv-focus-    │
         │              │           │              │           │ harness     │
         └──────────────┴───────────┴──────────────┴───────────┴─────────────┘
         Also `:testing:architecture-rules` — Gradle dependency-graph invariant
         test (see the Testing Modules table below).
                                │
         ┌──────────────────────▼────────────────────────────────────────────┐
         │                          Benchmark                                │
         │                      :benchmark                                    │
         │           Macrobenchmarks + baseline profile generation           │
         └────────────────────────────────────────────────────────────────────┘
```

---

## Application Shells

| Module | Convention Plugin | Targets | Description |
|---|---|---|---|
| `:androidApp` | `subsloth.android.application.compose` | Android | APK entry point. Uses AndroidX Compose BOM (not CMP) for TV foundation, adaptive layouts. Wires all features together. |
| `:desktopApp` | `subsloth.jvm.library` + manual Compose config | JVM | Desktop entry point via `compose.desktop.application`. Same feature set as Android, but Desktop-first Compose. |
| `:webApp` | `subsloth.web.library` + Compose | WasmJS | Browser entry point via Compose for Web. |

**Dependency rule:** Shells depend on all feature and core modules. They must not be depended upon by any other module.

---

## Feature Modules

| Module | Convention Plugin | Depends On | Description |
|---|---|---|---|
| `:feature:auth` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain`, `:core:ui` | Login, logout, credential management, profile selection |
| `:feature:catalog` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain` | Home screen, search, filters, sort |
| `:feature:details` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain`, `:core:ui` | Movie/series detail views, episode lists |
| `:feature:player` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain`, `:core:media` | Playback UI, controls, subtitles, quality |
| `:feature:library` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain` | Library, downloads list, storage management |
| `:feature:settings` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain` | Settings, diagnostics, about |

**Dependency rule:** Features depend only on `:core:model`, `:core:domain` (for domain ports), and shared UI modules (`:core:ui`, `:core:media`) — never on `:core:network`, `:core:database`, `:core:preferences`, or `:core:data` directly. Concrete adapter instances are constructed only at each platform's composition root and injected into feature ViewModels through domain port constructor parameters (see [composition-root ownership](architecture/composition-roots.md)). This is enforced by an executable Gradle dependency-graph test, not just convention — see `:testing:architecture-rules` below. Features do not depend on other features.

---

## Core Modules

| Module | Convention Plugin | Depends On | Targets | Description |
|---|---|---|---|---|
| `:core:model` | `subsloth.kmp.library` | none | JVM, WasmJS | Pure domain types: sealed ADTs, value objects, enums. No explicit project dependencies, and no Compose runtime dependency — relies on Kotlin stdlib and convention-provided libraries (immutable collections, kermit). Compose stability is supplied to consuming UI modules via a checked-in stability-configuration file, not via `@Stable`/`@Immutable` annotations here. |
| `:core:domain` | `subsloth.kmp.library` | `:core:model` | JVM, WasmJS | Port interfaces, use cases, domain logic, `DomainError` hierarchy. Pure Kotlin — no platform APIs. |
| `:core:network` | `subsloth.kmp.library` | `:core:model`, `:core:domain` | JVM, WasmJS | Ktor HTTP client, DTOs, API definitions, request identity, rate limiting. Transport only — no persistence or preferences dependency. |
| `:core:database` | `subsloth.kmp.library` + Room | `:core:model`, `:core:domain` | JVM, WasmJS, Android | Room 3 database, DAOs, migrations, schemas. Platform-specific SQLite drivers. |
| `:core:preferences` | `subsloth.kmp.library` | `:core:model`, `:core:domain` | JVM, WasmJS, Android | DataStore-backed preferences, credential store, account profiles. |
| `:core:media` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain`, `:core:database`, `:core:preferences` | JVM, WasmJS, Android | Media player abstractions, download manager, offline storage. |
| `:core:ui` | `subsloth.kmp.library` + Compose | `:core:model`, `:core:domain` | JVM, WasmJS | Shared Compose components, theming, navigation keys, UI error mapping. |
| `:core:data` | `subsloth.kmp.library` | `:core:model`, `:core:domain`, `:core:network`, `:core:database`, `:core:preferences` | JVM, WasmJS | Repository/orchestration classes that combine HTTP transport with persistence or preferences (e.g. `CatalogRepository`), implementing the domain ports those repositories fulfill. |

**Dependency rule:** Dependencies flow inward — `:core:model` has zero project dependencies. `:core:domain` depends only on `:core:model`. `:core:data` is, by deliberate architectural rule, the only module that combines `:core:network`, `:core:database`, and `:core:preferences`; those three never depend on each other. Every other core module depends on `:core:model` and optionally `:core:domain`. This specific rule is a documented convention, not yet independently verified by an executable test — `:testing:architecture-rules`'s `DependencyGraphInvariantTest` currently checks only the six `:feature:*` modules and `:core:network`, not arbitrary core-to-core combinations.

---

## Testing Modules

| Module | Convention Plugin | Description |
|---|---|---|
| `:testing:api-contract` | `subsloth.jvm.library` | WireMock stubs, fixture JSONs, HAR processing, `Endpoint` enum, `CaptureApi`/`ExportFixtures` tasks |
| `:testing:architecture-rules` | `kotlin("jvm")` (plain, no convention plugin) | Gradle TestKit-driven JUnit test that inspects the resolved dependency graph of every `:feature:*` module and `:core:network`, failing the build if a forbidden adapter dependency is present |
| `:testing:assertions` | `subsloth.jvm.library` | Shared test assertion helpers and matchers |
| `:testing:detekt-rules` | `subsloth.jvm.library` | Custom detekt rules specific to the project |
| `:testing:mock-api` | `subsloth.jvm.library` | Mock API server for integration testing |
| `:testing:tv-focus-harness` | `subsloth.jvm.library` | TV focus testing harness for desktop Compose tests |

**Dependency rule:** Testing modules are depended upon by test source sets only. They are never included in production classpaths.

---

## Benchmark

| Module | Convention Plugin | Description |
|---|---|---|
| `:benchmark` | `com.android.test` | Macrobenchmarks (startup, home load, detail open, playback start) and baseline profile generation. Self-instrumenting: targets `:androidApp`. |

See `docs/testing/benchmarks.md` for detailed usage.

---

## Dependency Graph (simplified)

Feature modules never depend on concrete adapter modules directly — only on
`:core:model`, `:core:domain` (domain ports), and shared UI (`:core:ui`,
`:core:media`). Concrete adapters are wired together in `:core:data` and
injected only at each platform's composition root
([details](architecture/composition-roots.md)).

```text
:feature:* ──────┬── :core:model
                  ├── :core:domain
                  ├── :core:ui        (auth, details)
                  └── :core:media     (player only)

:core:data ───────┬── :core:network      (transport only)
                  ├── :core:database
                  └── :core:preferences

:androidApp / :desktopApp / :webApp (composition roots) ──┬── :feature:*
                                                            ├── :core:data
                                                            ├── :core:media
                                                            ├── :core:ui
                                                            └── :core:* (directly, for wiring)
```

---

## How to Add a New Module

### 1. Choose a Convention Plugin

Use the table below to pick the right convention plugin for your module type. Each convention handles boilerplate: Kotlin/JVM target setup, spotless, detekt, power-assert, JUnit Platform, and common dependencies.

| Module type | Convention plugin | Example modules |
|---|---|---|
| Cross-platform library | `subsloth.kmp.library` | `:core:model`, `:feature:catalog` |
| Cross-platform library with Compose | `subsloth.kmp.library` + `kotlin.plugin.compose` + `alias(libs.plugins.compose.gradle)` | `:feature:auth`, `:core:ui` |
| Android-only library | `subsloth.android.library` | — |
| Android-only library with Compose | `subsloth.android.library.compose` | — |
| Android application | `subsloth.android.application` or `subsloth.android.application.compose` | `:androidApp` |
| JVM-only library | `subsloth.jvm.library` | `:testing:api-contract`, `:testing:assertions` |
| JVM application (desktop) | `subsloth.jvm.library` + manual Compose Desktop plugin | `:desktopApp` |
| Benchmark | `com.android.test` (no project-specific convention) | `:benchmark` |

### 2. Register the Module

Open `settings.gradle.kts` and add an `include(...)` line. Follow the existing naming convention:

```kotlin
include(":feature:your-new-feature")
```

Module paths use colons as separators and follow this naming pattern:
- Application shells: `:androidApp`, `:desktopApp`, `:webApp`
- Core: `:core:{name}` (model, domain, network, database, preferences, media, ui, data)
- Feature: `:feature:{name}` (auth, catalog, details, player, library, settings)
- Testing: `:testing:{name}` (api-contract, architecture-rules, assertions, detekt-rules, mock-api, tv-focus-harness)
- Benchmark: `:benchmark`

### 3. Create the Build File

Create `{module-path}/build.gradle.kts`. The minimum boilerplate depends on the convention plugin:

**KMP library (cross-platform):**
```kotlin
plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            // add shared dependencies
        }
        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.coroutines.test)
        }
    }
}
```

**KMP library with Compose:**
```kotlin
plugins {
    id("subsloth.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.gradle)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:ui"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.multiplatform.material3)
        }
        // ...
    }
}
```

**JVM-only library:**
```kotlin
plugins {
    id("subsloth.jvm.library")
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(project(":testing:assertions"))
}
```

### 4. Create Source Sets

| Convention | Main source path | Test source path |
|---|---|---|
| `subsloth.kmp.library` | `src/commonMain/kotlin/` | `src/jvmTest/kotlin/` |
| `subsloth.jvm.library` | `src/main/kotlin/` | `src/test/kotlin/` |
| `subsloth.android.*` | `src/main/kotlin/` | `src/test/kotlin/` (+ `src/androidTest/kotlin/` for instrumented) |

### 5. Create a Package and Add Code

Follow the `net.subsloth.{module}` package convention. For a feature module `:feature:example`:
- Sources: `src/commonMain/kotlin/net/subsloth/example/`
- Tests: `src/jvmTest/kotlin/net/subsloth/example/`

### 6. Verify

```bash
# Check compilation
./gradlew :{module-path}:compileKotlinJvm  # or compileKotlin for JVM-only

# Run tests
./gradlew :{module-path}:jvmTest  # or test for JVM-only

# Full build with linting
./gradlew spotlessApply spotlessCheck detekt :{module-path}:compileKotlinJvm :{module-path}:jvmTest
```

---

## Quick Reference: Which Convention to Use

| If you're building… | Use… |
|---|---|
| A new feature screen (works on all platforms) | `subsloth.kmp.library` + Compose plugins |
| A new core data/domain module | `subsloth.kmp.library` |
| An Android-only library | `subsloth.android.library` or `subsloth.android.library.compose` |
| An Android application | `subsloth.android.application` or `subsloth.android.application.compose` |
| A JVM-only library (e.g. testing utilities) | `subsloth.jvm.library` |
| A JVM application (e.g. desktop) | `subsloth.jvm.library` + manual Compose Desktop plugin |
| A new benchmark | `com.android.test` (no project-specific convention) |

See `docs/convention-plugins.md` for detailed convention plugin documentation.
