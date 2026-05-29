# KMP Core Shared (Scope A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert `:core:model` and `:core:domain` from JVM-only libraries to Kotlin Multiplatform libraries targeting JVM + Apple (iosArm64, iosSimulatorArm64, iosX64), enabling them to be shared with an iOS app while keeping the rest of the Android project unchanged.

**Architecture:** The Functional Core / Imperative Shell architecture already drew the right boundary — the two pure modules have zero Android framework imports. The migration is primarily a build-config change: declare them as `kotlin.multiplatform`, move source from `src/main/` → `src/commonMain/`, and wire the Compose annotations (`@Stable`/`@Immutable`) from their multiplatform coordinates so they compile on all targets.

**Tech Stack:** Kotlin 2.3.21, Kotlin Multiplatform, Compose Multiplatform runtime (for `@Stable`/`@Immutable`), kotlinx-collections-immutable (already multiplatform), kotlinx-datetime (already multiplatform), JUnit 5 (JVM target only), Spotless, Detekt.

**Scope boundary:** Only `:core:model` and `:core:domain`. Nothing else moves. The convention plugin `subsloth.kmp.library` is created alongside existing conventions. The imperative shell (`:core:database`, `:core:network`, `:core:media`, `:core:preferences`, `:core:ui`, all features, `:app`) stays exactly as-is on Android.

---

## File Map

### New files
| File | Purpose |
|---|---|
| `build-logic/convention/src/main/kotlin/subsloth.kmp.library.gradle.kts` | KMP library convention plugin: JVM + iOS targets, Spotless, Detekt, test config |
| `core/model/src/iosMain/kotlin/net/subsloth/core/model/Platform.explicit.kt` | Explicit declaration of platform markers (empty — no actual platform code needed) |

### Modified files
| File | Change |
|---|---|
| `build-logic/convention/build.gradle.kts` | Add `kotlin.multiplatform` gradlePlugin to compileOnly deps |
| `gradle/libs.versions.toml` | Add multiplatform Compose runtime dependency entry |
| `settings.gradle.kts` | No change needed (modules are already included by name) |
| `build.gradle.kts` (root) | Add `org.jetbrains.kotlin.multiplatform` plugin with `apply false` |
| `core/model/build.gradle.kts` | Change `subsloth.jvm.library` → `subsloth.kmp.library`, declare targets, update dependencies |
| `core/domain/build.gradle.kts` | Change `subsloth.jvm.library` → `subsloth.kmp.library`, declare targets, update dependencies |

### Moved files (content unchanged)
| From | To |
|---|---|
| `core/model/src/main/kotlin/...` | `core/model/src/commonMain/kotlin/...` |
| `core/domain/src/main/kotlin/...` | `core/domain/src/commonMain/kotlin/...` |
| `core/domain/src/test/kotlin/...` | `core/domain/src/jvmTest/kotlin/...` |

---

## Task 1: Add KMP plugin to build-logic and root

**Files:**
- Modify: `build-logic/convention/build.gradle.kts`
- Modify: `build.gradle.kts` (root)

**Why:** The convention plugin needs the KMP Gradle plugin on its classpath. The root build needs to declare it as available (but not apply it globally).

- [ ] **Step 1: Add KMP Gradle plugin to convention plugin dependencies**

In `build-logic/convention/build.gradle.kts`, add `compileOnly(libs.kotlin.multiplatform.gradlePlugin)` to the dependency block:

```kotlin
dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.multiplatform.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    implementation(libs.spotless.gradlePlugin)
    implementation(libs.detekt.gradlePlugin)
    implementation(libs.kotlin.power.assert.gradlePlugin)
}
```

- [ ] **Step 2: Add KMP plugin to version catalog**

In `gradle/libs.versions.toml`, add to the `[libraries]` section:

```toml
kotlin-multiplatform-gradlePlugin = { module = "org.jetbrains.kotlin:kotlin-gradle-plugin", version.ref = "kotlin" }
```

Note: This uses the same `kotlin` version ref (2.3.21) as the existing `kotlin-gradlePlugin` entry. The `kotlin-gradlePlugin` library already exists; we're adding an alias with a more descriptive name so it's clear in the convention plugin what it's for. The module is the same — the KMP plugin (`org.jetbrains.kotlin.multiplatform`) is part of `kotlin-gradle-plugin`.

- [ ] **Step 3: Declare KMP plugin in root build.gradle.kts**

In `build.gradle.kts` (root), add the KMP plugin to the `plugins` block:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.spotless) apply false
}
```

- [ ] **Step 4: Add KMP plugin to version catalog plugins section**

In `gradle/libs.versions.toml`, add to `[plugins]`:

```toml
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

- [ ] **Step 5: Verify current build still compiles**

