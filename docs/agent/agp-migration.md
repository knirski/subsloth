# AGP / Gradle / Kotlin Migration Guide

## Overview

Subsloth uses Gradle Kotlin DSL with a version catalog and build-logic
convention plugins. Every module applies one of 6 convention plugins
instead of repeating build configuration. The version catalog in
`gradle/libs.versions.toml` is the single source of truth for all
dependency and plugin versions.

## Current Baseline

| Component | Version |
|---|---|
| AGP | 9.2.1 |
| Gradle | 9.5.1 |
| Kotlin | 2.3.21 |
| KSP | 2.3.7 |
| Compose BOM | 2026.05.00 |
| compileSdk | 36 |
| minSdk | 26 |
| targetSdk | 36 |
| JDK (compile) | 17 (via `jvmToolchain(17)`) |
| JDK (daemon) | 25 (set by Nix flake `JAVA_HOME`) |
| Detekt | 2.0.0-alpha.3 |
| Spotless | 8.5.1 |
| ktlint | 1.5.0 |

## Convention Plugin Inventory

Files live in `build-logic/convention/src/main/kotlin/`.

**`subsloth.android.application.gradle.kts`** -- Android application
module. Applies `com.android.application`, `spotless`, `detekt`,
`kotlin.plugin.power-assert`. Sets `compileSdk=36`, `minSdk=26`,
`targetSdk=36`, `applicationId=net.subsloth`. Configures JUnit 5 via
`useJUnitPlatform()`. Enables `lint.abortOnError`, `warningsAsErrors`,
`checkAllWarnings`. Disables lint rules `DataExtractionRules`,
`MissingApplicationIcon`, `NotShrinkingResources`, `GradleDependency`.
Runs ktlint on Kotlin sources and Gradle scripts. Points detekt at
`config/detekt.yml` and `config/detekt-baseline.xml`. Loads custom
detekt rules from `:testing:detekt-rules` and `compose-rules-detekt`.

**`subsloth.android.application.compose.gradle.kts`** -- Extends the
application plugin. Applies `kotlin.plugin.compose` and enables
`buildFeatures.compose`.

**`subsloth.android.library.gradle.kts`** -- Android library module.
Mirrors the application plugin but without `DataExtractionRules`,
`MissingApplicationIcon`, or `NotShrinkingResources` lint disables
(those are app-specific). Adds ktlint exception for PascalCase
Composable function names via
`ktlint_function_naming_ignore_when_annotated_with=Composable`.

**`subsloth.android.library.compose.gradle.kts`** -- Extends the
library plugin. Applies `kotlin.plugin.compose` and enables
`buildFeatures.compose`.

**`subsloth.android.feature.gradle.kts`** -- Feature module plugin.
Extends `android.library.compose`. Adds feature-layer dependencies:
Compose BOM, Material3, Lifecycle (runtime-compose + viewmodel-compose),
and Navigation3 (runtime + ui + viewmodel). Used by modules under
`:feature:*`.

**`subsloth.jvm.library.gradle.kts`** -- Pure JVM module (no Android).
Applies `kotlin.jvm`, `spotless`, `detekt`, `kotlin.plugin.power-assert`.
Same `jvmToolchain(17)`, `allWarningsAsErrors`, JUnit 5, ktlint, and
detekt config as the Android plugins. No Android SDK or lint config.

## Version Catalog

The catalog at `gradle/libs.versions.toml` declares:

- `[versions]` -- all version numbers (AGP, Kotlin, KSP, Compose BOM,
  Room, Detekt, Spotless, etc.)
- `[libraries]` -- module coordinates keyed by name, referencing versions
- `[plugins]` -- plugin declarations used via `alias(libs.plugins.*)`

To add or update a version: edit the `[versions]` key, then update the
`[libraries]` or `[plugins]` entry that references it. Run `./gradlew
sync` to verify the catalog resolves.

## AGP Upgrade Procedure

