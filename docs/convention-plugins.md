# Convention Plugins

Precompiled script plugins in `build-logic/convention/src/main/kotlin/` that encapsulate shared Gradle configuration. All plugins are applied by `id("subsloth.*")` — never by `id("...")` with a full plugin ID.

---

## Plugin Inventory

| Plugin | Applies To | Key Configuration |
|---|---|---|
| `subsloth.kmp.library` | KMP libraries (`:core:*`, `:feature:*`) | Kotlin Multiplatform (JVM + WasmJS), jvmToolchain(17), allWarningsAsErrors, spotless, detekt, power-assert, JUnit Platform, `kotlinx-collections-immutable`, `kermit` |
| `subsloth.android.application` | Android apps (`:androidApp`) | Android application plugin, compileSdk 37, minSdk 26, targetSdk 37, lint strict, spotless, detekt, power-assert, JUnit Platform |
| `subsloth.android.application.compose` | Android apps with Compose | Extends `subsloth.android.application` + enables Compose build features + Compose compiler with stability config |
| `subsloth.android.library` | Android libraries | Android library plugin, compileSdk 37, minSdk 26, lint strict, spotless, detekt, power-assert, JUnit Platform, `kermit` |
| `subsloth.android.library.compose` | Android libraries with Compose | Extends `subsloth.android.library` + enables Compose build features + Compose compiler with stability config |
| `subsloth.jvm.library` | JVM-only libraries (`:testing:*`, `:desktopApp`) | Kotlin JVM plugin, jvmToolchain(17), allWarningsAsErrors, spotless, detekt, power-assert, JUnit Platform |

---

## `subsloth.kmp.library`

The primary convention for **cross-platform modules** shared across Android, Desktop, and Web.

**Applied plugins:**
- `org.jetbrains.kotlin.multiplatform`
- `com.diffplug.spotless`
- `dev.detekt`
- `org.jetbrains.kotlin.plugin.power-assert`

**KMP targets:**
| Target | Enabled? | Notes |
|---|---|---|
| `jvm()` | ✅ Enabled | Primary test target |
| `wasmJs()` | ✅ Enabled | Browser output, `binaries.executable()` |
| `iosArm64()` | ❌ Disabled | No iOS testing infra available |
| `iosSimulatorArm64()` | ❌ Disabled | No iOS testing infra available |
| `macosArm64()` | ❌ Disabled | No macOS testing infra available |

**Common dependencies (applied to all modules automatically):**
- `kotlinx-collections-immutable` (commonMain)
- `kermit` logging (commonMain)
- `kotlin("test")` (commonTest)
- JUnit 5 + JUnit Platform (jvmTest)

**Compiler settings:**
- `jvmToolchain(17)` — compiles to Java 17 bytecode
- `allWarningsAsErrors = true` — no warnings tolerated

**Linting:**
- Spotless/ktlint — applied to `src/*/kotlin/**/*.kt` and `*.gradle.kts`
- detekt — configured from `config/detekt.yml`, baseline at `config/detekt-baseline.xml`, custom rules from `:testing:detekt-rules` + Compose rules

**Testing:**
- JUnit Platform (5.x) with JUnit Jupiter API + Engine
- `kotlin.test` assertions enhanced by power-assert plugin

---

## `subsloth.android.application`

**Applied plugins:**
- `com.android.application`
- `com.diffplug.spotless`
- `dev.detekt`
- `org.jetbrains.kotlin.plugin.power-assert`

**Defaults:**
- `compileSdk = 37`, `buildToolsVersion = "37.0.0"`
- `applicationId = "net.subsloth"` (can be overridden per-module)
- `minSdk = 26`, `targetSdk = 37`

**Lint:**
- `abortOnError = true`, `warningsAsErrors = true`
- Suppressed: `DataExtractionRules`, `MissingApplicationIcon`, `NotShrinkingResources`, `GradleDependency`, `InvalidPackage`

---

## `subsloth.android.application.compose`

**Extends:** `subsloth.android.application`

**Adds:**
- `org.jetbrains.kotlin.plugin.compose`
- `buildFeatures { compose = true }`
- Compose compiler stability config from `config/compose_stability.conf`

