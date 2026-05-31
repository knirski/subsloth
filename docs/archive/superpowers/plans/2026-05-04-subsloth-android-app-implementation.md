# subsloth Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build `subsloth`, a native Kotlin Android app for Android TV 8, Android tablet 13, and Android phone 16 that browses, searches, streams, downloads, and resumes Media movies and TV series while preserving Media metadata and excluding comments.

**Architecture:** Use Functional Core / Imperative Shell with Arrow-first FP design. Pure Kotlin modules own sealed ADTs, typed errors, Raise/Either/Option-based decisions, optics-friendly immutable state, parsing, selection, resume, offline, and library policies; Android shell modules own Compose UI, Media3, Room, DataStore, encrypted credentials, WorkManager, networking, filesystem, and notifications.

**Tech Stack:** Kotlin, Gradle Kotlin DSL, AGP 9.2.0, Gradle 9.5.0, JDK 17, minSdk 26, targetSdk 36, compileSdk 36, Jetpack Compose, Compose for TV, Material 3 Adaptive, Hilt, Room, DataStore, Android Keystore, WorkManager, Retrofit/OkHttp, Moshi, Media3, Coil, OpenAPI Generator, Arrow Core, Arrow Retrofit, Arrow Resilience, Arrow Optics, Arrow Detekt rules, Kotest Arrow matchers, Turbine, MockWebServer, Compose UI Test, Roborazzi, Macrobenchmark, Baseline Profiles, detekt, Spotless/ktlint, Android Lint.

---

## Implementation Rules

- Keep generated OpenAPI DTOs out of domain and UI modules.
- Keep Android framework dependencies out of `:core:model` and `:core:domain`.
- Model domain state, events, and recoverable errors as Kotlin sealed ADTs; every domain `when` over a sealed hierarchy must be an exhaustive expression with no unnecessary `else`.
- Use Arrow `Raise` inside use cases/domain services, `Either` at module boundaries, `Option` for meaningful optional domain values, `NonEmptyList` for guaranteed non-empty domain collections, and accumulating validation for multi-field validation.
- Use Arrow Optics for deeply nested immutable state updates where nested `copy` chains would reduce clarity.
- Use Arrow Resilience schedules/circuit breakers for retryable Media API calls, metadata refresh, and download metadata recovery.
- Use tagless-final-inspired capability interfaces for effectful dependencies: small `suspend` ports with Arrow typed errors, interpreted by shell modules. Do not use full HKT/tagless-final machinery or `Kind<F, A>` abstractions unless a later task documents a concrete payoff.
- Use Arrow Retrofit integration at the HTTP boundary when compatible with Retrofit 3; otherwise wrap Retrofit calls into Arrow typed results in a small adapter and document the incompatibility.
- Never implement comments endpoints or comments UI.
- Run credentialed API discovery and update OpenAPI/fixtures before locking DTOs, persistence schema, or discovery-gated UI controls.
- Treat quality selection, confirmed season download queues, and precise recently-added/new-episode rows as discovery-gated features. New-episode notifications and automatic smart downloads are excluded from v1.
- Prefer cached/offline data first; network failure must not block downloaded playback.
- Store credentials with Android Keystore-backed encryption, exclude credential storage from backup, and avoid deprecated encrypted-preferences APIs unless the commit explicitly documents why no better stable API fits API 26.
- Match the Kodi plugin request identity for production Media API requests: same API host/prefix, same endpoint set, same Basic auth style, same JSON headers, and Kodi-style `User-Agent` metadata.
- Do not add browser/WebView verification flows, web-page scraping, web-only frontend APIs, or unrelated Media endpoints to the v1 native data source.
- Keep v1 traffic user-driven: no periodic background catalog polling, no request storms, low concurrency, single-flight duplicate request de-duplication, bounded retries, and explicit `429`/`Retry-After` handling.
- Remote filters/sorts and server-side library reads/writes are allowed only when Kodi plugin source or live parity tests prove the exact request shape.
- Raw stream, download, and subtitle URLs are ephemeral and must not be persisted; refresh expired URLs through Kodi-compatible detail requests only.
- v1 distribution is personal/internal sideload only, with no analytics SDK, crash-reporting SDK, telemetry upload, or remote diagnostics upload.
- Foreground services are narrow and typed: playback uses `mediaPlayback`, active visible downloads use `dataSync` only when needed, neither starts from `BOOT_COMPLETED`, and foreground services must not be used to bypass Android background-work quotas.
- Use stable dependency versions by default; if a newer alpha/beta/RC is needed for a device-blocking issue or platform requirement, document the issue, source, and rollback condition in the same commit. Current approved exception: `androidx.tv:tv-foundation:1.0.0-rc01` because first-class Android TV support is locked and the TV foundation artifact has no stable release yet.
- If the workspace is a valid Git repository, commit after each task with the exact message listed in that task. If Git is intentionally unavailable, treat those commit steps as local checkpoints and record completed tasks in the plan instead.
- Enable Gradle dependency verification metadata, dependency locking, and secret scanning/pre-commit or CI checks before adding third-party dependencies or committing discovery fixtures.
- Never commit authenticated Playwright/browser logs, screenshots, snapshots, HAR files, credentials, Basic auth headers, signed stream URLs, or signed download URLs.

## Target File Structure

Create this structure during the scaffold task:

```text
settings.gradle.kts
build.gradle.kts
.github/workflows/ci.yml
.github/workflows/release-please.yml
version.txt
CHANGELOG.md
gradle.properties
gradle/libs.versions.toml
build-logic/settings.gradle.kts
build-logic/convention/build.gradle.kts
build-logic/convention/src/main/kotlin/subsloth.android.application.gradle.kts
build-logic/convention/src/main/kotlin/subsloth.android.library.gradle.kts
build-logic/convention/src/main/kotlin/subsloth.kotlin.library.gradle.kts
build-logic/convention/src/main/kotlin/subsloth.android.hilt.gradle.kts
api/subsloth.openapi.yaml
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/net/subsloth/MainActivity.kt
core/model/build.gradle.kts
core/domain/build.gradle.kts
core/network/build.gradle.kts
core/database/build.gradle.kts
core/preferences/build.gradle.kts
core/media/build.gradle.kts
feature/auth/build.gradle.kts
feature/catalog/build.gradle.kts
feature/details/build.gradle.kts
feature/player/build.gradle.kts
feature/library/build.gradle.kts
feature/settings/build.gradle.kts
testing/api-contract/build.gradle.kts
```

---