1. Read the AGP release notes for breaking changes and behavior flags.
2. Update `agp` in `gradle/libs.versions.toml` `[versions]`.
3. Check convention plugin compatibility -- AGP API changes may require
   updates in `build-logic/convention/src/main/kotlin/`.
4. Update Kotlin if AGP requires a newer minimum.
5. Update Compose compiler (it ships with Kotlin, so a Kotlin bump is
   sufficient).
6. Run `./gradlew clean build` and fix compile errors.
7. Run all tests: `./gradlew test` and `./gradlew connectedCheck` if
   an emulator is available.

## Gradle Upgrade

Run `./gradlew wrapper --gradle-version X.Y` from the project root.
Check `gradle/wrapper/gradle-wrapper.properties` to confirm the new
distribution URL. Verify compatibility with AGP before committing.

AGP-Gradle compat matrix: AGP requires a minimum Gradle version. If you
upgrade Gradle past what AGP supports, the build fails at configuration
time with a clear error.

## Kotlin Upgrade

Update `kotlin` in `gradle/libs.versions.toml` `[versions]`. The Compose
compiler ships as part of Kotlin (plugin `kotlin.plugin.compose`), so no
separate version is needed. Run `./gradlew clean build`. Detekt may fire
new rules on upgraded Kotlin -- review and update
`config/detekt-baseline.xml` if needed.

KSP versions are tied to Kotlin. Update `ksp` at the same time if a
compatible release exists.

## JDK Toolchain

The Gradle daemon runs on JDK 25 (`JAVA_HOME` from Nix flake). The
compile toolchain is JDK 17, set via `jvmToolchain(17)` in every
convention plugin. The toolchain is discovered through
`JAVA17_HOME` env var, configured in `gradle.properties`:

```
org.gradle.java.installations.fromEnv=JAVA17_HOME
```

To change the compile toolchain version:
1. Update `jvmToolchain(N)` in all 6 convention plugins.
2. Update `build-logic/convention/build.gradle.kts` `jvmToolchain(N)`.
3. Update `JAVA17_HOME` in `flake.nix` if the JDK is not available on
   the host.
4. Update `gradle.properties` `fromEnv` if the env var name changes.

## Migration Checklist

### Pre-migration
- [ ] Read changelogs for AGP, Gradle, Kotlin, and Compose.
- [ ] Check convention plugin compat (public API changes, removed APIs).
- [ ] Create a new branch off main.
- [ ] Backup the working `gradle/libs.versions.toml` (git diff will
      suffice).

### Migration
- [ ] Update version numbers in `gradle/libs.versions.toml`.
- [ ] Run `./gradlew clean build` -- fix compile and configuration
      errors.
- [ ] Update convention plugins if AGP/Kotlin API changed.
- [ ] Run `./gradlew test` -- fix unit test failures.
- [ ] Run `./gradlew detekt` -- update baseline or fix new violations.

### Post-migration
- [ ] Run screenshot tests (Roborazzi) if UI modules changed.
- [ ] Run lint: `./gradlew lint` -- verify no new warnings.
- [ ] Run `./gradlew build` clean one final time.
- [ ] Commit with message: `chore: upgrade AGP to X.Y.Z` or similar.

## Rollback

If the migration fails at any point: `git checkout -- .` to discard
changes, or `git revert HEAD` if committed. If only the version catalog
changed, revert the version keys in `gradle/libs.versions.toml` and
re-run `./gradlew clean build`.

## References

- `gradle/libs.versions.toml` -- version catalog
- `gradle/wrapper/gradle-wrapper.properties` -- Gradle distribution
- `build-logic/convention/src/main/kotlin/*.gradle.kts` -- 6 convention
  plugins
- `build-logic/convention/build.gradle.kts` -- build-logic module
- `settings.gradle.kts` -- project layout and plugin management
- `gradle.properties` -- JDK toolchain discovery, JVM args
- `flake.nix` -- Nix dev shell with pinned JDK 17 and JDK 25
