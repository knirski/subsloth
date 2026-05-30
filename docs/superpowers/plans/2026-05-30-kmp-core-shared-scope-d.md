# KMP Scope D (Compose Multiplatform UI) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the UI layer from Android-only Compose to Compose Multiplatform, sharing UI code across Android, iOS, and Desktop.

**Architecture:** Compose Multiplatform allows sharing Compose UI code across platforms. The ViewModels, screens, and UI components move to `commonMain` with platform-specific adapters in platform source sets. Android TV APIs (`tv-foundation`, `tv-material`) have no KMP equivalent — they stay Android-only behind `expect`/`actual` abstractions.

**Tech Stack:** Compose Multiplatform 1.7.x (matching Kotlin 2.3.21), Material3, Navigation3 (already multiplatform), Coil 3 (already KMP), kotlinx-collections-immutable.

**Scope boundary:** This is the most ambitious scope. It covers `:core:ui`, all `:feature:*` modules, and `:app`. Android TV APIs are extracted behind `expect`/`actual` boundaries. Only UI code moves — the imperative shell (network, database, media) stays unchanged.

---

## Migration Strategy

### What's already multiplatform-ready
- **Material3** — available in Compose Multiplatform
- **Coil 3** (`io.coil-kt.coil3:coil-compose`) — KMP-native
- **Navigation3** — multiplatform
- **Lifecycle ViewModel** — `lifecycle-viewmodel-compose` available for KMP
- **kotlinx-collections-immutable** — already multiplatform
- **kotlinx-serialization** — already multiplatform

### What needs platform-specific handling
- **Android TV** (`tv-foundation`, `tv-material`) — no KMP equivalent, use `expect`/`actual`
- **Activity/Context-dependent APIs** — image loading, file access, system services
- **Robolectric/Roborazzi tests** — JVM-only, stay in `jvmTest`

---

## File Map

### Convention plugins

| File | Change |
|---|---|
| `build-logic/convention/src/main/kotlin/subsloth.kmp.library.compose.gradle.kts` | **New**: KMP library convention with Compose Multiplatform plugin |
| `build-logic/convention/src/main/kotlin/subsloth.android.feature.gradle.kts` | Update to also support KMP Compose |

### Module conversions

| Module | Current plugin | New plugin |
|---|---|---|
| `:core:ui` | `subsloth.android.library` | `subsloth.kmp.library.compose` |
| `:feature:auth` | `subsloth.android.feature` | `subsloth.kmp.library.compose` |
| `:feature:catalog` | `subsloth.android.feature` | `subsloth.kmp.library.compose` |
| `:feature:details` | `subsloth.android.feature` | `subsloth.kmp.library.compose` |
| `:feature:player` | `subsloth.android.feature` | `subsloth.kmp.library.compose` |
| `:feature:library` | `subsloth.android.feature` | `subsloth.kmp.library.compose` |
| `:feature:settings` | `subsloth.android.feature` | `subsloth.kmp.library.compose` |

### Platform-specific concerns

| Android-only API | Replacement |
|---|---|
| `androidx.tv:tv-foundation` (TV focus) | `expect`/`actual` — wraps TV focus handling |
| `androidx.tv:tv-material` | `expect`/`actual` — wraps TV-specific composables |
| `androidx.activity.compose` (setContent) | App entry point — platform-specific in `app` module |
| `androidx.core.net.toUri` | Ktor `Url` or kotlinx Uri |
| `androidx.annotation` | Remove or replace with Kotlin stdlib contracts |

---

## Task 1: Add Compose Multiplatform convention plugin

**Files:**
- Create: `build-logic/convention/src/main/kotlin/subsloth.kmp.library.compose.gradle.kts`

- [ ] **Step 1: Create the convention plugin**

```kotlin
plugins {
    id("subsloth.kmp.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(platform(libs.findLibrary("androidx-compose-bom").get()))
            api(libs.findLibrary("androidx-compose-ui").get())
            api(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            api(libs.findLibrary("androidx-compose-material3").get())
            api(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            api(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            api(libs.findLibrary("androidx-navigation3-runtime").get())
            api(libs.findLibrary("androidx-navigation3-ui").get())
            api(libs.findLibrary("androidx-lifecycle-viewmodel-navigation3").get())
            api(libs.findLibrary("kotlinx-collections-immutable").get())
        }

        androidMain.dependencies {
            implementation(libs.findLibrary("androidx-activity-compose").get())
        }
    }
}
```

---

## Task 2: Convert `:core:ui` to Compose Multiplatform

**Files:**
- Modify: `core/ui/build.gradle.kts`
- Move: `core/ui/src/main/kotlin/...` → `core/ui/src/commonMain/kotlin/...`

**Why:** `:core:ui` is a thin module with shared UI types and annotations. It currently uses `subsloth.android.library` and depends on `androidx.annotation`.

- [ ] **Step 1: Rewrite build.gradle.kts**

```kotlin
plugins {
    id("subsloth.kmp.library.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":core:model"))
        }
    }
}
```

- [ ] **Step 2: Replace Android annotation references**

Replace `import androidx.annotation` with Compose Multiplatform alternatives or remove.

- [ ] **Step 3: Move source to commonMain**

```bash
mkdir -p core/ui/src/commonMain/kotlin
cp -r core/ui/src/main/kotlin/* core/ui/src/commonMain/kotlin/
rm -rf core/ui/src/main
```

---

## Task 3: Convert feature modules to Compose Multiplatform

**Files:**
- Modify: Each `feature/*/build.gradle.kts`
- Move: Each `feature/*/src/main/kotlin/...` → `feature/*/src/commonMain/kotlin/...`

Each feature module follows the same pattern:

- [ ] **Step 1: Rewrite build.gradle.kts**