### Task 1: Scaffold Gradle, Modules, And Quality Gates

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/convention/build.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/subsloth.android.application.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/subsloth.android.library.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/subsloth.kotlin.library.gradle.kts`
- Create: `build-logic/convention/src/main/kotlin/subsloth.android.hilt.gradle.kts`
- Create: module `build.gradle.kts` files listed in Target File Structure

- [ ] **Step 1: Create the Gradle wrapper using Gradle 9.5.0**

Run:

```bash
gradle wrapper --gradle-version 9.5.0 --distribution-type all
```

Expected: `gradlew`, `gradlew.bat`, and `gradle/wrapper/*` exist.

- [ ] **Step 2: Add root settings**

Create `settings.gradle.kts`:

```kotlin
pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "subsloth"

include(":app")
include(":core:model")
include(":core:domain")
include(":core:network")
include(":core:database")
include(":core:preferences")
include(":core:media")
include(":feature:auth")
include(":feature:catalog")
include(":feature:details")
include(":feature:player")
include(":feature:library")
include(":feature:settings")
include(":testing:api-contract")
```

- [ ] **Step 3: Add root build and properties**

Create `build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.openapi.generator) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless) apply false
}
```

Create `gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
org.gradle.parallel=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 4: Add version catalog**

Create `gradle/libs.versions.toml` with the exact pinned versions below. Update a version only when official release notes confirm a newer stable version, and update this implementation plan in the same commit:

```toml
[versions]
agp = "9.2.0"
kotlin = "2.2.21"
ksp = "2.2.21-2.0.4"
composeBom = "2026.04.01"
material3Adaptive = "1.2.0"
androidxCore = "1.17.0"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
navigation = "2.9.7"
hilt = "2.57.2"
androidxHilt = "1.3.0"
tvFoundation = "1.0.0-rc01"
tvMaterial = "1.0.1"
room = "2.8.4"
datastore = "1.1.7"
media3 = "1.10.0"
coil = "3.3.0"
retrofit = "3.0.0"
okhttp = "5.3.0"
moshi = "1.15.2"
work = "2.11.2"
coroutines = "1.10.2"
turbine = "1.2.1"
roborazzi = "1.52.0"
benchmark = "1.4.1"
openapiGenerator = "7.21.0"
jsonSchemaValidator = "3.0.1"
arrow = "2.2.2.1"
kotest = "6.1.11"
kotestArrow = "2.0.0"
arrowDetektRules = "0.5.0"
detekt = "1.23.8"
spotless = "8.4.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidxCore" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
androidx-compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-material3-adaptive = { module = "androidx.compose.material3.adaptive:adaptive", version.ref = "material3Adaptive" }
androidx-material3-adaptive-layout = { module = "androidx.compose.material3.adaptive:adaptive-layout", version.ref = "material3Adaptive" }
androidx-material3-adaptive-navigation = { module = "androidx.compose.material3.adaptive:adaptive-navigation", version.ref = "material3Adaptive" }
androidx-tv-foundation = { module = "androidx.tv:tv-foundation", version.ref = "tvFoundation" }
androidx-tv-material = { module = "androidx.tv:tv-material", version.ref = "tvMaterial" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
androidx-hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "androidxHilt" }
androidx-hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "androidxHilt" }
androidx-hilt-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "androidxHilt" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-compiler", version.ref = "hilt" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
media3-exoplayer = { module = "androidx.media3:media3-exoplayer", version.ref = "media3" }
media3-ui = { module = "androidx.media3:media3-ui", version.ref = "media3" }
media3-datasource-okhttp = { module = "androidx.media3:media3-datasource-okhttp", version.ref = "media3" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
retrofit = { module = "com.squareup.retrofit2:retrofit", version.ref = "retrofit" }
retrofit-converter-moshi = { module = "com.squareup.retrofit2:converter-moshi", version.ref = "retrofit" }
moshi = { module = "com.squareup.moshi:moshi", version.ref = "moshi" }
moshi-kotlin = { module = "com.squareup.moshi:moshi-kotlin", version.ref = "moshi" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-logging = { module = "com.squareup.okhttp3:logging-interceptor", version.ref = "okhttp" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
work-testing = { module = "androidx.work:work-testing", version.ref = "work" }
coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
mockwebserver3 = { module = "com.squareup.okhttp3:mockwebserver3", version.ref = "okhttp" }
json-schema-validator = { module = "com.networknt:json-schema-validator", version.ref = "jsonSchemaValidator" }
roborazzi = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
benchmark-macro-junit4 = { module = "androidx.benchmark:benchmark-macro-junit4", version.ref = "benchmark" }
arrow-core = { module = "io.arrow-kt:arrow-core", version.ref = "arrow" }
arrow-core-retrofit = { module = "io.arrow-kt:arrow-core-retrofit", version.ref = "arrow" }
arrow-resilience = { module = "io.arrow-kt:arrow-resilience", version.ref = "arrow" }
arrow-optics = { module = "io.arrow-kt:arrow-optics", version.ref = "arrow" }
arrow-optics-ksp-plugin = { module = "io.arrow-kt:arrow-optics-ksp-plugin", version.ref = "arrow" }
kotest-runner-junit5 = { module = "io.kotest:kotest-runner-junit5", version.ref = "kotest" }
kotest-assertions-core = { module = "io.kotest:kotest-assertions-core", version.ref = "kotest" }
kotest-property = { module = "io.kotest:kotest-property", version.ref = "kotest" }
kotest-assertions-arrow = { module = "io.kotest.extensions:kotest-assertions-arrow", version.ref = "kotestArrow" }
arrow-detekt-rules = { module = "com.wolt.arrow.detekt:rules", version.ref = "arrowDetektRules" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
detekt = { id = "io.gitlab.arturbosch.detekt", version.ref = "detekt" }
spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
androidx-baselineprofile = { id = "androidx.baselineprofile", version.ref = "benchmark" }
openapi-generator = { id = "org.openapi.generator", version.ref = "openapiGenerator" }
```

- [ ] **Step 5: Add convention plugins and empty modules**

Create convention plugins that set Java/Kotlin target 17, `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`, Compose enabled for Android modules that need UI, and explicit API mode for Kotlin library modules. Create empty module `build.gradle.kts` files applying the appropriate convention plugin.

Supply-chain baseline:
- Enable Gradle dependency verification metadata after the first dependency resolution and commit/checkpoint the generated verification metadata only after review.
- Enable dependency locking for resolvable configurations once the first dependency graph is stable.
- Add a secret/artifact scanning check that fails on credentials, Basic auth headers, signed media URLs, `.playwright-cli/`, HAR files, screenshots, and browser traces.

App identity requirements:
- `:app` namespace must be `subsloth`.
- `:app` `defaultConfig.applicationId` must be `subsloth`.
- Feature and core module namespaces must live under `subsloth.*`.

Arrow convention requirements:
- Add `arrow-core` to `:core:model`, `:core:domain`, `:core:network`, and feature modules that expose typed errors in UI state.
- Add `arrow-resilience` to `:core:network` and sync/download modules.
- Add `arrow-core-retrofit` to `:core:network` when compatible with Retrofit 3.
- Add `arrow-optics` and KSP `arrow-optics-ksp-plugin` only to modules with nested immutable state that benefits from generated optics.
- Add Arrow Detekt rules to the Detekt configuration.
- Add Kotest Arrow matchers/property helpers to JVM test source sets for core/domain/network tests.

UI dependency requirements:
- Add Material3 adaptive artifacts to tablet/adaptive UI modules that implement list-detail or multi-pane layouts.
- Add `androidx-tv-foundation` and `androidx-tv-material` to TV-capable UI modules that implement TV browse, focus, rows, or detail screens.
- `androidx.tv:tv-foundation:1.0.0-rc01` is an explicit pre-stable exception because the TV foundation artifact has no stable release yet and first-class Android TV support is a locked requirement. Replace it with the first stable `tv-foundation` release as soon as one is available.

Worker dependency requirements:
- Add `androidx-hilt-work` and `androidx-hilt-compiler` to modules that define WorkManager workers needing DI.
- WorkManager workers are imperative-shell interpreters only; pure scheduling/download decisions remain in `:core:domain` policies and are injected into workers through capability ports.
- Do not launch WorkManager, JobScheduler, DownloadManager, or equivalent quota-governed background jobs from foreground services to bypass Android 16+ quotas.

API contract test dependency requirements:
- Add `json-schema-validator` to `:testing:api-contract` for offline fixture validation against OpenAPI 3.1 response schema fragments.

Run:

```bash
./gradlew projects
```

Expected: all app, core, feature, and testing modules are listed.

- [ ] **Step 6: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle build-logic app core feature testing
git commit -m "chore: scaffold Android project"
```

---

### Task 2: Create API Contract And Drift Test Harness

**Files:**
- Create: `api/subsloth.openapi.yaml`
- Modify: `core/network/build.gradle.kts`
- Create: `testing/api-contract/src/test/kotlin/net/subsloth/testing/api/MediaContractFixtureTest.kt`
- Create: `testing/api-contract/src/test/kotlin/net/subsloth/testing/api/MediaLiveDriftTest.kt`
- Create: `testing/api-contract/src/test/resources/media/README.md`
- Create: `testing/api-contract/src/test/resources/media/movies.json`
- Create: `testing/api-contract/src/test/resources/media/shows.json`
- Create: `testing/api-contract/src/test/resources/media/show-detail.json`
- Create: `testing/api-contract/src/test/resources/media/movie-detail.json`
- Create: `testing/api-contract/src/test/resources/media/episode-detail.json`
- Create: `docs/api-discovery.md`

- [ ] **Step 1: Document discovery scope and fixture policy**

Create `docs/api-discovery.md` with the discovery checklist, credential handling rules, fixture sanitization rules, and the exact endpoints that may be probed. Do not run live Gradle tests yet; the live drift test harness does not exist until Step 5.

Seed the document with the authenticated web discovery notes already captured on May 4, 2026:
- Authenticated web pages must not become the native app data source because detail pages auto-load a frontend comments resource.
- Series web detail pages expose season tabs, `#season-episode` episode anchors, watched/favorite/watch-later actions, email notification action, per-episode subtitle download paths shaped like `/en/shows/{showSlug}/videos/{videoId}/download_subtitle/{language}`, and upcoming premiere text where available.
- Movie web detail pages expose Watch and Download buttons, watched/favorite/watch-later actions, and subtitle download paths shaped like `/en/movies/{movieSlug}/download_subtitle/{language}`.
- Web player labels include qualities `1080p`, `720p`, `480p`, `360p`, `240p`, `auto` and speeds `0.50`, `0.60`, `0.70`, `0.80`, `0.90`, `1.00`, `1.25`, `1.50`, `2.00`.
- Web playback used an HTML5 video element with fragmented media resources from an edge host; API discovery must determine whether Kodi/API responses expose direct URLs, adaptive/fragmented streams, or only reported resolution labels.
- Movie catalog web filters include genre, country, subtitle language, year range, rating range, and sort by publication date, popularity, rating, name, and year.

Document these findings:
- Whether stream quality is separate URLs, adaptive tracks, a single fixed URL, or only a resolution label.
- Whether `download_url` is consistently present, subscription-gated, single-use/signed, or absent for some content.
- Subtitle language shape, URL behavior, and offline-download viability.
- Trailer URL availability.
- Recently-added/new-episode precision: actual added/updated timestamp, movie release date, episode air date, show `newest_video`, or unavailable. Record the exact label allowed for each available signal.
- Error response shape for `401`, `402`, not found, and malformed IDs.
- Kodi plugin request identity: exact `User-Agent` value format, required headers, accepted content types, query parameter names, endpoint paths, redirect behavior, and non-JSON error bodies.
- Kodi plugin traffic shape: startup request sequence, request concurrency, pagination/query params, retry behavior, and any `429`/`Retry-After` handling observed during live parity tests.
- Kodi plugin server-side library actions: exact endpoints/methods/payloads for favorites, watch later, watched/progress, and subscriptions. Unobserved actions remain local-only.
- Local hygiene: delete generated authenticated browser logs/snapshots after extracting sanitized findings, and keep `.playwright-cli/`, HAR files, screenshots, and similar artifacts ignored.

- [ ] **Step 2: Preserve and refine the OpenAPI source**

Use the existing `api/subsloth.openapi.yaml` as the starting contract. Do not replace it with a minimal skeleton. It already includes Kodi Python source findings, authenticated browser discovery notes, excluded comments resources, Kodi-style User-Agent requirements, and tolerant schemas for movies, shows, episodes, subtitles, streams, and downloads.

Refine it only from sanitized fixtures, Kodi source evidence, and `docs/api-discovery.md`. The OpenAPI contract is allowed to change during implementation when generator output, fixture validation, or live drift tests reveal exact Media response shapes. When that happens, update the spec, fixtures, mapper tests, and `docs/api-discovery.md` in the same task.

Do not add comments endpoints except as explicitly excluded web resources.

- [ ] **Step 3: Configure OpenAPI generation and validation**

Configure OpenAPI Generator in `core/network/build.gradle.kts` to generate Kotlin/Moshi DTO models from `api/subsloth.openapi.yaml` into the module build directory. Keep generated DTOs out of domain and UI modules. The generated source set must be treated as network implementation detail and mapped manually into `:core:model` domain types.

OpenAPI tooling requirements:
- Add an `openApiValidate` verification path for `api/subsloth.openapi.yaml`.
- Configure `openApiGenerate` with `generatorName = "kotlin"`, Moshi serialization, package names under `subsloth.network.generated`, and output under `layout.buildDirectory.dir("generated/openapi")`.
- Generate models/DTOs only, not a competing API client layer; use OpenAPI Generator `globalProperties`/equivalent settings to generate `models` and skip generated API/client scaffolding.
- Add DTO model generation to `:core:network` compile inputs without committing generated files.
- If generator output is incompatible with Media's discovered schema or Retrofit 3, document the reason in `docs/api-discovery.md`, switch to handwritten DTOs, and keep `openApiValidate` plus fixture/schema tests as the contract gate.

Run:

```bash
./gradlew :core:network:openApiValidate :core:network:openApiGenerate :core:network:compileDebugKotlin
```

Expected for an Android `:core:network` module: the OpenAPI document validates, DTOs generate into `core/network/build/generated` or the configured Gradle build directory, and generated DTOs compile.

If `:core:network` is implemented as a JVM module instead of an Android module, replace `:core:network:compileDebugKotlin` with `:core:network:compileKotlin` in this verification command and document that module choice in `docs/api-discovery.md`.

If OpenAPI 3.1 `oneOf`/`anyOf`/nullable modeling produces unusable Kotlin/Moshi code, document the exact generator failure and switch network DTOs to handwritten models while preserving OpenAPI schema validation and drift tests.

- [ ] **Step 4: Add sanitized fixtures**

Create fixture files with realistic sanitized examples that include movie/show metadata, episode metadata, subtitles, `url`, `download_url`, and `resolution`. Do not include comments, credentials, signed URLs, or real private account data.

- [ ] **Step 5: Add offline fixture and schema tests**

Write tests that validate every fixture against `api/subsloth.openapi.yaml` schemas, parse every fixture through the network DTO layer, assert required top-level fields, and fail if comments fields are required by any mapper.

Schema validation requirements:
- Use `com.networknt:json-schema-validator` in `:testing:api-contract`, not only ad hoc JSON parsing.
- Load the matching response schema fragments from `api/subsloth.openapi.yaml` and validate fixtures as JSON Schema 2020-12/OpenAPI 3.1 schema data.
- Validate `movies.json`, `shows.json`, `show-detail.json`, `movie-detail.json`, and `episode-detail.json` against their matching response schemas.
- Keep schema validation offline and deterministic.

Run:

```bash
./gradlew :testing:api-contract:test
```

Expected: tests pass offline without credentials.

- [ ] **Step 6: Add optional live drift tests and run credentialed discovery**

Write live tests gated by environment variables:

```kotlin
val login = System.getenv("SUBSLOTH_LOGIN")
val password = System.getenv("SUBSLOTH_PASSWORD")
assumeTrue(!login.isNullOrBlank() && !password.isNullOrBlank())
```

Live tests call `/movies`, `/shows`, one movie detail, one show detail, and one episode detail using the same request identity and headers as the Kodi plugin. They assert status codes, response shape, and request metadata only. They must not call comments endpoints or endpoints not used by the Kodi plugin. With credentials available, run these tests once during this task and copy only sanitized field names, data types, capability findings, and non-sensitive examples into `docs/api-discovery.md`.

Add a negative contract assertion that web-only frontend comments resources such as `/api/frontend/comments` are not part of the native app data source and are never fetched by tests or production clients.

Add parity assertions that remote filter/sort query params and server-side library write endpoints are disabled unless Kodi plugin behavior proves their exact request shape.

Run:

```bash
./gradlew :testing:api-contract:test
```

Expected without env vars: live tests skipped, offline tests pass. Expected with credentials: live discovery tests pass or report precise drift; fixtures and OpenAPI are updated from sanitized findings only.

- [ ] **Step 7: Commit**

```bash
git add api core/network/build.gradle.kts testing/api-contract docs/api-discovery.md
git commit -m "test: add Media API contract harness"
```

---

### Task 3: Implement Core Models And Pure Domain Decisions

**Files:**
- Create: `core/model/src/main/kotlin/net/subsloth/model/MediaModels.kt`
- Create: `core/model/src/main/kotlin/net/subsloth/model/UserLibraryModels.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/domain/QualityPolicy.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/domain/SubtitlePolicy.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/domain/ResumePolicy.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/domain/NextEpisodePolicy.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/domain/SearchPolicy.kt`
- Test: matching files under `core/domain/src/test/kotlin/net/subsloth/domain/`

- [ ] **Step 1: Write failing domain tests**

Test these exact decisions:
- Phone/tablet default quality caps at `1080p`.
- TV default quality selects highest available.
- Quality labels normalize observed Media labels `auto`, `1080p`, `720p`, `480p`, `360p`, and `240p`.
- Manual in-player quality changes affect only the current playback session and do not update the account-scoped quality preference.
- Subtitle default is enabled English.
- Subtitle disabled means no subtitle selected.
- Resume ignores progress below `30s`.
- Resume treats progress at or beyond `95%` of known duration as completed for resume purposes and starts from the beginning unless the user explicitly seeks from history/player UI.
- Resume with unknown duration uses only the `30s` lower threshold and does not infer completion.
- Resume applies thresholds independently to account-scoped progress and shared offline progress before choosing the later resumable point.
- Playback reaching at least `95%` of known duration marks the item locally completed/watched in the relevant local scope.
- Unknown-duration playback marks completed only on actual playback-ended event.
- Explicit local watched/unwatched toggles are allowed; server watched/progress mutation remains gated by verified Kodi plugin behavior.
- Explicit watched/unwatched actions apply to current context: logged-in online/catalog/detail/library contexts update only active account-scoped watched/progress state; downloaded/offline contexts update shared offline watched/progress state for that downloaded media.
- Logged-in actions from a downloaded/offline view update shared offline watched/progress state and may label the result as local to this device.
- Explicit watched toggles are not automatically mirrored between account-scoped and shared offline state.
- Next episode follows season and episode order.
- Next episode prompt appears after local completion with a short countdown and explicit Play/Cancel actions.
- Do not autoplay next episode by default.
- Cancel/dismiss hides the next-episode prompt for that completed episode in the current playback session, including after focus changes, controls reopening, orientation changes, and process restoration for that same session.
- Replaying the episode later and reaching completion again may show the prompt again. Do not add a global next-episode prompt disable setting in v1.
- Streaming/logged-in next episode may use cached/detail metadata to identify the next released episode, but fetch/play starts only after user confirmation.
- Offline/logged-out next episode appears only when the next episode is already downloaded and playable locally.
- Unreleased upcoming episodes with future premiere dates are not playable or downloadable.
- Playback speed accepts `0.50x`, `0.60x`, `0.70x`, `0.80x`, `0.90x`, `1.00x`, `1.25x`, `1.50x`, and `2.00x`.
- Logged-in player speed changes persist immediately to the active account profile's playback speed preference.
- Trailer playback uses the same persisted account-scoped playback speed behavior as main playback; no trailer-specific speed state exists.
- Logged-out offline playback may use the last available local/default speed but must not mutate account-scoped playback speed preferences.
- Search token matching is case-insensitive and all tokens must match.

Run:

```bash
./gradlew :core:domain:test
```

Expected: fails because policies do not exist.

- [ ] **Step 2: Add immutable models**

Create models for `Movie`, `Show`, `Season`, `Episode`, `SubtitleTrack`, `VideoQuality`, `PlayableMedia`, `PlaybackProgress`, `DownloadState`, `LibraryState`, `DeviceClass`, and `SubtitlePreference`.

Represent sum types as sealed ADTs. Examples:
- `sealed interface PlayableMedia` with `data class Movie(...)` and `data class Episode(...)`.
- `sealed interface DownloadState` with `data object NotDownloaded`, `data class Queued(...)`, `data class Downloading(...)`, `data class Failed(...)`, and `data class Complete(...)`.
- `sealed interface DomainError` with typed cases for auth, payment/free-limit, network, decode, unavailable, and storage errors.
- `Option<SubtitleTrack>` for selected subtitles where absence is a meaningful state.
- `NonEmptyList<SubtitleTrack>` or `NonEmptyList<VideoQuality>` where the UI may assume at least one choice.
- Value classes for identifiers: `MovieId`, `ShowId`, `EpisodeId`, `ImdbId`, `TmdbId`, `SubtitleLanguage`, and `VideoResolution`.
- Represent upcoming/unreleased episodes explicitly, for example `EpisodeAvailability.Released` and `EpisodeAvailability.Upcoming(premiereDate)`, so the core can prevent invalid play/download actions.

- [ ] **Step 3: Implement pure policies**

Implement policy objects/functions in domain. Return values, not side effects. Use Arrow `Raise` inside policy composition, return `Either<DomainError, A>` at module boundaries, use `ValidatedNel`/accumulating validation for login/settings/download request validation, and use `when` as an expression for sealed ADTs with no unnecessary `else`.

Define tagless-final-inspired capability ports for use cases that need effects, for example catalog, library, credential, download, playback, clock, and connectivity ports. Keep these as focused Kotlin interfaces with `suspend` functions and typed Arrow errors. Shell modules provide Retrofit, Room, DataStore, Android Keystore, filesystem, WorkManager, and Media3 interpreters.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :core:model:test :core:domain:test
```

Expected: all tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/model core/domain
git commit -m "feat: add core media domain policies"
```

---

### Task 4: Implement Network Client And Mappers

**Files:**
- Create: `core/network/src/main/kotlin/net/subsloth/network/Api.kt`
- Create: `core/network/src/main/kotlin/net/subsloth/network/MediaAuthInterceptor.kt`
- Create: `core/network/src/main/kotlin/net/subsloth/network/MediaUserAgentInterceptor.kt`
- Create: `core/network/src/main/kotlin/net/subsloth/network/MediaUnexpectedResponseDetector.kt`
- Create: `core/network/src/main/kotlin/net/subsloth/network/RedactingLoggingInterceptor.kt`
- Create if generator fallback is documented: `core/network/src/main/kotlin/net/subsloth/network/dto/MediaDtos.kt`
- Create: `core/network/src/main/kotlin/net/subsloth/network/MediaMappers.kt`
- Test: `core/network/src/test/kotlin/net/subsloth/network/MediaMappersTest.kt`
- Test: `core/network/src/test/kotlin/net/subsloth/network/MediaAuthInterceptorTest.kt`
- Test: `core/network/src/test/kotlin/net/subsloth/network/MediaUserAgentInterceptorTest.kt`
- Test: `core/network/src/test/kotlin/net/subsloth/network/MediaUnexpectedResponseDetectorTest.kt`

- [ ] **Step 1: Write failing mapper and interceptor tests**

Use fixtures from `testing/api-contract`. Assert movies, shows, episodes, subtitles, stream URLs, download URLs, resolution, trailer URL where present, and metadata map correctly. Assert comments fields are ignored and never required. Assert auth uses Basic auth and logging redacts auth/media/download URLs.

User-Agent and unexpected-response tests:
- Production Media requests set the Kodi-style `User-Agent` and JSON headers observed in the Kodi plugin.
- The shipped Media API request identity must not contain `okhttp`, `Dalvik`, `HeadlessChrome`, `Playwright`, `Selenium`, `test`, emulator/debug markers, or Android browser/WebView identity.
- Redirects, HTML bodies, and other non-JSON responses from API endpoints map to a typed recoverable network/service error.
- Unexpected-response errors include only sanitized status/content-type/redirect target data needed by the app shell; they must not include credentials, auth headers, media URLs, or raw signed URLs.
- Unexpected responses are not retried in a network loop.
- Duplicate in-flight requests for the same catalog/detail/library key are coalesced.
- Retry budgets are bounded and respect `429`/`Retry-After`.
- Raw stream, download, subtitle, and artwork URLs are never persisted by network mappers, logs, diagnostics, or fixtures.

Run:

```bash
./gradlew :core:network:test
```

Expected: fails because client/mappers do not exist.

- [ ] **Step 2: Implement API interface and mappers**

Implement Retrofit/OkHttp client with endpoints from the spec. Use generated DTO models from `subsloth.network.generated` by default. Create handwritten DTOs only if Task 2 documented that OpenAPI Generator output is incompatible with the discovered Media schema or Retrofit 3, and keep that fallback isolated under `core/network/src/main/kotlin/net/subsloth/network/dto`.

Prefer Arrow Retrofit integration to expose typed Arrow results; if Retrofit 3 compatibility blocks it, implement a small local adapter from Retrofit responses/exceptions into `Either<NetworkError, A>`. Map API errors into typed failures: unauthorized, payment/free-limit, not found, network unavailable, server failure, decode failure.

Implement `MediaUserAgentInterceptor` and `MediaUnexpectedResponseDetector`:
- `MediaUserAgentInterceptor` applies the Kodi-style User-Agent value documented by live discovery, defaulting to the observed `Kodi ({version})` format until drift tests prove otherwise.
- `MediaUnexpectedResponseDetector` detects redirects, HTML bodies, or other non-JSON API responses before DTO parsing and maps them to a typed recoverable network/service error.
- Keep WebView and Android `CookieManager` out of Media API request identity. The v1 client does not use browser verification cookies/tokens.
- Implement a small request policy around the Retrofit/OkHttp boundary: low concurrency, single-flight request coalescing for identical catalog/detail/library reads, bounded retry schedules, and explicit `429`/`Retry-After` handling.
- Implement remote filter/sort requests only for query params proven by Kodi plugin behavior. All other filters/sorts are local over fetched/cached data.

Use Arrow Resilience for retryable calls:
- Retry idempotent catalog/detail refresh on transient network/server failures.
- Do not retry `401`, `402`, decode failures, malformed IDs, or comments endpoints.
- Never call web-only frontend comments resources such as `/api/frontend/comments`; authenticated web discovery showed these can load automatically on Media web detail pages, so scraping web pages is not an acceptable native data source.
- Do not retry unexpected redirect/HTML/non-JSON responses in a network loop.
- Use circuit-breaker behavior for repeated Media availability failures so UI can show cached/offline data quickly.

- [ ] **Step 3: Verify network tests**

Run:

```bash
./gradlew :core:network:test :testing:api-contract:test
```

Expected: all tests pass; live tests skip without credentials.

- [ ] **Step 4: Commit**

```bash
git add core/network testing/api-contract api
git commit -m "feat: add Media network client"
```

---

### Task 5: Implement Persistence, Settings, And Encrypted Credentials

**Files:**
- Create: `core/database/src/main/kotlin/net/subsloth/database/SubSlothDatabase.kt`
- Create: `core/database/src/main/kotlin/net/subsloth/database/entity/LibraryEntities.kt`
- Create: `core/database/src/main/kotlin/net/subsloth/database/dao/LibraryDao.kt`
- Create: `core/preferences/src/main/kotlin/net/subsloth/preferences/AccountProfileStore.kt`
- Create: `core/preferences/src/main/kotlin/net/subsloth/preferences/UserPreferences.kt`
- Create: `core/preferences/src/main/kotlin/net/subsloth/preferences/CredentialStore.kt`
- Create: `app/src/main/res/xml/backup_rules.xml`
- Create: `app/src/main/res/xml/data_extraction_rules.xml`
- Test: `core/database/src/test/kotlin/net/subsloth/database/LibraryDaoTest.kt`
- Test: `core/preferences/src/test/kotlin/net/subsloth/preferences/AccountProfileStoreTest.kt`
- Test: `core/preferences/src/test/kotlin/net/subsloth/preferences/UserPreferencesTest.kt`
- Test: `core/preferences/src/androidTest/kotlin/net/subsloth/preferences/CredentialStoreTest.kt`

- [ ] **Step 1: Write failing persistence tests**

Tests cover account profile key derivation from normalized login, raw login never stored as a profile identifier, account-scoped online progress save/load, shared offline progress save/load, shared downloads, favorites, watch later, subtitle preference persistence, quality preference persistence, playback speed persistence, per-account isolation for account-scoped Room rows and DataStore preferences, shared offline downloads/progress visible across accounts, logout always clearing credentials, logout retaining non-transient local data by default, optional cleanup deleting shared downloads/offline progress, optional cleanup deleting active-profile preferences, optional cleanup deleting active-profile watch/library data, other account profiles remaining untouched except for intentionally shared offline data deletion, backup exclusion for credential files, and raw media URLs never being persisted.

Run:

```bash
./gradlew :core:database:test :core:preferences:test
```

Expected: fails because DAOs/stores do not exist.

- [ ] **Step 2: Implement Room database**

Create entities for account-scoped cached online metadata, streamed/online playback progress, favorites, watch later, watched state, subscriptions/server mirrors, and local-only library records. Create separate shared offline entities for downloaded media records, minimal offline display metadata, and shared offline playback progress. Include migration baseline version `1`. Every account-scoped row must include the non-reversible local account profile key and queries must filter by the active account profile. Shared offline rows must not include an account profile key. Download records persist opaque app-private local file path, Media content id, media type, size/status, and timestamps only; video records store selected quality, and subtitle sidecar records store language plus source/format when Media/Kodi-compatible data exposes it. They never persist raw stream, download, subtitle URLs, raw Media login/email, or profile keys. Shared offline display metadata persists indefinitely while downloaded media exists and may contain last known title, poster/backdrop cache keys, episode/season info, effective downloaded quality, subtitle languages, duration, and local progress references. Download paths and artwork URLs must not be exposed in diagnostics/logs except as redacted IDs, counts, cache keys, or byte sizes.

- [ ] **Step 3: Implement DataStore and Android Keystore-backed credentials**

AccountProfileStore owns local profile-key derivation. It normalizes the Media login by trimming whitespace, applying Unicode NFC, and using locale-independent lowercase for email-style logins, then derives the profile key as `HMAC-SHA256(appLocalProfileSalt, normalizedLogin)`. The app-local profile salt is app-private non-credential metadata and is not cleared by logout or "Reset preferences". UserPreferences owns account-scoped subtitle enabled/language, quality preference, playback speed, downloads-on-Wi-Fi-only, and cache timestamps. CredentialStore owns encrypted login/password and has `save`, `read`, and `clear`.

CredentialStore requirements:
- Use Android Keystore-backed encryption compatible with API 26.
- Keep credentials separate from DataStore.
- Keep credentials separate from account profile data and shared offline data. Clearing credentials must not delete account profile rows, profile preferences, shared offline download files, shared offline progress, or the profile-key derivation salt.
- Do not persist Basic auth headers.
- Do not log credential material.
- Avoid deprecated AndroidX Security encrypted-preferences APIs unless the implementation commit documents why direct platform Keystore is not viable.
- Exclude credential files from Auto Backup and device-transfer backup using `backup_rules.xml` and `data_extraction_rules.xml`.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :core:database:test :core:preferences:test
```

Expected: unit tests pass. Instrumented encrypted credential tests run on emulator/device in Task 13.

- [ ] **Step 5: Commit**

```bash
git add core/database core/preferences app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml
git commit -m "feat: add local persistence and encrypted credentials"
```

---

### Task 6: Implement Media Playback, Resume, Subtitles, And Downloads Shell

**Files:**
- Create: `core/media/src/main/kotlin/net/subsloth/media/PlaybackController.kt`
- Create: `core/media/src/main/kotlin/net/subsloth/media/MediaItemFactory.kt`
- Create: `core/media/src/main/kotlin/net/subsloth/media/DownloadController.kt`
- Create: `core/media/src/main/kotlin/net/subsloth/media/SubSlothDownloadService.kt`
- Test: `core/media/src/test/kotlin/net/subsloth/media/MediaItemFactoryTest.kt`
- Test: `core/media/src/test/kotlin/net/subsloth/media/DownloadControllerTest.kt`

- [ ] **Step 1: Write failing media tests**

Tests cover local file selected before stream, no-subtitle playback when disabled/missing, preferred subtitle attachment, subtitle load/download failure continuing video playback, non-blocking subtitle failure actions for retry current subtitle, switch language, and turn subtitles off, no automatic subtitle language cycling, offline subtitle retry limited to local sidecar files unless the logged-in user explicitly requests adding or retrying a subtitle sidecar, quality constraints, saved resume seek position, streamed playback URL failure performing at most one same-item Kodi-compatible detail/media refresh and one playback retry, selected streamed quality failure falling back at most once to the nearest lower compatible quality for current playback only, quality fallback notice without persisting the fallback as preference, no automatic cycling through every quality, refreshed streamed retry or quality fallback failure surfacing recoverable Retry/Back to details actions, streamed playback or same-item URL refresh auth failure stopping online playback, saving local progress, marking auth invalid, routing to auth repair/login, no auth-sensitive playback retry loops, offline local-file playback never performing network refresh or quality fallback, missing/corrupt local files showing Back to Downloads/Details, auth failure not interrupting local downloaded playback and deferring repair until online-only action, downloaded quality metadata, one shared video asset per content item, lower-quality duplicate download reuse/skip when a higher-quality asset exists, safe higher-quality replacement after successful verification, ambiguous quality ordering refusing auto-replace, subtitle sidecar assets by content, language, and source/format when available, adding missing subtitle sidecars without re-downloading an existing shared video asset, ambiguous subtitle identity keeping separate sidecars instead of overwriting, low-storage refusal using known/estimated remaining size plus safety reserve, unknown-size downloads requiring reserve and ongoing checks, no automatic completed-media deletion to make room, metered-network confirmation, pause/resume/retry where supported, partial-file cleanup, playback foreground-service type, download foreground-service type when used, active-download notification channel creation, no queue summary notification, no completed/failed queued-item notifications, active notification tap target to Downloads, and minimal active-only notification actions.

Run:

```bash
./gradlew :core:media:test
```

Expected: fails because media shell does not exist.

- [ ] **Step 2: Implement Media3 boundaries**

Create factories that convert domain playback requests into Media3 `MediaItem`s. Keep Media3 types out of pure domain modules.

Media item requirements:
- Support direct media URLs, adaptive playlists, and fragmented/progressive resources discovered from the API.
- Attach subtitle files as external subtitle configurations when available; do not require HTML `<track>` elements because authenticated web playback may use custom subtitle controls without native track elements.
- Apply Media3 track-selection constraints when the API exposes adaptive/fragmented streams and a user-selected quality.

Playback service requirements:
- Use a dedicated Media3 playback service only while playback needs to continue in the background or on Android TV.
- Declare `android:foregroundServiceType="mediaPlayback"` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
- Do not start playback foreground services from `BOOT_COMPLETED`.
- Create a playback notification channel and a platform-compliant foreground notification when required.

- [ ] **Step 3: Implement download service boundary**

Use shared device-local app-private storage for downloaded videos and subtitles. Download video and optional subtitles independently. Downloaded media is not additionally encrypted beyond Android app sandbox and device-storage protections in v1. Shared offline storage keeps one video asset per content item. If a higher-quality completed asset already exists, lower-quality download requests for the same content reuse the existing asset or are skipped with an "already available in higher quality" state. If a lower-quality asset exists and the user requests a higher quality, the higher-quality download may replace the lower-quality asset only after the new file completes and verifies successfully; do not delete the existing playable asset until replacement verification succeeds. If exact quality ordering cannot be determined from Media/API data, do not auto-replace; keep the existing playable asset and show a clear unavailable/ambiguous reason. Subtitles are shared sidecar assets by content item, language, and source/format when available. If a shared video asset already exists and a user requests more subtitle languages, download only the missing subtitle sidecars and attach them to the existing offline item. Do not duplicate or re-download video for subtitle-only changes. Subtitle sidecars are shared across accounts and visible in the logged-out Offline Library. If subtitle identity is ambiguous, keep separate sidecars rather than overwriting an existing playable subtitle. Shared offline metadata records the effective downloaded quality and is kept indefinitely while downloaded media exists. Deleting a downloaded media item deletes shared offline display metadata when no other shared offline asset for that content remains. Download directories and filenames use opaque identifiers only. Path components may include Media content ID/video ID, subtitle language code, random UUID, and file extension. Path components must not include raw login/email values, account profile keys, movie/show/episode titles, slugs, search queries, subtitle filenames returned by Media, or human-readable account labels. UI display titles come from shared offline Room metadata, not filesystem names. Do not expose downloaded media through public/shared storage, MediaStore, Storage Access Framework exports, or external player handoff. Missing subtitle download does not fail video download. Failed/partial video download is not playable.

Download robustness requirements:
- Track queued, downloading, paused, failed, partial, complete, and unavailable states.
- Verify file existence and non-zero length before marking playable.
- Clean up partial files on cancel and storage cleanup.
- Before queueing or resuming a download, require known/estimated remaining download size plus a safety reserve: `2 GB`, or `10%` of total device storage on small devices, whichever is smaller.
- If size is unknown, require the safety reserve before starting and continue checking during download.
- If the storage check fails, mark the queue item paused/unavailable with a clear low-storage reason before starting more transfer.
- Do not delete completed downloads automatically to make room; completed media deletion is always user-driven through storage/download management.
- Exclude downloaded videos, subtitles, partial download files, and download metadata from Android Auto Backup and device-to-device transfer.
- Redact absolute media file paths from diagnostics and logs when they could reveal opaque content identifiers, title names, or subtitle filenames.
- Respect downloads-on-Wi-Fi-only by default on phone/tablet.
- Require explicit confirmation for metered-network downloads.
- Prefer user-initiated transfer APIs where practical. If a foreground service is needed for active visible downloads, declare `android:foregroundServiceType="dataSync"` and `FOREGROUND_SERVICE_DATA_SYNC`.
- Do not start download foreground services from `BOOT_COMPLETED`.
- Create only an active-download notification channel and a platform-compliant foreground notification when required for the active visible download or foreground service. Do not create a queue summary notification or notifications for completed/failed queued items. The active notification shows current item title or a redacted generic label if needed, progress when known, paused/metered/low-storage state when relevant, and a tap target to Downloads. Notification actions are limited to safe active-item actions such as pause/cancel when platform-appropriate; queue-wide retry/delete actions stay inside the app.
- Android 13+ requests `POST_NOTIFICATIONS` only when required for playback/download foreground notifications. Permission denial must not break required playback/download foreground-service notifications.
- On Android TV, treat playback/download notifications as platform-required operational UI, not as the primary control surface.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :core:media:test
```

Expected: tests pass.

- [ ] **Step 5: Commit**

```bash
git add core/media
git commit -m "feat: add media playback and downloads shell"
```

---

### Task 7: Implement App Shell, Navigation, And Login

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/styles.xml`
- Create: `app/src/main/java/net/subsloth/SubSlothApplication.kt`
- Create: `app/src/main/java/net/subsloth/MainActivity.kt`
- Create: `app/src/main/java/net/subsloth/navigation/AppNavGraph.kt`
- Create: `app/src/main/java/net/subsloth/security/SensitiveScreenPolicy.kt`
- Create: `app/src/main/java/net/subsloth/network/KodiUserAgentProvider.kt`
- Create: `feature/auth/src/main/kotlin/net/subsloth/auth/LoginScreen.kt`
- Create: `feature/auth/src/main/kotlin/net/subsloth/auth/LoginViewModel.kt`
- Test: `feature/auth/src/test/kotlin/net/subsloth/auth/LoginViewModelTest.kt`

- [ ] **Step 1: Write failing auth tests**

Tests cover no credentials routes to login, valid credentials route to catalog, invalid credentials show auth error, logout clears credentials and routes to login, logout does not mutate Media server state, logged-out Offline Library entry appears only when playable shared downloads exist, logged-out Offline Library is a single combined library with no profile chooser, logged-out Offline Library never sends Media requests or validates credentials, `FLAG_SECURE` applies only on credential-sensitive screens, unexpected API redirect/HTML/non-JSON states remain recoverable, and offline downloads remain reachable while online service state is unavailable.

Run:

```bash
./gradlew :feature:auth:test
```

Expected: fails because auth ViewModel does not exist.

- [ ] **Step 2: Implement app manifest and shell**

Set package/namespace to `subsloth`. Add TV launcher intent, internet permission, foreground service/download permissions as needed, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `FOREGROUND_SERVICE_DATA_SYNC` when a data-sync foreground service is implemented, Android 13 `POST_NOTIFICATIONS` permission guarded by runtime request only for playback/download foreground notifications, backup exclusion resources, edge-to-edge theme defaults, and predictive-back support where available. Manifest services must declare matching foreground-service types and must not be boot-completed receivers for playback/download work.

Implement `SensitiveScreenPolicy` in the app shell to apply Android `FLAG_SECURE` only while credential-sensitive screens are visible: login, auth repair, diagnostics, and logout cleanup confirmation. Do not apply `FLAG_SECURE` globally to catalog, details, library, settings, or playback in v1.

Implement `KodiUserAgentProvider` with the Kodi-style User-Agent value documented by API discovery. Provide this value to the network shell through DI. Do not derive Media API identity from WebView/Chrome, and do not send headless, automation, test, OkHttp/Dalvik, emulator/debug, or Android-browser User-Agent strings for production Media traffic.

- [ ] **Step 3: Implement login UI**

Create phone/tablet/TV-compatible Compose login. Use D-pad focus order on TV. Mark login as credential-sensitive for `FLAG_SECURE`. Allow normal Android Autofill/password manager integration for login and password fields. Do not implement custom paste-from-clipboard buttons, clipboard inspection, clipboard history, or clipboard clearing. Password input uses secure text entry and does not expose characters except through standard temporary reveal behavior if explicitly enabled by the user. Error text is clear and does not expose credentials. Never log login text, password text, Autofill payloads, IME suggestions, clipboard contents, or validation request bodies. Login screen screenshots in tests or docs must use fake credentials only.

When no credentials are stored, show an "Offline Library" entry point only if at least one playable shared download exists. Offline Library is a single combined library across all shared downloaded media; do not show account/profile labels or profile selection.

- [ ] **Step 4: Implement recoverable service-state UI**

For unexpected API redirect/HTML/non-JSON responses, show a recoverable state with retry and offline-library actions. Do not open a WebView, scrape the redirected page, sync browser cookies, or automate browser verification.

- [ ] **Step 5: Verify**

Run:

```bash
./gradlew :app:assembleDebug :feature:auth:test
```

Expected: app assembles and auth tests pass.

- [ ] **Step 6: Commit**

```bash
git add app feature/auth
git commit -m "feat: add app shell and authentication"
```

---

### Task 8: Implement Catalog, Search, And Offline Home Mode

**Files:**
- Create: `feature/catalog/src/main/kotlin/net/subsloth/catalog/HomeScreen.kt`
- Create: `feature/catalog/src/main/kotlin/net/subsloth/catalog/HomeViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/net/subsloth/catalog/SearchScreen.kt`
- Create: `feature/catalog/src/main/kotlin/net/subsloth/catalog/MediaCard.kt`
- Test: `feature/catalog/src/test/kotlin/net/subsloth/catalog/HomeViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/net/subsloth/catalog/SearchViewModelTest.kt`

- [ ] **Step 1: Write failing catalog tests**

Tests cover cached data first, offline home shows Available Offline and Continue Watching, logged-out Offline Library allows shared downloaded-title browsing/playback/storage usage/download deletion only, logged-out Offline Library uses shared offline progress, logged-out Offline Library keeps last known display metadata indefinitely while media exists, artwork loads only from Kodi-compatible returned URLs with low-concurrency demand-driven requests, evicted artwork falls back to placeholders without network refresh, logged-out Offline Library blocks catalog, online search, network-refreshed details, favorites/watch-later/server library actions, new downloads, progress sync, notifications, account settings, and diagnostics with auth/network state, search filters movies/shows, process-death restoration of selected tab/search query, no comments calls are made, and recency rows appear only when Kodi-compatible data supports an honest label.

Run:

```bash
./gradlew :feature:catalog:test
```

Expected: fails because catalog does not exist.

- [ ] **Step 2: Implement home and search state**

Home rows: Continue Watching, Available Offline when relevant, Movies, Shows, and discovery-gated recency rows. Use "Recently Added" only with actual added/updated timestamps. Use "Recent by release date" for movie release-date fallback, "Recent by air date" for episode air-date fallback, and "Shows with recent episodes" for show-level `newest_video` fallback. Hide recency rows when none of those signals exist. Do not use web scraping or non-Kodi endpoints to improve recency precision. Load poster/backdrop artwork only from Kodi-compatible returned URLs, demand-driven from visible UI, with memory/disk caching, low concurrency, single-flight de-duplication, bounded retry, and no catalog-wide bulk prefetch. Do not infer artwork URLs or call unrelated image/search APIs. Search supports type, genre, country, subtitle language, watched/unwatched, downloaded/offline availability, year range, and rating range when data is present. Logged-in views may combine account-scoped progress with shared offline progress where relevant. Logged-out Offline Library uses the same downloaded-media presentation components but runs with an offline-only capability set, shared offline progress only, shared offline display metadata only, placeholder artwork when image cache is evicted, and no network-backed actions.

- [ ] **Step 3: Implement adaptive UI**

Phone uses bottom navigation. Tablet uses wider grids/list-detail entry. TV uses rows and focusable large cards.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :feature:catalog:test :app:assembleDebug
```

Expected: tests pass and app assembles.

- [ ] **Step 5: Commit**

```bash
git add feature/catalog app
git commit -m "feat: add catalog search and offline home"
```

---

### Task 9: Implement Movie And Series Detail Screens

**Files:**
- Create: `feature/details/src/main/kotlin/net/subsloth/details/MovieDetailScreen.kt`
- Create: `feature/details/src/main/kotlin/net/subsloth/details/SeriesDetailScreen.kt`
- Create: `feature/details/src/main/kotlin/net/subsloth/details/EpisodeRow.kt`
- Create: `feature/details/src/main/kotlin/net/subsloth/details/DetailViewModels.kt`
- Test: `feature/details/src/test/kotlin/net/subsloth/details/MovieDetailViewModelTest.kt`
- Test: `feature/details/src/test/kotlin/net/subsloth/details/SeriesDetailViewModelTest.kt`

- [ ] **Step 1: Write failing detail tests**

Tests cover all required metadata from the spec, season tabs, episode ordering, play/resume labels, next episode state, favorite/watch later actions, download action, and no comments UI state.

Run:

```bash
./gradlew :feature:details:test
```

Expected: fails because detail feature does not exist.

- [ ] **Step 2: Implement movie detail**

Include poster, title, plot, subtitle languages, watch/resume, download, trailer when available, rating, year, genres, countries, duration, quality/resolution, favorite, watch later, watched/progress, downloaded availability. Exclude comments completely.

- [ ] **Step 3: Implement series detail**

Include poster/backdrop, title, plot, rating, year, genres, countries, status, duration, IDs where available, newest video, qualities, favorite, watch later/subscription, watched/progress, downloaded availability, season tabs, episode rows, upcoming premiere rows where available, and next episode.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :feature:details:test :app:assembleDebug
```

Expected: tests pass and app assembles.

- [ ] **Step 5: Commit**

```bash
git add feature/details app
git commit -m "feat: add movie and series details"
```

---

### Task 10: Implement Player UI And In-Player Controls

**Files:**
- Create: `feature/player/src/main/kotlin/net/subsloth/player/PlayerScreen.kt`
- Create: `feature/player/src/main/kotlin/net/subsloth/player/PlayerViewModel.kt`
- Test: `feature/player/src/test/kotlin/net/subsloth/player/PlayerViewModelTest.kt`

- [ ] **Step 1: Write failing player tests**

Tests cover resume position using the later resumable point from account-scoped progress and shared offline progress, ignoring progress below `30s`, treating progress at or beyond `95%` of known duration as completed for resume purposes, local completed/watched marking at `95%` of known duration in the relevant local scope, unknown duration using only the `30s` lower threshold and marking completed only on playback-ended event, explicit local watched/unwatched toggles, online/catalog/detail/library toggles updating only active account-scoped watched/progress state, downloaded/offline toggles updating shared offline watched/progress state, no automatic mirroring between account-scoped and shared offline watched state, no server watched/progress mutation without verified Kodi plugin behavior, no copying shared offline progress into account-scoped progress on login, account-scoped progress updating only after logged-in play/resume/seek/complete, optional "Resume from this device" indicator when shared offline progress is ahead, subtitle enabled/disabled, in-player subtitle language switch, subtitle failure continuing video playback with explicit retry/switch/off actions, manual in-player quality changes scoped to current playback without updating account-scoped quality preference, logged-in playback speed changes persisting to the active account profile, trailer playback using the same account-scoped playback speed behavior as main playback, logged-out offline playback not mutating account-scoped speed preference, next episode prompt after local completion with explicit Play/Cancel, no default autoplay, next-episode prompt dismissal hidden for current completed episode/session across focus/control/orientation/process restoration, replaying later may show prompt again, no global disable setting, streaming next episode fetch/play only after user confirmation, offline next episode only when already downloaded and playable locally, no next-episode actions for unreleased/upcoming episodes, offline local playback, and no-subtitle offline playback.

Run:

```bash
./gradlew :feature:player:test
```

Expected: fails for player ViewModel tests.

- [ ] **Step 2: Implement player UI and state**

Use the `:core:media` Media3 shell boundary instead of placing Compose UI in `:core:media`. Add controls for subtitle language/off, speed, quality when available, play/pause, seek, and next episode prompt/countdown with explicit Play/Cancel. Manual quality changes inside the player affect only the current playback session and do not update the account-scoped quality preference; persistent default quality changes happen only from Settings. Logged-in player speed changes persist immediately to the active account profile's playback speed preference. Trailer playback uses the same persisted account-scoped playback speed behavior as main playback; do not add trailer-specific speed state. Logged-out offline playback may use the last available local/default speed but must not mutate account-scoped playback speed preferences. Do not autoplay next episode by default. If a subtitle file, track, or subtitle download fails during playback, keep video playback running and show a non-blocking subtitle error with explicit actions to retry the current subtitle, switch language when another downloaded or available subtitle exists, or turn subtitles off. Do not automatically cycle subtitle languages. Subtitle retry follows the same Kodi-compatible URL and bounded retry rules; offline local playback retries only local sidecar files unless the user is logged in and explicitly requests adding or retrying a subtitle sidecar. For streamed playback URL expiry/failure, run at most one same-item Kodi-compatible detail/media refresh and retry playback once. If streamed playback fails for a selected quality and lower compatible qualities are known, allow one current-playback-only fallback to the nearest lower compatible quality with a non-blocking notice; do not persist that fallback as the user's quality preference, and do not cycle through every quality. If refreshed streamed retry or quality fallback fails, show a recoverable playback error with Retry and Back to details actions. If streamed playback or same-item URL refresh returns `401` or equivalent auth failure, stop online playback, save local progress, mark auth state invalid, and route to auth repair/login without auth-sensitive retry loops. Offline local-file playback must not refresh over network or perform quality fallback; missing/corrupt local files show a local-file error with Back to Downloads/Details. If auth fails while playing a local downloaded file, do not interrupt playback; defer auth repair until the user takes an online-only action. Match the observed Media web speed range where practical: `0.50x`, `0.60x`, `0.70x`, `0.80x`, `0.90x`, `1.00x`, `1.25x`, `1.50x`, and `2.00x`.

- [ ] **Step 3: Verify**

Run:

```bash
./gradlew :feature:player:test :core:media:test :app:assembleDebug
```

Expected: tests pass and app assembles.

- [ ] **Step 4: Commit**

```bash
git add feature/player core/media app
git commit -m "feat: add playback UI and controls"
```

---

### Task 11: Implement Library, Downloads, Storage Management, And Settings

**Files:**
- Create: `feature/library/src/main/kotlin/net/subsloth/library/LibraryScreen.kt`
- Create: `feature/library/src/main/kotlin/net/subsloth/library/DownloadsScreen.kt`
- Create: `feature/library/src/main/kotlin/net/subsloth/library/StorageManagementScreen.kt`
- Create: `feature/settings/src/main/kotlin/net/subsloth/settings/SettingsScreen.kt`
- Create: `feature/settings/src/main/kotlin/net/subsloth/settings/DiagnosticsScreen.kt`
- Test: `feature/library/src/test/kotlin/net/subsloth/library/LibraryViewModelTest.kt`
- Test: `feature/settings/src/test/kotlin/net/subsloth/settings/SettingsViewModelTest.kt`

- [ ] **Step 1: Write failing library/settings tests**

Tests cover Continue Watching, favorites, watch later, Available Offline, logged-out Offline Library storage/download deletion subset, central Downloads screen sections for active, queued/paused, failed/unavailable, and completed items, download rows with title/media type/episode info/effective or target quality/subtitle status/size/progress/status reason/actions, season queue per-episode status visible from Downloads screen, TV Downloads large rows/cards, deterministic D-pad focus, focus restoration after dialogs/detail/player, overscan-safe spacing, simple TV actions, TV storage deletion actions limited to delete this download/delete watched completed/delete all downloads, TV destructive confirmation dialogs stating deleted scope and shared offline progress removal, no TV long menus/multi-select/range selection/drag-reorder/filter-builder deletion/dense tables, storage usage, low-storage recovery showing required/available/reserve space with Manage downloads action, storage management sorting/filtering by largest size, completed/watched status, and recently played/downloaded, no auto-select or auto-delete behavior, delete individual downloads, explicit confirmation for bulk deletes, delete downloads completed according to shared offline progress, delete all downloads, shared offline progress deletion when shared downloads are deleted, subtitle settings, quality settings, playback speed settings, absence of new-episode notification settings, logout default retention, scoped local-only logout cleanup choices, view-only diagnostics, diagnostics redaction, absence of diagnostics export/share/copy actions, and `FLAG_SECURE` on diagnostics and logout cleanup confirmation.

Run:

```bash
./gradlew :feature:library:test :feature:settings:test
```

Expected: fails because features do not exist.

- [ ] **Step 2: Implement library and storage UI**

Library exposes Continue Watching, favorites, watch later, downloads, and offline filter while logged in. Logged-out Offline Library exposes only shared retained downloads, shared offline progress, local storage usage, and delete-download actions. Downloads is the central screen for all offline media and queue state across movies, episodes, and confirmed season queues. It groups active, queued/paused, failed/unavailable, and completed items. Rows show title, media type/episode info, effective or target quality, subtitle languages/status, size/estimate when known, progress, status reason, and state-specific actions: pause, resume, cancel remaining, retry failed, delete completed media, and manage storage. Confirmed season queues expose per-episode status from Downloads, without requiring the user to return to the season detail screen. TV Downloads uses the same model with large rows/cards, deterministic D-pad focus order, focus restoration after returning from detail/player/dialogs, and overscan-safe spacing. TV Downloads actions are simple and confirmation-based where destructive: resume, retry failed, cancel remaining, delete completed media, and manage storage. TV storage deletion supports only "Delete this download", "Delete watched completed", and "Delete all downloads". Each destructive TV action requires a confirmation dialog that states what will be deleted and that shared offline progress for deleted media will also be removed. TV sorting/filtering may help inspect storage, but deletion actions stay simple and explicit. Exclude long menus, multi-select, range selection, drag/reorder, filter-builder deletion, and dense table layouts from TV Downloads in v1. Low-storage recovery states show required space, available space, reserve requirement when known, and a Manage downloads action. Storage management may sort/filter downloads by largest size, completed/watched status, and recently played/downloaded, but must not auto-select, auto-delete, or imply deletion is required. Bulk deletes require explicit confirmation.

- [ ] **Step 3: Implement settings and diagnostics**

Settings includes account-scoped logout, subtitle enabled/language, quality preference, playback speed, and view-only diagnostics. Do not add a new-episode notification setting in v1. Mark diagnostics and logout cleanup confirmation as credential-sensitive for `FLAG_SECURE`; do not mark the general settings screen as secure. Logout shows independent optional local-only cleanup choices for shared downloaded videos/subtitles, active-profile DataStore-backed preferences, and active-profile watch/library data. Deleting downloaded videos/subtitles deletes shared offline media and shared offline progress; clearing watch/library data does not delete shared downloads/progress. No cleanup choice calls Media server mutation endpoints. Diagnostics shows only redacted operational state: installed app version, build type, version code, Git SHA if available, release channel label such as `debug-sideload`, device/API level, API base URL, auth state category, cache age, last refresh time, download queue counts, storage usage, last status category, last successful refresh age, and `Kodi-compatible request mode: enabled`.

Diagnostics requirements:
- Do not implement export, share, upload, copy-to-clipboard, attachment, bug-report bundle, HAR, screenshot, trace, or raw artifact creation from inside the app in v1.
- Last status category uses the typed network/service categories, such as unauthorized, payment/free-limit, not found, network unavailable, server failure, decode failure, or unexpected non-JSON/redirect.
- Never show credentials, auth headers, media URLs, download URLs, absolute media file paths, raw account login/email, profile keys, cookies, endpoint paths, query params, request headers, header values, auth scheme details, User-Agent value, raw response bodies, request bodies, redirect targets, or raw request/response headers.
- Keep detailed request-shape verification in tests and sanitized discovery docs, not in the app UI.
- Export/share/copy support requires a later explicit design decision and sanitizer tests first.
- Do not implement in-app update checking, update downloading, install prompting, GitHub Releases API calls, or calls to any non-Media service for update checks in v1.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :feature:library:test :feature:settings:test :app:assembleDebug
```

Expected: tests pass and app assembles.

- [ ] **Step 5: Commit**

```bash
git add feature/library feature/settings app
git commit -m "feat: add library downloads and settings"
```

---

### Task 12: Implement Startup Refresh, Smart Downloads, And Optional Notifications

**Files:**
- Create: `app/src/main/java/net/subsloth/sync/StartupRefreshCoordinator.kt`
- Create: `app/src/main/java/net/subsloth/sync/NewEpisodeWorker.kt`
- Create: `core/domain/src/main/kotlin/net/subsloth/domain/DownloadQueuePolicy.kt`
- Test: `core/domain/src/test/kotlin/net/subsloth/domain/DownloadQueuePolicyTest.kt`
- Test: `app/src/test/java/net/subsloth/sync/StartupRefreshCoordinatorTest.kt`

- [ ] **Step 1: Write failing sync tests**

Tests cover no refresh without connectivity, cached/offline data remains usable, logged-out Offline Library never runs startup refresh or metadata refresh, stored credentials are not proactively revalidated on later startup, auth expiry while offline does not block downloaded playback, shared offline progress is never server-synced directly, account-scoped progress may sync only after explicit logged-in playback and proven Kodi-compatible progress writes, startup never fetches comments, no periodic background catalog polling exists, no foreground service launches from `BOOT_COMPLETED`, explicit item and season download queues respect storage/quality/subtitles/connectivity/metered-network settings, storage policy requires known/estimated remaining download size plus `2 GB` or `10%` safety reserve before queue/resume, unknown-size items require reserve and ongoing checks, low-storage failures pause/mark items unavailable before more transfer, completed downloads are never deleted automatically to make room, one active video download exists across the app, subtitle sidecar downloads are tied to the active item workflow and do not create parallel video-like bursts, no adaptive download concurrency exists in v1, season-size preflight runs only after the user selects "Download season", size preflight uses only minimum Kodi-compatible metadata for that selected season, every season queue requires user confirmation before enqueueing, metered network use requires explicit per-queue consent, queues confirmed on unmetered networks pause before further transfer when the network becomes metered, season quality selection falls back per episode to nearest lower then nearest higher quality, ambiguous/unavailable qualities skip only that episode with a clear reason, season subtitle selection falls back per episode from preferred language to English to first available to no subtitles, subtitle failures do not fail video downloads, additional subtitle sidecars can be added later without video re-download, effective per-episode quality is exposed and persisted, equal-or-higher already downloaded season episodes are skipped/reused without network download, lower-quality existing season episodes upgrade only through safe replacement, no "don't ask again"/auto-confirm/one-tap season queueing exists, completed season episodes remain playable when other episodes fail, per-episode season status is exposed, retrying failed season items is explicit and user-driven after bounded retry exhaustion, canceling a season queue cancels remaining queued/active items but keeps completed episodes and sidecars, confirmed season queues persist across process death/restart, restart resumes only already-confirmed incomplete items after rechecking connectivity/storage/metered/auth/URL freshness, restart resume never discovers/adds new episodes or creates new queues, logout pauses incomplete confirmed queues and keeps queue state, logged-out queues never send Media requests, after login incomplete queues resume only when the authenticated session can access the same content through Kodi-compatible flows, failed retry-exhausted persisted items remain failed until explicit retry, no automatic next-episode/background smart-download queueing exists, and no new-episode notification work/channel/settings exist in v1.

Run:

```bash
./gradlew :core:domain:test :app:testDebugUnitTest
```

Expected: fails because sync classes do not exist.

- [ ] **Step 2: Implement startup refresh**

On app open, refresh stale catalog data using the Kodi-compatible startup sequence only when online and credentials exist. Catalog list caches are stale after 24 hours. Detail caches are stale after 7 days. Playback, download, and subtitle URLs are never cached. Manual refresh is available for online catalog/detail screens. Do not proactively revalidate stored credentials on startup; react to `401` or equivalent auth failures from normal requests. Logged-out Offline Library must not run startup refresh, validate credentials, refresh metadata, mutate server state, or start online-only work. Shared offline display metadata updates only during logged-in Kodi-compatible catalog, detail, play, or download flows. Shared offline progress is not copied into account-scoped progress on login and is never server-synced directly; account-scoped progress may sync only after explicit logged-in playback and proven Kodi-compatible progress write support. Never fetch comments. Use Hilt Work integration for workers that need DI, but keep workers as imperative-shell interpreters of pure `:core:domain` policies.

Kodi-verified server library state may refresh or mutate only as part of user-driven startup/explicit-refresh/detail/library actions, with the exact endpoint/method/payload shape observed in the Kodi plugin. Do not poll library state periodically.

- [ ] **Step 3: Implement explicit item and season download queues**

Enable explicit single item downloads and explicit user-confirmed season downloads only when reliable Kodi-compatible `download_url` data exists or can be fetched through the confirmed season's Kodi-compatible detail flow. Season download starts only after the user opens a season and selects "Download season". After that selection and before confirmation, the app may fetch only the minimum Kodi-compatible metadata needed to estimate download sizes for that selected season. Do not run season-size preflight during passive browsing, startup refresh, or background work. Size preflight uses low concurrency, single-flight de-duplication, bounded retries, and `429`/`Retry-After`; it must not scrape pages or call non-Kodi endpoints. The confirmation screen shows episode count, already-downloaded/skipped count when known, selected quality policy, subtitle language policy, exact/known sizes when available, partial estimate when only some sizes are known, "size unknown" for episodes without Kodi-compatible size data, metered-network warning when relevant, and clear unavailable reasons for episodes that cannot be queued. A confirmed season queue may run on metered network only when the user explicitly allows metered use for that specific queue. If a season queue was confirmed on unmetered network and the network later becomes metered, pause queued/active season items before starting more transfer and show a "paused on metered network" state. The user may explicitly allow metered resume for that existing queue; this does not change global preferences. For season downloads, selected quality is a per-episode policy, not a guarantee. Each episode tries preferred quality first, then nearest lower quality, then nearest higher quality. If no compatible quality is available or quality ordering is ambiguous, mark that episode unavailable/skipped with a clear reason. When known before confirmation, summarize quality fallback impact, such as preferred quality, episode count using fallback quality, and unavailable episode count. Per-episode final quality is shown in queue/downloads UI and persisted as the effective downloaded quality. For season downloads, subtitle selection is a per-episode policy matching playback defaults: preferred language first, then English, then first available subtitle, then no subtitles. Missing subtitles or subtitle download failure must not fail a season video download. When known before confirmation, summarize subtitle fallback impact, such as preferred language, fallback-language counts, and no-subtitle count. Additional subtitle sidecars may be added later without re-downloading video. If an episode is already downloaded in equal or higher effective quality, skip/reuse it and show "already available" in confirmation and queue UI. Do not re-download it. If an episode exists only in lower quality and the user selected a higher quality, the queue may upgrade it using the safe replacement rule: download and verify the higher-quality file first, then replace the lower-quality asset. If quality ordering is ambiguous, keep the existing playable asset and mark the upgrade unavailable/ambiguous. If size preflight fails, show the failure/unavailable reason and let the user proceed with unknown sizes or cancel. Every season download requires this confirmation screen; do not add "don't ask again", preference-based auto-confirm, or one-tap season queueing from season rows/cards in v1. v1 allows one active video download at a time across the app, including confirmed season queues; other item/episode downloads remain queued. Subtitle sidecar downloads may run only as part of the active episode/item workflow and must not create parallel video-like request bursts. Metadata/detail refresh for the active item or selected season preflight remains low-concurrency and single-flight. Do not use adaptive download concurrency based on Wi-Fi, charging, or device class in v1. Queue confirmed season episodes sequentially. Season queue items are independent: completed episodes remain playable when other episodes fail. Expose per-episode status: queued, downloading, paused, completed, skipped/already available, unavailable, or failed. After bounded retry exhaustion, retrying failed season items is explicit and user-driven: retry one failed episode or retry failed items in that season queue. Do not silently keep retrying failed season items after the user leaves or in background automation. Canceling a season queue means "cancel remaining downloads": stop queued and active items for that queue, clean up partial files for canceled/failed active items unless safe resume is possible, and keep completed episodes playable. Season queue cancellation does not delete completed episodes, shared offline progress, metadata, or subtitle sidecars; deletion remains a storage/download management action. Persist confirmed season queues across process death/app restart. On restart, resume only incomplete items that were already part of the confirmed queue after rechecking connectivity, storage, metered-network policy, credential/auth state as encountered by normal Kodi-compatible requests, and URL freshness. Restart resume must not discover or add new episodes, expand the season, refresh unrelated metadata, or create new queues. Expired URLs for persisted queue items refresh only through the relevant Kodi-compatible detail/download flow for the already-confirmed item. Failed persisted items whose bounded retry budget was exhausted remain failed until the user explicitly retries. Logout pauses incomplete confirmed queues and keeps queue state. Completed downloads remain available in the shared logged-out Offline Library. While logged out, queued downloads must not resume, refresh URLs, validate credentials, discover queue metadata, or send Media requests. After login, incomplete queue items may resume only if the persisted queue was previously confirmed and the authenticated session can access the same content through Kodi-compatible flows. If auth/content access fails, items remain paused/failed with a clear reason until the user retries or cancels remaining downloads. Use single-flight duplicate de-duplication, bounded retries, `429`/`Retry-After` handling, low-storage refusal, partial-file cleanup, and foreground download notification where required. Do not implement automatic next-episode downloads, background discovery of new episodes, periodic queue workers, unconfirmed download-all actions, or smart-download behavior in v1. Discovery may record whether Media exposes reliable new-episode metadata, but v1 does not implement new-episode notifications, a new-episode notification channel, notification settings, or new-episode notification workers. Adding automatic smart downloads or new-episode notifications later requires a separate explicit design decision.

- [ ] **Step 4: Verify**

Run:

```bash
./gradlew :core:domain:test :app:testDebugUnitTest :app:assembleDebug
```

Expected: tests pass and app assembles.

- [ ] **Step 5: Commit**

```bash
git add app core/domain
git commit -m "feat: add startup refresh and download queues"
```

---

### Task 13: Add UI, Accessibility, TV Focus, Screenshot, And Architecture Tests

**Files:**
- Create: `app/src/androidTest/kotlin/net/subsloth/NavigationSmokeTest.kt`
- Create: `feature/details/src/androidTest/kotlin/net/subsloth/details/DetailScreenshotTest.kt`
- Create: `feature/player/src/androidTest/kotlin/net/subsloth/player/PlayerSmokeTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/net/subsloth/catalog/TvFocusTest.kt`
- Create: `app/src/androidTest/kotlin/net/subsloth/AccessibilitySmokeTest.kt`
- Create: `core/domain/src/test/kotlin/net/subsloth/domain/ArchitectureBoundaryTest.kt`

- [ ] **Step 1: Add architecture boundary tests**

Assert `core:model` and `core:domain` do not import Android, Compose, Room, DataStore, Retrofit, OkHttp, or Media3 packages.

Run:

```bash
./gradlew :core:domain:test
```

Expected: architecture tests pass.

- [ ] **Step 2: Add Compose and TV tests**

Cover login, logout, movie detail without comments, series detail without comments, player controls, Continue Watching, offline library, Downloads screen on TV, subtitle settings, quality settings, speed settings, D-pad focus through TV detail and episode list, D-pad focus through TV Downloads sections/actions, focus restoration after back navigation and dialogs, remote media keys where practical, and process-death state restoration for main navigation.

- [ ] **Step 3: Add accessibility tests**

Cover meaningful labels/content descriptions, large-text resilience, contrast-critical states, visible TV focus indicator, touch target sizing on phone/tablet, and remote-only operation for TV paths.

- [ ] **Step 4: Run encrypted credential instrumented tests**

Run the Android Keystore-backed `CredentialStoreTest` on an emulator/device. Verify save/read/clear behavior, backup-exclusion resources, logout removes credential material, logout retains shared downloads/progress and active-profile preferences/watch-library data by default, deleting downloaded videos/subtitles clears shared offline media/progress, resetting preferences clears only active-profile preferences, clearing watch/library data clears only active-profile watch/library data, and other account profiles remain untouched except for intentionally shared offline media/progress deletion.

Run:

```bash
./gradlew connectedDebugAndroidTest :core:preferences:connectedDebugAndroidTest
```

Expected: tests pass on configured emulator/device.

- [ ] **Step 5: Add Roborazzi screenshot tests**

Capture movie and series detail layouts for phone, tablet, and TV dimensions using Roborazzi. Ensure comments UI is absent. Use real device screenshots only for TV focus/rendering issues that Roborazzi cannot represent. Screenshots for credential-sensitive screens must use fake credentials and redacted state; non-sensitive browsing, library, settings, and playback screenshots remain allowed.

- [ ] **Step 6: Commit**

```bash
git add app feature core
git commit -m "test: add UI focus and architecture tests"
```

---

### Task 14: Add Performance, Baseline Profiles, And Device Acceptance

**Files:**
- Create: `app/src/androidTest/kotlin/net/subsloth/benchmark/StartupBaselineProfileGenerator.kt`
- Create: `app/src/androidTest/kotlin/net/subsloth/benchmark/AppMacrobenchmark.kt`
- Create: `docs/testing/device-acceptance.md`

- [ ] **Step 1: Add baseline profile generation**

Profile startup, catalog scroll, detail open, and playback start.

Run:

```bash
./gradlew :app:generateBaselineProfile
```

Expected: baseline profile generated for release builds.

- [ ] **Step 2: Add macrobenchmarks**

Measure startup, home load from cache, series detail open, movie detail open, and playback start. Include Android TV 8 as a required manual/device acceptance target.

Run:

```bash
./gradlew :app:connectedBenchmarkAndroidTest
```

Expected: benchmark results generated on attached device.

- [ ] **Step 3: Write manual acceptance checklist**

Create `docs/testing/device-acceptance.md` with required checks for Android TV 8, Android tablet 13, and Android phone 16 from the spec.

- [ ] **Step 4: Commit**

```bash
git add app docs/testing
git commit -m "test: add performance and device acceptance coverage"
```

---

### Task 15: Final Verification And Release Build

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `.github/workflows/release-please.yml`
- Create: `version.txt`
- Create: `CHANGELOG.md`
- Modify: `README.md`
- Create: `docs/development.md`
- Create: `docs/release.md`

- [ ] **Step 1: Add project docs**

Document JDK 17 as the required build runtime, Android Studio latest stable, Gradle wrapper usage, local env vars for optional live drift tests, how to run offline tests, how to install debug APK, how to run device acceptance, release-please workflow, debug-signed sideload APK naming, manual install/update, rollback, changelog expectations, and that release discovery happens manually through GitHub Releases outside the app. Explicitly state that JDK 25 is not the project baseline unless AGP, Gradle, Kotlin, Android Studio, and CI images officially support it and full verification passes.

- [ ] **Step 2: Add required offline CI workflow**

Create `.github/workflows/ci.yml` for pull requests and pushes to `main`.

CI requirements:
- Use JDK 17 and the checked-in Gradle wrapper.
- Run Gradle wrapper validation.
- Restore/save Gradle caches without caching credentials, signing keys, build scans, browser traces, HAR files, screenshots, or live drift artifacts.
- Run dependency verification/locking checks.
- Run OpenAPI validation/generation before mapper/domain tests.
- Run `check`, `lintDebug`, `testDebugUnitTest`, and `assembleDebug`.
- Run secret/artifact scanning for credentials, Basic auth headers, signed media URLs, `.playwright-cli/`, HAR files, browser traces, screenshots, and signed APK material.
- Run the no-comments invariant check.
- Run Kodi-parity invariant tests that prove production requests use Kodi-style headers/metadata and never web-only endpoints or WebView verification flows.
- Do not set Media credentials, contact Media, run live drift tests, upload signed URLs/credentials, or publish browser artifacts.

- [ ] **Step 3: Document local-only live drift execution**

Do not create `.github/workflows/live-drift.yml` in v1. GitHub Actions must not store Media credentials, contact Media, or run credentialed live drift tests.

Local live drift requirements:
- Run only from a developer machine with local environment variables `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD`.
- Run only the gated live drift test task.
- Treat missing local env vars as skipped tests, not failures.
- Write only sanitized response-shape/capability summaries to docs or fixtures. Do not persist raw responses, credentials, auth headers, signed URLs, HAR files, screenshots, or browser traces.
- Adding a protected manual GitHub live-drift workflow requires a later explicit design decision.

- [ ] **Step 4: Add release-please workflow**

Create `.github/workflows/release-please.yml`, `version.txt`, and `CHANGELOG.md`.

Release Please requirements:
- Use `googleapis/release-please-action@v4`.
- Use a dedicated `RELEASE_PLEASE_TOKEN` PAT or GitHub App token, not the default `GITHUB_TOKEN`.
- Run on pushes to `main`.
- Use `release-type: simple`.
- Treat the repository as one releasable product.
- Maintain `version.txt` and `CHANGELOG.md`.
- Use tags in the form `vX.Y.Z`.
- Allow all Conventional Commit release-worthy changes, including docs/spec changes, to produce repository releases.
- Before the app scaffold exists, releases may be changelog-only.
- When `release_created == true` and the app scaffold exists, build `assembleDebug` in the same workflow and upload the debug-signed APK to the GitHub Release.
- Name the APK `subsloth-vX.Y.Z-debug-<shortsha>.apk`.
- Derive Android `versionName` from `version.txt`.
- Derive Android `versionCode` deterministically from SemVer.
- Never run live Media tests as part of release artifact creation.
- Upload only the debug-signed APK and non-sensitive release notes/checksums.
- Do not require release signing keys or signing passwords in v1.
- Dedicated release signing with a release key is deferred until a later explicit decision.

- [ ] **Step 5: Run full verification locally**

Run:

```bash
./gradlew :core:network:openApiValidate :core:network:openApiGenerate check lintDebug testDebugUnitTest assembleDebug
```

Expected: all checks pass and the debug APK builds.

- [ ] **Step 6: Run optional local live drift tests when credentials exist**

Run:

```bash
SUBSLOTH_LOGIN="$SUBSLOTH_LOGIN" SUBSLOTH_PASSWORD="$SUBSLOTH_PASSWORD" ./gradlew :testing:api-contract:test
```

Expected with local credentials: live drift tests pass or report precise API drift. Expected without local credentials: live tests skip.

- [ ] **Step 7: Confirm no comments implementation exists**

Run:

```bash
rg -n "comment|comments|spoiler" app core feature testing
```

Expected: no production code supports comments; any matches are only tests asserting comments are excluded. OpenAPI/docs may mention comments only to document intentional exclusion.

- [ ] **Step 8: Commit**

```bash
git add README.md docs app core feature testing api .github
git commit -m "docs: add development and release verification"
```
