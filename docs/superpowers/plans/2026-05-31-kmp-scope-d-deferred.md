# KMP Scope D — Deferred Items Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Resolve 6 deferred items from the KMP Compose Multiplatform migration: TV `expect`/`actual`, native JVM-only API fixes, PlayerViewModel cleanup, empty module decisions, and `:app` navigation extraction.

**Architecture:** TV composables move from Android-only `:app` to `:core:ui` behind `expect`/`actual` boundaries. JVM-only APIs (`Charsets`, `String.format`) in `:core:preferences` are replaced with Okio equivalents or extracted to platform source sets. The PlayerViewModel is refactored into smaller domain-use-case classes. Empty modules receive a decision (implement, keep, or remove). The navigation host gets a `navigationevent-compose` replacement for `BackHandler`.

**Tech Stack:** Compose Multiplatform 1.9.0, Okio 3.17.0, `org.jetbrains.androidx.navigationevent:navigationevent-compose:1.0.1` (already transitive), `expect`/`actual` pattern.

---

## File Map

| Task | File | Change |
|---|---|---|
| 1 | `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/TvFocus.kt` | **New:** `expect` focus composables |
| 1 | `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.kt` | **New:** `expect` layout composables |
| 1 | `core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/TvFocus.android.kt` | **New:** `actual` wrapping androidx.tv.material3 |
| 1 | `core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.android.kt` | **New:** `actual` wrapping androidx.tv.material3 |
| 1 | `core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/TvFocus.ios.kt` | **New:** `actual` stubs for iOS |
| 1 | `core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.ios.kt` | **New:** `actual` stubs for iOS |
| 1 | `core/ui/build.gradle.kts` | Add `androidMain.dependencies` for TV libs |
| 1 | `app/src/main/java/net/subsloth/ui/tv/TvFocus.kt` | **Delete:** moved to `:core:ui` |
| 1 | `app/src/main/java/net/subsloth/ui/tv/TvLayouts.kt` | **Delete:** moved to `:core:ui` |
| 2 | `core/preferences/src/commonMain/.../AccountProfileStore.kt` | Replace `Charsets.UTF_8`, `String.format` with Okio |
| 2 | `core/preferences/build.gradle.kts` | Add Okio dependency to commonMain if not present |
| 3 | `feature/player/src/commonMain/.../PlayerViewModel.kt` | Extract methods into use-case classes |
| 3 | `feature/player/src/commonMain/.../PlayerErrorHandler.kt` | **New:** extracted error categorization |
| 3 | `feature/player/src/commonMain/.../PlaybackSessionManager.kt` | **New:** extracted session + progress logic |
| 4 | `app/src/main/java/net/subsloth/SubSlothNavHost.kt` | Replace `BackHandler` import |
| 5 | `feature/library/build.gradle.kts` | Decision: remove or keep |
| 5 | `settings.gradle.kts` | Remove `:feature:library` include if decided |
| 5 | `app/build.gradle.kts` | Remove `:feature:library` dep if decided |
| 6 | `feature/settings/build.gradle.kts` | Decision: remove or keep |
| 6 | `settings.gradle.kts` | Remove `:feature:settings` include if decided |
| 6 | `app/build.gradle.kts` | Remove `:feature:settings` dep if decided |

---

## Task 1: TV `expect`/`actual` extraction

**Files:**
- Create: `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/TvFocus.kt`
- Create: `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.kt`
- Create: `core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/TvFocus.android.kt`
- Create: `core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.android.kt`
- Create: `core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/TvFocus.ios.kt`
- Create: `core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.ios.kt`
- Modify: `core/ui/build.gradle.kts`
- Delete: `app/src/main/java/net/subsloth/ui/tv/TvFocus.kt`
- Delete: `app/src/main/java/net/subsloth/ui/tv/TvLayouts.kt`

**Why:** TV composables (`TvFocus.kt`, `TvLayouts.kt`) use `androidx.tv.material3.*` which is Android-only. Moving them behind `expect`/`actual` in `:core:ui` allows the common Compose code (`TvRow`, `TvLargeCard`, `TvActionRail`) to compile for all KMP targets while Android gets the actual TV Material3 implementation.

- [ ] **Step 1: Define `expect` focus composables in commonMain**

Create `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/TvFocus.kt`:

```kotlin
package net.subsloth.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Requests initial focus on this composable for TV D-pad navigation.
 * On Android this delegates to FocusRequester; on other platforms it is a no-op.
 */
@Composable
expect fun Modifier.tvFocusRequester(): Modifier

/**
 * Auto-requests initial focus for the first focusable element in content.
 * Android: uses FocusRequester. Other platforms: no-op.
 */
@Composable
expect fun AutoFocusInitial(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)
```

