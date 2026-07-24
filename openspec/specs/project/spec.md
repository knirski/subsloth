# project Specification

## Purpose
Define the multiplatform project baseline, module/convention boundaries, dependency guardrails, and v1 scope/security constraints for subsloth.
## Requirements
### Requirement: Android Project Baseline
The project SHALL build a greenfield multiplatform KMP app with application id and namespace `net.subsloth` for Android (phone, tablet, TV), desktop (JVM), and web (Wasm JS).

#### Scenario: Scaffolded modules are present
- **WHEN** `./gradlew projects` is executed
- **THEN** the listed modules include `:androidApp`, `:desktopApp`, `:webApp`, `:core:model`, `:core:domain`, `:core:network`, `:core:data`, `:core:database`, `:core:preferences`, `:core:media`, `:core:ui`, `:feature:auth`, `:feature:catalog`, `:feature:details`, `:feature:player`, `:feature:library`, and `:feature:settings`

#### Scenario: App identity is locked
- **WHEN** the app module is configured
- **THEN** its namespace and application id are both `net.subsloth`

### Requirement: Repository Scaffold Layout
The project scaffold SHALL use Gradle Kotlin DSL, a shared `build-logic` build, a single version catalog, and an OpenAPI contract source.

#### Scenario: Scaffold files are created
- **WHEN** the initial scaffold is implemented
- **THEN** the repo includes `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `build-logic/`, `api/subsloth.openapi.yaml`, and a `build.gradle.kts` for every module listed in the Android Project Baseline

---

### Requirement: Toolchain Baseline
The project SHALL treat `gradle/wrapper/gradle-wrapper.properties` (Gradle version), `gradle/libs.versions.toml` (AGP, Kotlin, `compileSdk`, `minSdk`, `targetSdk`), and `flake.nix` (JDK roles) as the single executable source of truth for toolchain versions. Prose documentation MAY describe current values for readability but SHALL NOT be treated as normative when it disagrees with the catalog, wrapper, or flake; a document found to disagree SHALL be corrected to match the executable source rather than the reverse.

#### Scenario: Build is reproducible from the Nix shell
- **WHEN** a developer runs `./gradlew help` from the project directory on a clean machine (with `direnv allow` completed and no system JDK configured)
- **THEN** the build succeeds — all required JDK versions are supplied exclusively by the Nix flake

#### Scenario: Emitted bytecode targets Java 17
- **WHEN** any compiled module artifact is inspected
- **THEN** its class files are at Java 17 level regardless of the JDK running the Gradle daemon

#### Scenario: Toolchain doc drift is corrected toward the catalog
- **WHEN** a document states a Gradle, AGP, Kotlin, `compileSdk`, `minSdk`, or `targetSdk` value
- **THEN** the stated value matches `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml` at the time of writing, or the document is corrected

### Requirement: Convention Plugin Discipline
Every module SHALL apply exactly one project-level convention plugin. Raw AGP, Kotlin JVM, Kotlin Wasm, or Kotlin Multiplatform plugin IDs shall not appear directly in module `build.gradle.kts` files.

#### Scenario: Module applies a convention plugin
- **WHEN** a module `build.gradle.kts` is inspected
- **THEN** its `plugins {}` block contains exactly one `subsloth.*` plugin id and no raw `com.android.library`, `com.android.application`, or `org.jetbrains.kotlin.jvm` id

#### Scenario: Shared config lives in build-logic only
- **WHEN** any module `build.gradle.kts` is inspected
- **THEN** it contains no `compileSdk`, `minSdk`, `compileOptions`, or `jvmToolchain` declarations — those are defined once in the convention plugins

---

### Requirement: Functional Core Isolation
The `:core:model` and `:core:domain` modules SHALL be platform-agnostic. They shall not carry Android runtime dependencies.

#### Scenario: Model and domain have no Android deps
- **WHEN** the dependency graph of `:core:model` or `:core:domain` is resolved
- **THEN** no `com.android.*` or `androidx.*` runtime artifact appears in any production configuration

---

### Requirement: Namespace Convention
Every module with an Android target or `androidMain` source set SHALL declare a namespace that matches its Gradle module path under the `net.subsloth` root.

#### Scenario: Namespace matches module path
- **WHEN** an Android module's `android { namespace }` is read
- **THEN** it equals `net.subsloth` followed by the module path segments joined with `.` (e.g. `:core:network` → `net.subsloth.core.network`, `:feature:player` → `net.subsloth.feature.player`)

---

### Requirement: Superseded Libraries Excluded
The dependency graph SHALL contain none of the libraries replaced by the current stack choices.

#### Scenario: Banned artifacts are absent
- **WHEN** the full production dependency graph is resolved across all modules
- **THEN** no configuration includes any artifact from `io.arrow-kt`, `com.squareup.moshi`, `com.google.dagger:hilt-*`, `androidx.hilt`, `androidx.navigation:navigation-compose`, or `io.kotest`

---

### Requirement: Dependency Policy
The project SHALL prefer stable dependency releases and SHALL document any pre-release exception in `docs/policies/dependency-policy.md` in the same commit that introduces it.

#### Scenario: No undocumented pre-release runtime deps
- **WHEN** a module `build.gradle.kts` is inspected
- **THEN** every `implementation` and `api` dependency resolves to a stable artifact version unless an active exception is listed in `docs/policies/dependency-policy.md`

---

### Requirement: Supply Chain Checks
The project SHALL scan for credentials and sensitive artifacts before any fixture or dependency change is treated as release-ready.

#### Scenario: Sensitive artifacts are scanned
- **WHEN** CI or local verification runs
- **THEN** credentials, Basic auth headers, signed media URLs, browser traces, HAR files, and authenticated screenshots are rejected as committed artifacts

---

### Requirement: Internal Sideload Distribution
The v1 project SHALL target personal/internal sideload distribution and SHALL not include analytics SDKs, crash-reporting SDKs, telemetry upload, in-app update checks, or remote diagnostics upload.

#### Scenario: Release channel is internal
- **WHEN** a release artifact is produced for v1
- **THEN** it is a debug-signed sideload APK intended for internal installation only

---

### Requirement: V1 Scope Exclusions
The v1 project SHALL exclude Chromecast, external player handoff, public-folder downloads, Kodi NFO export, Play Store billing, intro/recap skip, and user-visible multi-profile switching.

#### Scenario: Out-of-scope feature is deferred
- **WHEN** an implementation task encounters any excluded feature
- **THEN** it is deferred unless a later explicit OpenSpec change adds it