Run: `./gradlew :core:model:compileKotlin :core:domain:compileKotlin`
Expected: BUILD SUCCESSFUL (no actual change yet — just confirming the baseline)

---

## Task 2: Create `subsloth.kmp.library` convention plugin

**Files:**
- Create: `build-logic/convention/src/main/kotlin/subsloth.kmp.library.gradle.kts`

**Why:** This convention plugin encapsulates all KMP boilerplate — target setup, common dependencies, spotless, detekt, test configuration — so that `:core:model` and `:core:domain` only need a one-line plugin reference.

- [ ] **Step 1: Write the convention plugin**

```kotlin
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.diffplug.spotless")
    id("dev.detekt")
    id("org.jetbrains.kotlin.plugin.power-assert")
}

val libs = the<VersionCatalogsExtension>().named("libs")
val ktlintVersion = libs.findVersion("ktlint").get().toString()

kotlin {
    jvmToolchain(17)

    jvm()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    compilerOptions {
        allWarningsAsErrors = true
    }

    sourceSets {
        commonMain.dependencies {
            // kotlinx-collections-immutable is already multiplatform.
            implementation(libs.findLibrary("kotlinx-collections-immutable").get())
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            testRuntimeOnly("org.junit.platform:junit-platform-launcher:${libs.findVersion("junitPlatform").get().requiredVersion}")
        }

        jvmTest.dependencies {
            implementation(platform(libs.findLibrary("junit-bom").get()))
            implementation(libs.findLibrary("junit-jupiter-api").get())
            testRuntimeOnly(libs.findLibrary("junit-jupiter-engine").get())
        }
    }
}

@OptIn(ExperimentalKotlinGradlePluginApi::class)
powerAssert {
    functions =
        listOf(
            "kotlin.assert",
            "kotlin.require",
            "kotlin.check",
            "kotlin.test.assertTrue",
            "kotlin.test.assertFalse",
            "kotlin.test.assertEquals",
            "kotlin.test.assertNotEquals",
            "kotlin.test.assertNull",
            "kotlin.test.assertNotNull",
        )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

spotless {
    kotlin {
        target("src/*/kotlin/**/*.kt")
        ktlint(ktlintVersion)
        toggleOffOn()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(ktlintVersion)
    }
}

detekt {
    config.setFrom(file("${rootProject.layout.projectDirectory}/config/detekt.yml"))
    basePath = rootProject.layout.projectDirectory.asFile
    baseline.set(file("${rootProject.layout.projectDirectory}/config/detekt-baseline.xml"))
}

dependencies {
    detektPlugins(project(":testing:detekt-rules"))
    detektPlugins(libs.findLibrary("compose-rules-detekt").get())
}
```

**Key design decisions:**
- `jvm()` + `iosArm64()` + `iosSimulatorArm64()` + `iosX64()` covers all iOS targets. Skip `iosX64` if you don't care about the simulator-on-Intel edge case.
- JUnit 5 is configured only for `jvmTest`. iOS tests use `kotlin.test` (common) + `test-annotations-common`. The `commonTest` source set gets the basic `kotlin("test")` dependency.
- `@Stable`/`@Immutable` is NOT added here — it's module-specific (only `:core:model` needs it). Each module adds its own `commonMain` deps on top.
- Spotless targets `src/*/kotlin/**/*.kt` which covers all KMP source sets (`commonMain`, `jvmMain`, `iosMain`, etc.).
- `power-assert` is configured the same way as the existing `subsloth.jvm.library` and `subsloth.android.library`.

- [ ] **Step 2: Verify convention plugin compiles**

Run: `./gradlew :build-logic:convention:compileKotlin`
Expected: BUILD SUCCESSFUL

---

## Task 3: Convert `:core:model` to KMP

**Files:**
- Modify: `core/model/build.gradle.kts`
- Move: `core/model/src/main/kotlin/...` → `core/model/src/commonMain/kotlin/...`
- Create: `core/model/src/jvmMain/kotlin/net/subsloth/core/model/ModelJvm.kt` (empty marker — may be needed to satisfy KMP source set existence)

**Why:** The model module is the innermost shared type library. All `@Stable`/`@Immutable` annotations come from `androidx.compose.runtime` which publishes multiplatform artifacts under `org.jetbrains.compose.runtime:runtime`.

- [ ] **Step 1: Move source to commonMain**

```bash
# Create commonMain directory
mkdir -p core/model/src/commonMain/kotlin

# Move all source from src/main to src/commonMain
cp -r core/model/src/main/kotlin/* core/model/src/commonMain/kotlin/

# Remove old source directory
rm -rf core/model/src/main
```

After this, verify the directory structure looks like:
```
core/model/src/
  commonMain/kotlin/net/subsloth/core/model/
    Availability.kt
    download/
    error/
    identifier/
    library/
    media/
    playback/
    progress/
  jvmTest/   (empty — no tests yet)
```