---

## `subsloth.android.library`

**Applied plugins:**
- `com.android.library`
- `com.diffplug.spotless`
- `dev.detekt`
- `org.jetbrains.kotlin.plugin.power-assert`

**Defaults:**
- `compileSdk = 37`, `buildToolsVersion = "37.0.0"`
- `minSdk = 26`

**Lint:**
- `abortOnError = true`, `warningsAsErrors = true`
- Suppressed: `GradleDependency`, `InvalidPackage` (Ktor's `ktor-utils` references `java.lang.management`)

**Common dependencies:**
- `kermit` logging (implementation)

---

## `subsloth.android.library.compose`

**Extends:** `subsloth.android.library`

**Adds:**
- `org.jetbrains.kotlin.plugin.compose`
- `buildFeatures { compose = true }`
- Compose compiler stability config from `config/compose_stability.conf`

---

## `subsloth.jvm.library`

Used for **JVM-only** modules where Kotlin Multiplatform would be overkill (testing utilities, desktop app shell).

**Applied plugins:**
- `org.jetbrains.kotlin.jvm`
- `com.diffplug.spotless`
- `dev.detekt`
- `org.jetbrains.kotlin.plugin.power-assert`

**Compiler settings:**
- `jvmToolchain(17)`
- `allWarningsAsErrors = true`

---

## Power-Assert Configuration

All convention plugins configure the **Kotlin power-assert** plugin identically:

```kotlin
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
```

This enables diff-style assertion output for all listed functions — failed assertions print the intermediate values of each sub-expression in the assertion condition. No test assertion library (Truth, Kotest) is needed.

### Usage

Because power-assert is a compiler plugin (not a runtime library), no special imports are needed. Simply write standard `kotlin.test` assertions:

```kotlin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MyTest {
    @Test
    fun `example power-assert output`() {
        val items = listOf("a", "b", "c")
        val filtered = items.filter { it.length > 1 }

        // Power-assert rewrites assertEquals at compile time.
        // On failure you see the intermediate values:
        //   Expected: 3
        //   Actual:   0
        //   items: ["a", "b", "c"]
        //   filtered: []
        assertEquals(3, filtered.size)

        // Works the same for assertTrue with a complex condition:
        assertTrue(filtered.contains("a"))
    }
}
```

### How It Works

The plugin rewrites supported assertion function calls at compile time to capture each sub-expression's value. When an assertion fails, the exception message includes a tree of intermediate values rather than just "expected X but got Y". This eliminates the need for a separate assertion library.

### Supported Functions (configured in convention plugins)

- `kotlin.assert` / `kotlin.require` / `kotlin.check`
- `kotlin.test.assertTrue` / `kotlin.test.assertFalse`
- `kotlin.test.assertEquals` / `kotlin.test.assertNotEquals`
- `kotlin.test.assertNull` / `kotlin.test.assertNotNull`

### Limitations

- Power-assert only rewrites calls inside functions **annotated with `@Test`** or in files compiled with the plugin active. Top-level assertions outside test functions are not rewritten.
- Nested lambdas inside the assertion condition may show truncated intermediate values.
- The plugin is active for **all sources** in the module (not just test source sets), though its main value is in test assertions.

---

## Compose Stability Configuration

The Compose compiler uses `config/compose_stability.conf` to recognise additional types as stable (enabling strong-skipping-mode optimisations in CMP). All Compose-applying convention plugins (`*.compose`) wire this file in:

```kotlin
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose_stability.conf"),
    )
}
```

The file lists one fully-qualified class per line. Comments are not supported (they cause parser errors).

---

## Adding a New Convention Plugin

1. Create `build-logic/convention/src/main/kotlin/subsloth.{name}.gradle.kts`
2. Add any required `compileOnly` dependencies to `build-logic/convention/build.gradle.kts`
3. The plugin is automatically available as `id("subsloth.{name}")` in all project modules — no registration step needed (the `kotlin-dsl` plugin auto-registers precompiled scripts in `src/main/kotlin/`).
