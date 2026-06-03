# AGP / Gradle / Kotlin Migration

## Current Baseline

| Component | Version |
|---|---|
| AGP | 9.2.1 |
| Gradle | 9.5.1 |
| Kotlin | 2.3.21 |
| KSP | 2.3.7 |
| Compose BOM | 2026.05.00 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |
| JDK (compile) | 17 (via `jvmToolchain(17)`) |
| JDK (daemon) | 25 (Nix flake `JAVA_HOME`) |
| Detekt | 2.0.0-alpha.3 |
| Spotless | 8.5.1 |

## Convention Plugins (6)

All in `build-logic/convention/src/main/kotlin/`:

- **`subsloth.android.application`** — `com.android.application`, spotless, detekt, `kotlin.plugin.power-assert`. Sets SDK, JUnit 5, lint `abortOnError` + `warningsAsErrors`.
- **`subsloth.android.application.compose`** — extends app plugin, adds `kotlin.plugin.compose` + `buildFeatures.compose`.
- **`subsloth.android.library`** — mirrors app plugin, no app-specific lint disables. Adds ktlint exception for PascalCase Composables.
- **`subsloth.android.library.compose`** — extends library plugin, adds compose.
- **`subsloth.android.feature`** — extends `android.library.compose`. Adds Compose BOM, Material3, Lifecycle, Navigation3 deps. Used by `:feature:*`.
- **`subsloth.jvm.library`** — pure JVM. No Android SDK or lint config.

## Version Catalog

`gradle/libs.versions.toml`: `[versions]` (all versions), `[libraries]` (coordinates keyed by name), `[plugins]` (use `alias(libs.plugins.*)`). To add/update: edit version key, update references, run `./gradlew tasks` to verify.

## Upgrade Procedures

### AGP
1. Read AGP release notes for breaking changes and behavior flags
2. Update `agp` version in `gradle/libs.versions.toml`
3. Check convention plugin compat
4. Update Kotlin if AGP requires newer minimum
5. Run `./gradlew clean build && test`

### Gradle
`./gradlew wrapper --gradle-version X.Y`. Check `gradle-wrapper.properties`. Verify AGP compat.

### Kotlin
Update `kotlin` version in TOML. Compose compiler ships with Kotlin (plugin `kotlin.plugin.compose`) — no separate version. Update KSP at same time. Run `./gradlew clean build`, review detekt baseline.

### JDK Toolchain
Daemon: JDK 25 (Nix `JAVA_HOME`). Compile: JDK 17 via `jvmToolchain(17)` in all 6 plugins. Discovery via `JAVA17_HOME` env var. To change: update `jvmToolchain(N)` in all plugins + `build-logic/convention/build.gradle.kts`, update `JAVA17_HOME` in `flake.nix`, update `fromEnv` in `gradle.properties`.

## Migration Checklist

### Pre-migration
- Read changelogs for AGP, Gradle, Kotlin, Compose
- Check convention plugin compat
- Branch off main, backup `gradle/libs.versions.toml`

### Migration
- Update versions in `gradle/libs.versions.toml`
- `./gradlew clean build` → fix errors
- Update convention plugins if API changed
- `./gradlew test && detekt`

### Post-migration
- `./gradlew clean build` final time
- Commit: `chore: upgrade AGP to X.Y.Z`

## Rollback

`git checkout -- .` (uncommitted) or `git revert HEAD` (committed). Revert version keys in TOML, `./gradlew clean build`.

## References
- `gradle/libs.versions.toml`, `gradle-wrapper.properties`
- `build-logic/convention/src/main/kotlin/*.gradle.kts` (6 plugins)
- `settings.gradle.kts`, `gradle.properties`, `flake.nix`