- [ ] **Step 2: Update build.gradle.kts**

Replace the entire file content with:

```kotlin
plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Compose runtime provides @Stable / @Immutable for all targets.
            // compileOnly keeps it off the runtime classpath of consumers.
            compileOnly(libs.findLibrary("androidx-compose-runtime-annotation").get())
        }
    }
}
```

**Why this works:** The `subsloth.kmp.library` convention plugin already provides `kotlinx-collections-immutable` in `commonMain`, so we don't repeat it here. We only add the `compileOnly` dependency for Compose annotations (which is specific to this module).

The `compileOnly` scope means consumers of `:core:model` don't transitively inherit the Compose runtime dependency — they add it themselves if needed. This is the same pattern as the current `subsloth.jvm.library` setup.

- [ ] **Step 3: Add multiplatform Compose runtime to version catalog**

In `gradle/libs.versions.toml`, add a new entry to `[libraries]`:

```toml
# Compose Multiplatform annotations (shared across KMP targets)
androidx-compose-runtime-annotation-mp = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeRuntimeAnnotation" }
```

And update the existing `androidx-compose-runtime-annotation` entry. Actually, since `:core:model` is the only module that needs this in a KMP context, and the existing entry `androidx-compose-runtime-annotation = { module = "androidx.compose.runtime:runtime-annotation", version.ref = "composeRuntimeAnnotation" }` is for the Android-only artifact, let's add a separate KMP alias:

```toml
# The Compose runtime (provides @Stable, @Immutable) — multiplatform artifact
# used by :core:model in its commonMain source set.
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "composeRuntimeAnnotation" }
```

And use this in `:core:model`:

```kotlin
compileOnly(libs.findLibrary("compose-runtime").get())
```

> **Note:** The Maven coordinate `org.jetbrains.compose.runtime:runtime` is the Compose Multiplatform artifact that resolves to platform-specific JARs/KLIBs. The version `1.9.3` (same as `composeRuntimeAnnotation` version ref) corresponds to Kotlin 2.3.x compatibility.

- [ ] **Step 4: Build to verify**

Run: `./gradlew :core:model:compileKotlinJvm :core:model:compileKotlinIosArm64 :core:model:compileKotlinIosSimulatorArm64 :core:model:compileKotlinIosX64`

Expected: BUILD SUCCESSFUL

If the iOS compilation fails because the Compose runtime artifact didn't resolve, verify the version. The `composeRuntimeAnnotation` version ref is `1.9.3` which is the Compose runtime version (matching the Kotlin compiler plugin). The Compose Multiplatform runtime artifact `org.jetbrains.compose.runtime:runtime` may need a slightly different version. If it fails, use `1.7.3` or whatever the latest stable Compose Multiplatform runtime version is that matches Kotlin 2.3.21.

---

## Task 4: Convert `:core:domain` to KMP

**Files:**
- Modify: `core/domain/build.gradle.kts`
- Move: `core/domain/src/main/kotlin/...` → `core/domain/src/commonMain/kotlin/...`
- Move: `core/domain/src/test/kotlin/...` → `core/domain/src/jvmTest/kotlin/...`

**Why:** The domain module is pure Kotlin with zero platform dependencies. It's the cleanest KMP conversion possible.

- [ ] **Step 1: Move source to commonMain**

```bash
mkdir -p core/domain/src/commonMain/kotlin
cp -r core/domain/src/main/kotlin/* core/domain/src/commonMain/kotlin/
rm -rf core/domain/src/main
```

- [ ] **Step 2: Move tests to jvmTest**

```bash
mkdir -p core/domain/src/jvmTest/kotlin
cp -r core/domain/src/test/kotlin/* core/domain/src/jvmTest/kotlin/
rm -rf core/domain/src/test
```

- [ ] **Step 3: Update build.gradle.kts**

Replace the entire file content with:

```kotlin
plugins {
    id("subsloth.kmp.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.findLibrary("coroutines-test").get())
        }
    }
}
```

**What this means:**
- `commonMain` depends on `:core:model` (which is now also KMP, so cross-module KMP dependency works).
- `jvmTest` adds `:testing:assertions` (a JVM-only test library) and `coroutines-test` — these are JVM-only test dependencies.
- The convention plugin already provides JUnit 5 for `jvmTest` and `kotlin.test` for `commonTest`.
- There are no iOS-specific source sets needed — the domain module has zero platform code.

- [ ] **Step 4: Fix DomainArchitectureTest path traversal**

The `DomainArchitectureTest` currently walks `src/main/kotlin` to find source files. After the move, the source is in `src/commonMain/kotlin`. Update the path in `core/domain/src/jvmTest/kotlin/net/subsloth/core/domain/architecture/DomainArchitectureTest.kt`:

```kotlin
private val allDomainImports: List<String> by lazy {
    val baseDir =
        System.getProperty("user.dir")
            ?: error("user.dir system property is required")
    val domainSourceDir =
        Paths
            .get(baseDir, "src", "commonMain", "kotlin")  // ← changed from "main" to "commonMain"

    if (!domainSourceDir.toFile().isDirectory) {
        error("Domain source directory not found at $domainSourceDir")
    }
    // ... rest unchanged
}
```

- [ ] **Step 5: Build and test to verify**

Run: `./gradlew :core:domain:compileKotlinJvm :core:domain:compileKotlinIosArm64 :core:domain:compileKotlinIosSimulatorArm64 :core:domain:compileKotlinIosX64`

Expected: BUILD SUCCESSFUL

Run: `./gradlew :core:domain:jvmTest`

Expected: All tests pass, including `DomainArchitectureTest`.

---

## Task 5: Update dependent Android modules

**Files:**
- Possibly modify: `core/network/build.gradle.kts`, `core/database/build.gradle.kts`, `core/preferences/build.gradle.kts`, `core/media/build.gradle.kts`, `core/ui/build.gradle.kts`, all feature modules, `app/build.gradle.kts`

**Why:** Android modules that depend on `:core:model` or `:core:domain` continue to do so with the same `project(":core:model")` syntax. KMP modules that target JVM produce a JVM artifact (`.jar`/`.class`), which Android's D8/R8 consumes just fine. No dependency syntax changes are needed.

However, verify that no module incorrectly references `:core:model` or `:core:domain` with an Android-specific plugin that would conflict.

- [ ] **Step 1: Verify all downstream modules compile**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL

If any module fails with a resolution error, it's likely because the module uses `subsloth.android.library` or `subsloth.jvm.library` and the KMP module's artifact isn't being resolved correctly for Android. The fix would be to add the JVM publication in the KMP convention plugin:

```kotlin
kotlin {
    // Ensure JVM target publishes a consumable JVM artifact for Android modules.
    jvm {
        withJava()
    }
}
```

Add `withJava()` to the `jvm()` target in `subsloth.kmp.library` if needed:

In `build-logic/convention/src/main/kotlin/subsloth.kmp.library.gradle.kts`, update:

```kotlin
kotlin {
    jvmToolchain(17)

    jvm {
        withJava()
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    // ...
}
```

- [ ] **Step 2: Run full test suite**

Run: `./gradlew test`

Expected: All tests pass (the domain tests now run under `jvmTest`, everything else unchanged).

---

## Task 6: CI and housekeeping

**Files:**
- Possibly modify: `.github/workflows/*.yml` (if CI builds reference specific tasks)

**Why:** CI should exercise the new KMP targets. The existing tasks (`:core:model:compileKotlin`, `:core:domain:compileKotlin`) will need updating if they reference the JVM-only task names, because KMP modules don't have a single `compileKotlin` task — they have per-target tasks.

- [ ] **Step 1: Check CI workflow files**

Look for any task names like `:core:model:compileKotlin` or `:core:domain:compileKotlin` in CI configs. If found, replace them with `:core:model:compileKotlinJvm` and `:core:domain:compileKotlinJvm`, or use the broader `allTests` pattern.

- [ ] **Step 2: Add KMP build verification to CI**

Add a step to compile iOS targets (requires macOS CI runner):

```yaml
- name: Compile iOS targets
  run: ./gradlew :core:model:compileKotlinIosArm64 :core:domain:compileKotlinIosArm64
  if: runner.os == 'macOS'
```

On Linux CI, only the JVM target will compile (iOS native compilation requires the Apple SDK). This is expected and fine — the iOS target config is validated at development time.

- [ ] **Step 3: Full pre-commit check**

Run: `./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :app:assembleDebug test`

Expected: All green.

---

## Self-Review

### Spec coverage
| Requirement | Task |
|---|---|
| KMP convention plugin | Task 2 |
| Convert `:core:model` to KMP | Task 3 |
| Convert `:core:domain` to KMP | Task 4 |
| Keep downstream Android modules working | Task 5 |
| CI/housekeeping | Task 6 |

### Placeholder scan
No placeholders. Every task contains concrete file paths, code blocks, and commands.

### Type consistency
- The convention plugin name `subsloth.kmp.library` is referenced consistently in Tasks 3 and 4.
- The version catalog alias `compose-runtime` is added in Task 3 and referenced in the same task.
- The `kotlin-multiplatform` plugin alias is added in Task 1 and used in Task 2.
- The `kotlin-multiplatform-gradlePlugin` library alias is added in Task 1 and used in Task 2's convention plugin build config.