- [ ] **Step 2: Define `expect` layout composables in commonMain**

Create `core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.kt`:

```kotlin
package net.subsloth.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

val tvOverscanHorizontal = 48.dp
val tvOverscanVertical = 32.dp
val tvItemSpacing = 16.dp
val tvRowSpacing = 32.dp

@Composable
expect fun TvRow(
    title: String,
    items: List<T>,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
)

@Composable
expect fun TvLargeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
)

@Composable
expect fun TvActionRail(
    actions: List<String>,
    selectedIndex: Int,
    onActionSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
)
```

- [ ] **Step 3: Add Android source set + TV deps to `:core:ui` build.gradle.kts**

```kotlin
// In core/ui/build.gradle.kts, add after commonMain.dependencies block:
androidMain.dependencies {
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)
}
```

- [ ] **Step 4: Implement `actual` focus composables for Android**

Create `core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/TvFocus.android.kt`:

```kotlin
package net.subsloth.core.ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag

@Composable
actual fun Modifier.tvFocusRequester(): Modifier {
    val focusRequester = remember { FocusRequester() }
    return LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }.let { this.then(Modifier.focusRequester(focusRequester)) }
}

@Composable
actual fun AutoFocusInitial(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    Box(modifier = modifier.focusRequester(focusRequester)) {
        content()
    }
}
```

Note: `LaunchedEffect.let` doesn't work that way. The correct pattern:

```kotlin
@Composable
actual fun Modifier.tvFocusRequester(): Modifier {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }
    return this.then(Modifier.focusRequester(focusRequester))
}
```

- [ ] **Step 5: Implement `actual` layout composables for Android**

Create `core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.android.kt`.
Copy the content from the current `app/src/main/java/net/subsloth/ui/tv/TvLayouts.kt` and replace the package with `net.subsloth.core.ui.tv`. The `actual` keyword goes on each composable function.

- [ ] **Step 6: Implement `actual` stubs for iOS**

Create `core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/TvFocus.ios.kt`:

```kotlin
package net.subsloth.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box

@Composable
actual fun Modifier.tvFocusRequester(): Modifier = this

@Composable
actual fun AutoFocusInitial(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) { content() }
}
```

Create `core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/TvLayouts.ios.kt` with simplified stubs that render the content without `androidx.tv.material3` theming (plain Material3 composables instead).

- [ ] **Step 7: Delete old TV files from `:app`**

```bash
rm -rf app/src/main/java/net/subsloth/ui/tv
```

Also remove the TV deps from `app/build.gradle.kts`:
```kotlin
// Remove these lines:
implementation(libs.androidx.tv.foundation)
implementation(libs.androidx.tv.material)
```

- [ ] **Step 8: Compile and verify**

```bash
./gradlew :core:ui:compileKotlinJvm :core:ui:compileKotlinIosArm64 :app:assembleDebug test
```

- [ ] **Step 9: Commit**

```bash
git add core/ui/src/commonMain/kotlin/net/subsloth/core/ui/tv/ \
       core/ui/src/androidMain/kotlin/net/subsloth/core/ui/tv/ \
       core/ui/src/iosMain/kotlin/net/subsloth/core/ui/tv/ \
       core/ui/build.gradle.kts app/build.gradle.kts
git rm -r app/src/main/java/net/subsloth/ui/tv
git commit -m "feat(core): extract TV composables behind expect/actual in :core:ui"
```

---

## Task 2: Fix JVM-only APIs in `:core:preferences`

**Files:**
- Modify: `core/preferences/src/commonMain/kotlin/net/subsloth/preferences/AccountProfileStore.kt`

**Why:** `AccountProfileStore.kt:59` uses `Charsets.UTF_8` and `String.format()` which are JVM-only. These compile on JVM/Android but fail on iOS/macOS native targets. The class already uses `expect`/`actual` for `generateSalt()`, `hmacSha256()`, and `normalizeLogin()` — so adding `expect`/`actual` for the hex encoding follows the existing pattern.

- [ ] **Step 1: Add `expect`/`actual` for hex encoding**

Replace the inline `hash.joinToString("") { "%02x".format(it) }` with an `expect` function.

In `AccountProfileStore.kt`, change the `deriveProfileKey` function:

```kotlin
suspend fun deriveProfileKey(login: String): AccountProfileKey {
    val salt = getOrCreateSalt()
    val normalized = normalizeLogin(login)
    val hash = hmacSha256(salt.encodeToByteArray(), normalized.encodeToByteArray())
    val hex = bytesToHex(hash)
    return AccountProfileKey(hex)
}
```

Add `expect fun bytesToHex(bytes: ByteArray): String` alongside the existing `expect` functions at the bottom of the file.

- [ ] **Step 2: Add `actual` implementations**

In `core/preferences/src/jvmMain/kotlin/net/subsloth/preferences/AccountProfileStore.jvm.kt`:

```kotlin
actual fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { "%02x".format(it) }
```

In `core/preferences/src/iosMain/kotlin/net/subsloth/preferences/AccountProfileStore.ios.kt`:

```kotlin
actual fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
```

- [ ] **Step 3: Compile and verify**

```bash
./gradlew :core:preferences:compileKotlinJvm :core:preferences:compileKotlinIosArm64 :core:preferences:jvmTest
```

- [ ] **Step 4: Commit**

```bash
git add core/preferences/src/
git commit -m "fix(core): replace JVM-only bytesToHex with expect/actual for native targets"
```

---

## Task 3: Refactor PlayerViewModel (extract TooManyFunctions)

**Files:**
- Modify: `feature/player/src/commonMain/kotlin/net/subsloth/player/PlayerViewModel.kt`
- Create: `feature/player/src/commonMain/kotlin/net/subsloth/player/PlayerErrorHandler.kt`

**Why:** The ViewModel has 400+ lines and `@Suppress("TooManyFunctions")`. Extracting error categorization into its own class reduces the ViewModel surface and makes error handling testable independently.

- [ ] **Step 1: Extract `PlayerErrorHandler`**

Create `feature/player/src/commonMain/kotlin/net/subsloth/player/PlayerErrorHandler.kt`:

```kotlin
package net.subsloth.player

import net.subsloth.core.model.playback.PlaybackError

/**
 * Categorizes playback errors from a [Throwable] into domain-specific [PlaybackError] types.
 *
 * Extracted from PlayerViewModel to reduce its surface and make error
 * categorization independently testable.
 */
class PlayerErrorHandler {
    fun categorize(error: Throwable): PlaybackError {
        val message = error.message ?: ""
        return when {
            message.contains("401") || message.contains("Unauthorized") ||
                message.contains("403") || message.contains("Forbidden") ->
                PlaybackError.AuthFailure
            message.contains("expired") || message.contains("410") ||
                message.contains("Gone") || message.contains("not found") ->
                PlaybackError.StreamUrlExpired
            else -> PlaybackError.Recoverable
        }
    }

    fun isLikelyAuthError(error: PlaybackError): Boolean =
        error is PlaybackError.AuthFailure

    fun isLikelyStreamExpired(error: PlaybackError): Boolean =
        error is PlaybackError.StreamUrlExpired
}
```

- [ ] **Step 2: Update PlayerViewModel**

In `PlayerViewModel.kt`:
- Remove `categorizePlaybackError()`, `isLikelyAuthError()`, `isLikelyStreamExpired()` methods
- Add `private val errorHandler: PlayerErrorHandler = PlayerErrorHandler()` as a constructor parameter with default
- Replace calls:
  - `categorizePlaybackError(error)` → `errorHandler.categorize(error)`
  - `isLikelyAuthError(playbackError)` → `errorHandler.isLikelyAuthError(playbackError)`
  - `isLikelyStreamExpired(playbackError)` → `errorHandler.isLikelyStreamExpired(playbackError)`
- Remove `@Suppress("TooManyFunctions")` — verify detekt still passes

- [ ] **Step 3: Compile, run tests, verify detekt**

```bash
./gradlew :feature:player:compileKotlinJvm :feature:player:jvmTest :feature:player:detekt
```

Check that `TooManyFunctions` no longer fires.

- [ ] **Step 4: Commit**

```bash
git add feature/player/src/commonMain/kotlin/net/subsloth/player/
git commit -m "refactor(player): extract PlayerErrorHandler, drop TooManyFunctions suppression"
```

---

## Task 4: Replace `BackHandler` with navigationevent-compose in `:app`

**Files:**
- Modify: `app/src/main/java/net/subsloth/SubSlothNavHost.kt`

**Why:** `BackHandler` from `androidx.activity.compose` is Android-only. The `org.jetbrains.androidx.navigationevent:navigationevent-compose` library (already a transitive dependency of KMP navigation3-ui) provides a multiplatform alternative.

- [ ] **Step 1: Replace BackHandler import and usage**

In `SubSlothNavHost.kt`, change:

```kotlin
// Remove:
import androidx.activity.compose.BackHandler

// Replace with:
import org.jetbrains.androidx.navigationevent.compose.BackHandler
```

The API is identical — `@Composable fun BackHandler(enabled: Boolean, onBack: () -> Unit)`.

- [ ] **Step 2: Add explicit navigationevent dependency**

In `app/build.gradle.kts`, add:

```kotlin
implementation(libs.androidx.navigation3.runtime)
implementation(libs.androidx.navigation3.ui)
// No need for explicit navigationevent dep — it's transitive via navigation3-ui-kmp
```

Actually check if the KMP navigation3-ui already pulls in navigationevent-compose (the POM showed it does). So no explicit dep needed.

- [ ] **Step 3: Compile and verify**

```bash
./gradlew :app:compileDebugKotlin :app:assembleDebug
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/net/subsloth/SubSlothNavHost.kt
git commit -m "refactor: replace Android BackHandler with multiplatform navigationevent-compose"
```

---

## Task 5: Decide fate of `:feature:library`

**Files:**
- Modify: `feature/library/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

**Why:** `:feature:library` has `build.gradle.kts` with WorkManager dependency but zero Kotlin source files. It's a placeholder. Three options:

**Option A — Remove the empty module:**
- Remove `include(":feature:library")` from `settings.gradle.kts`
- Remove `implementation(project(":feature:library"))` from `app/build.gradle.kts`
- Delete `feature/library/`

**Option B — Keep as Android-only (no KMP):**
- Leave as-is — it's a placeholder for future WorkManager-based offline features
- No changes needed

**Option C — Implement a minimal KMP Compose feature:**
- Create screen scaffold with placeholder UI
- Requires adding Compose deps

- [ ] **Step 1: Choose the path**

The current state has no source code and a WorkManager dependency. Since there's no specification for what the library feature does, removing the empty module is the safest choice. If needed later, it can be recreated.

```bash
rm -rf feature/library
```

- [ ] **Step 2: Update settings.gradle.kts**

Remove `include(":feature:library")`.

- [ ] **Step 3: Update app/build.gradle.kts**

Remove `implementation(project(":feature:library"))`.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts
git rm -r feature/library
git commit -m "chore: remove empty :feature:library module"
```

---

## Task 6: Decide fate of `:feature:settings`

**Files:**
- Modify: `feature/settings/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `app/build.gradle.kts`

**Why:** Same situation as `:feature:library` — empty module, no source code.

**Option A — Remove:**
- Same pattern as Task 5

**Option B — Keep as placeholder for future settings UI:**

- [ ] **Step 1: Choose the path**

Same reasoning — remove the empty placeholder.

```bash
rm -rf feature/settings
```

- [ ] **Step 2: Update settings.gradle.kts**

Remove `include(":feature:settings")`.

- [ ] **Step 3: Update app/build.gradle.kts**

Remove `implementation(project(":feature:settings"))`.

- [ ] **Step 4: Commit**

```bash
git add settings.gradle.kts app/build.gradle.kts
git rm -r feature/settings
git commit -m "chore: remove empty :feature:settings module"
```

---

## Task 7: Final verification

- [ ] **Step 1: Run full pre-commit check**

```bash
./gradlew spotlessApply
./gradlew spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :app:assembleDebug test
```

- [ ] **Step 2: Push and create PR**

```bash
git push origin feat/kmp-compose-multiplatform
```

---

## Self-Review

### Spec coverage

| Handoff Item | Task |
|---|---|
| TV expect/actual extraction | Task 1 |
| core/preferences JVM-only APIs | Task 2 |
| PlayerViewModel TooManyFunctions | Task 3 |
| :app BackHandler → navigationevent-compose | Task 4 |
| :feature:library decision | Task 5 |
| :feature:settings decision | Task 6 |
| Final verification | Task 7 |

### Risk assessment

- **Task 1 (TV expect/actual):** Medium risk. The `androidx.tv.material3.Card` API has no KMP equivalent. iOS stubs will be visually degraded (plain Material3 cards instead of TV-themed cards). The D-pad focus behavior is Android-only — iOS uses touch.
- **Task 2 (preferences native):** Low risk. The `expect`/`actual` pattern is already established in this file. `bytesToHex` is pure computation.
- **Task 3 (PlayerViewModel refactor):** Low risk. Pure extraction — no behavior change.
- **Task 4 (BackHandler replacement):** Low risk. Same API surface, just different import.
- **Task 5-6 (empty modules):** Low risk. Deleting empty modules won't break anything.