Example for `:feature:auth`:

```kotlin
plugins {
    id("subsloth.kmp.library.compose")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core:model"))
            implementation(project(":core:domain"))
            implementation(project(":core:network"))
            implementation(project(":core:ui"))
            implementation(project(":core:database"))
            implementation(project(":core:preferences"))
            implementation(libs.findLibrary("androidx-activity-compose").get())
        }

        jvmTest.dependencies {
            implementation(project(":testing:assertions"))
            implementation(libs.findLibrary("coroutines-test").get())
        }
    }
}
```

- [ ] **Step 2: Handle Android TV composables**

TV-specific composables (buttons with focus indicators, D-pad navigation) need `expect`/`actual`:

```kotlin
// commonMain — abstract TV surface
@Composable
expect fun TvSurface(content: @Composable () -> Unit)

// androidMain — actual Android TV surface
@Composable
actual fun TvSurface(content: @Composable () -> Unit) {
    androidx.tv.material.TvSurface(content = content)
}
```

- [ ] **Step 3: Handle Image loading**

Coil 3 is KMP-native, but some APIs differ. Replace `AsyncImage` with Coil 3's multiplatform variant:

```kotlin
// Common — works on all platforms with Coil 3
AsyncImage(
    model = url,
    contentDescription = null,
    modifier = Modifier.fillMaxWidth(),
)
```

- [ ] **Step 4: Move source to commonMain for each feature**

```bash
# For each feature module
mkdir -p feature/<name>/src/commonMain/kotlin
cp -r feature/<name>/src/main/kotlin/* feature/<name>/src/commonMain/kotlin/
rm -rf feature/<name>/src/main
```

---

## Task 4: Handle platform-specific composables with expect/actual

**Files:**
- Create: Platform-specific directories in each module

Common patterns to extract:

| Pattern | expect (commonMain) | actual (androidMain) |
|---|---|---|
| Window/Activity setup | `expect fun MainView()` | `actual fun MainView() { setContent { App() } }` |
| Image loading | Use Coil 3 (multiplatform) | Same |
| TV focus | `expect fun TvFocusIndicator()` | `androidx.tv.material` |
| Haptic feedback | `expect fun hapticFeedback()` | Android `HapticFeedbackConstants` |
| Navigation bar | Material3 (multiplatform) | Same |
| System bars | `expect fun systemBarsPadding()` | `WindowInsetsControllerCompat` |

---

## Task 5: Convert `:app` module to Compose Multiplatform entry point

**Files:**
- Modify: `app/build.gradle.kts`
- Create: `app/src/commonMain/kotlin/.../App.kt` (shared app composable)
- Keep: `app/src/main/kotlin/.../MainActivity.kt` (Android entry point)
- Create: `app/src/androidMain/kotlin/.../MainView.android.kt`
- Create: `app/src/iosMain/kotlin/.../MainView.ios.kt`
- Create: `app/src/desktopMain/kotlin/.../MainView.desktop.kt`

- [ ] **Step 1: Extract shared App composable**

```kotlin
// commonMain — shared app root
@Composable
fun App() {
    MaterialTheme {
        // Navigation3 host
        AppNavigation()
    }
}
```

- [ ] **Step 2: Create platform entry points**

```kotlin
// androidMain
fun MainView() {
    // Called from MainActivity.setContent
}

// iosMain
fun MainViewController(): UIViewController {
    // Called from SwiftUI/UIKit app delegate
}

// desktopMain
fun main() = application {
    Window(onCloseRequest = ::exitApplication) {
        App()
    }
}
```

---

## Task 6: Handle tests

**Files:**
- Move: `feature/*/src/test/kotlin/...` → `feature/*/src/jvmTest/kotlin/...`

**Why:** ViewModel tests are pure Kotlin + coroutines and work on JVM without Android. UI tests with Roborazzi/Robolectric stay in `jvmTest`.

- [ ] **Step 1: Move ViewModel tests to jvmTest**

```bash
# For each feature module
mkdir -p feature/<name>/src/jvmTest/kotlin
cp -r feature/<name>/src/test/kotlin/* feature/<name>/src/jvmTest/kotlin/
rm -rf feature/<name>/src/test
```

- [ ] **Step 2: Fix test dependencies**

Ensure `jvmTest` dependencies include `coroutines-test`, `turbine`, and `:testing:assertions`.

---

## Task 7: Verify and CI

- [ ] **Step 1: Full pre-commit check**

```bash
./gradlew spotlessApply spotlessCheck detekt :app:assembleDebug test
```

- [ ] **Step 2: Commit and push**

---

## Self-Review

### Spec coverage

| Requirement | Task |
|---|---|
| Compose Multiplatform convention plugin | Task 1 |
| Convert :core:ui | Task 2 |
| Convert 6 feature modules | Task 3 |
| expect/actual for TV/Platform APIs | Task 4 |
| Convert :app module | Task 5 |
| Test migration | Task 6 |
| Verification | Task 7 |

### Risk assessment

- **Very high effort** — 8 modules to convert, each with Android-specific dependencies
- Android TV APIs (`tv-foundation`, `tv-material`) have no KMP equivalent — TV support will be Android-only behind `expect`/`actual`
- Compose Multiplatform 1.7.x has good Material3 support but some APIs may differ from Android Compose
- Coil 3 is KMP-native, so image loading should work without changes
- Navigation3 is multiplatform — no migration needed
- The `app` module with its Android entry point (`MainActivity`, `AndroidManifest.xml`) stays Android-only; shared UI lives in `commonMain`
- Screenshot tests with Roborazzi/Robolectric are JVM-only and stay in `jvmTest`
- `metro` DI library may need configuration for multiplatform — check if it supports KMP
