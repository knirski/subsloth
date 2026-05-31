## 1. Android Scaffold

- [x] 1.1 Create the Gradle wrapper with Gradle 9.5.0.
- [x] 1.2 Add root Gradle Kotlin DSL files, Gradle properties, version catalog, and precompiled convention plugins under `build-logic/`.
- [x] 1.3 Create app, core, feature, and testing modules with namespace ownership under `subsloth`.
- [x] 1.4 Configure JDK 17 bytecode target (daemon runs JDK 25 via Nix flake), AGP 9.2, `minSdk 26`, `targetSdk 36`, and `compileSdk 36`.

## 2. Project Guardrails

- [x] 2.1 Add stable dependency policy and document all library choices in `docs/policies/dependency-policy.md`.
- [x] 2.2 Add dependency verification and dependency locking (`gradle/verification-metadata.xml`, lock files).
- [x] 2.3 Add secret and artifact scanning for credentials, auth headers, signed media URLs, browser traces, HAR files, and authenticated screenshots.
- [x] 2.4 Add v1 scope-exclusion checks or documentation for Chromecast, external player handoff, public-folder downloads, Kodi NFO export, Play Store billing, intro/recap skip, and user-visible multi-profile switching.

## 3. API Contract

- [x] 3.1 Preserve and refine `api/subsloth.openapi.yaml` from Kodi-compatible evidence only.
- [x] 3.2 Configure OpenAPI validation and handwritten Media DTOs in `:core:network`.
- [x] 3.3 Add sanitized movie, show, show-detail, movie-detail, and episode-detail fixtures.
- [x] 3.4 Add offline fixture schema validation in `:core:network` (JUnit 4 + Truth).
- [x] 3.5 Add optional live drift tests gated by `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD` (JUnit 4 `Assume` + Truth).

## 4. Verification

- [x] 4.1 Run `./gradlew projects`.
- [x] 4.2 Run `./gradlew :core:network:openApiValidate`.
- [x] 4.3 Run `./gradlew :core:network:test`.
- [x] 4.4 Run `openspec validate foundation-api-contract --strict`.
