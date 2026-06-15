# SubSloth Dependency Policy

## Stable Dependency Policy

All production dependencies must use stable, publicly released versions. A "stable" dependency is one that has reached its first non-preview, non-release-candidate, non-milestone release in Maven Central or Google Maven.

### Rules

1. **Prefer stable versions.** No `-alpha`, `-beta`, `-rc`, `-M`, or milestone releases in production dependencies unless listed under Active Exceptions below.
2. **Pin exact versions.** All versions are declared in `gradle/libs.versions.toml` under `[versions]`. No dynamic version ranges (e.g. `1.+`).
3. **Update deliberately.** A version may be updated only when the new release's release notes confirm compatibility with the project's Gradle, AGP, Kotlin, and Compose BOM baseline. Update the version catalog and this policy in the same commit.
4. **Prefer Compose BOM.** Compose UI dependencies use the BOM version rather than individual artifact version overrides, unless an artifact requires a separate version.
5. **Keep test-only dependencies lean.** Libraries used only for testing may use pre-release versions only when no stable release exists and the feature is gated to test source sets.

---

## Active Exceptions

None. All production dependencies are on stable releases.

---

## Closed Exceptions

### TV Foundation (resolved May 2026)

`androidx.tv:tv-foundation` was exempted from the stable rule while no stable release existed.

- **Was allowed at:** `1.0.0-rc01`
- **Resolved at:** `tv-foundation:1.0.0` and `tv-material:1.1.0` both reached stable on 6 May 2026.
- **Status:** Exception closed. Both artifacts are now on stable versions in the version catalog.

---

## Dependency Verification

Secret and artifact scanning rules are enforced in CI and cover credentials, signed URLs, HAR files, and authenticated screenshots.

## Modern Stack Choices


The following libraries represent deliberate choices replacing previously common alternatives. No module `build.gradle.kts` file shall import the superseded alternatives.

| Layer | Chosen | Superseded |
|---|---|---|
| DI | Manual constructor injection (no DI framework) | Hilt, Metro, Dagger |
| Navigation | Navigation3 1.1 (`androidx.navigation3`) | Navigation Compose |
| JSON serialization | kotlinx.serialization | Moshi, Gson |
| HTTP | Ktor 3.5 (`io.ktor:ktor-client-core`) | Retrofit, OkHttp |
| FP / error modelling | Kotlin `Result<T>`, `sealed interface` | Arrow |
| Testing assertions | Kotlin Power-Assert (`org.jetbrains.kotlin.plugin.power-assert`) | Truth, Kotest |
| Test runner | JUnit 5 | Kotest Engine |
| Screenshot tests | Roborazzi | — |

---

## Version Catalog Structure

All dependency coordinates live in `gradle/libs.versions.toml`:

```toml
[versions]
library-name = "X.Y.Z"

[libraries]
library-name = { module = "group:artifact", version.ref = "library-name" }

[plugins]
plugin-name = { id = "plugin.id", version.ref = "pluginVersion" }
```

Library aliases use kebab-case. No dependency coordinates are hardcoded in module `build.gradle.kts` files.

---

## Review Process

1. A developer proposes a dependency change in a PR.
2. The PR updates `gradle/libs.versions.toml`, and if needed `docs/policies/dependency-policy.md` and ProGuard rules.
3. The PR runs `./gradlew :core:network:test` and relevant build tasks before merge.
