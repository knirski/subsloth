# Lessons Learned

Hard-won patterns from implementing `android-ui-foundation` across 3 PRs.  
Read this before writing any Kotlin/Compose code in this repo.

---

## 1. Verify API Signatures Before Writing Code

Every time I assumed an API signature, I was wrong.  
**Always query docs or check the actual dependency before writing.**

| Assumption | Reality |
|---|---|
| `WindowWidthSizeClass` is in `material3` | ❌ It's in `material3-window-size-class` (separate artifact) |
| `WindowInsets.systemBars.top` | ❌ Returns pixels (`Int`), needs `with(LocalDensity)` or `asPaddingValues()` |
| `navigator.currentPaneAdaptedValue` exists | ❌ Doesn't exist in M3 Adaptive 1.2.0 |
| `CardDefaults.focusedBorder` exists | ❌ Doesn't exist in TV Material |
| `focusProperties { left = FocusDirection.Right }` | ❌ `left`/`right` expect `FocusRequester?` |
| `navigator.navigateBack()` is blocking | ❌ It's `suspend` — needs coroutine scope |
| rememberNavBackStack<AppNavKey>(LoginKey) | ❌ Use inference: returns NavBackStack<T> |
| `NavDisplay.onBack` accepts `null` | ❌ It's non-nullable `() -> Unit` |

**Fix:** grep for usage examples or query API docs before writing.

---

## 2. Run Checks Locally Before Push

```bash
./gradlew spotlessCheck :app:lintDebug :app:detekt :app:testDebugUnitTest
```

### ktlint/spotless traps

| What fails | Fix |
|---|---|
| `const val` naming | SCREAMING_SNAKE_CASE: `PHONE_WIDTH` not `PhoneWidth` |
| File name ≠ declaration | `DeviceConfig.kt` → `ScreenshotDevices.kt` |
| Composable lambda position | Must be last param, after `modifier` |
| Fully-qualified names | Use imports, never `androidx.foo.Bar(...)` |
| Single-expression functions | `fun foo() = expr` not `fun foo() { return expr }` |

### Detekt traps (fires often)

| Rule | Fix |
|---|---|
| `TopLevelPropertyNaming` | CamelCase starting lowercase: `minimumTouchTarget` |
| `MagicNumber` | Named constant: `COMPACT_WIDTH_THRESHOLD` |
| `ExpressionBodySyntax` | `=` syntax for single-expression |
| `NoFullyQualifiedNames` | Add import |
| `ClassOrdering` | Properties before methods |
| `ParameterNaming` | Present tense: `onActionSelect` not `onActionSelected` |
| `ModifierMissing` | `@Composable` → `modifier: Modifier = Modifier` |
| `ComposableParamOrder` | Required → `modifier` → optional → trailing lambda |

### Lint traps

| Rule | Fix |
|---|---|
| `ConfigurationScreenWidthHeight` | `LocalWindowInfo.current.containerSize` not `LocalConfiguration.current.screenWidthDp` |
| `ComposableLambdaParameterPosition` | Lambda must be last parameter |

---

## 3. Dependencies: You Need to Add Them

| Feature | Catalog entries needed | Plugin |
|---|---|---|
| Material 3 Adaptive | `material3.adaptive`, `.adaptive-layout`, `.adaptive-navigation` | — |
| TV UI | `tv-foundation`, `tv-material` | — |
| Kotlin Serialization | `kotlinx-serialization-json` | `kotlin.plugin.serialization` |
| Roborazzi | `roborazzi`, `.compose`, `.junit.rule` | `alias(libs.plugins.roborazzi)` |
| Navigation3 | `navigation3-runtime`, `navigation3-ui` | — |

**Always use `alias(libs.plugins.xxx)` not `id("...")`** for catalog plugins.

---

## 4. Navigation3 Patterns

```kotlin
@Serializable data object LoginKey : NavKey
@Serializable data class DetailKey(val id: String) : NavKey

val backStack = rememberNavBackStack(LoginKey)   // survives process death

NavDisplay(
    backStack = backStack,
    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider = entryProvider {
        entry<LoginKey> { /* screen */ }
        entry<DetailKey> { key -> /* key.id */ }
    },
)
```

- `onBack` is non-nullable, guard with `size > 1` to keep root
- `.size` works (NavBackStack extends List)
- `rememberSaveableStateHolderNavEntryDecorator()` preserves composable state

---

## 5. Gitignore Traps

`screenshots/` matches **any** `screenshots/` directory anywhere — it will silently ignore `testing/screenshots/`.  
Once tracked, gitignore doesn't apply. Use `git add -f` for force-add.

---

## 6. Compose Parameter Order

```kotlin
@Composable
fun MyComponent(
    required: String,          // 1. Required params
    modifier: Modifier = Modifier,  // 2. Modifier
    optional: Int = 0,         // 3. Optional params
    content: @Composable () -> Unit, // 4. Trailing lambda (last!)
)
```

---

## 7. General Defense

- **Verify API assumptions** — grep or query docs first.
- **Run `spotlessCheck` before every push** — fastest gate.
- **Formatting PRs separate from functional PRs** — spotless touches many files.
- **Check `.gitignore` before adding modules**.
- **`read_file` may show a buffer, not disk** — verify with `cat` via terminal.
- **After GitHub API file creates, fetch locally first** to avoid SHA conflicts.
- **Scope `spotlessApply`** to only your module: `./gradlew :app:spotlessApply`.
- **PR review replies** — explain each fix, then resolve the thread.

---

## 8. CI Monitoring: Use `gh run watch`

Don't poll with `sleep && gh run view` — use `gh run watch <run-id>` instead. It refreshes every 3 seconds and exits when the run completes, saving time and context. Find the run ID via `gh run list` or the status check rollup URL. The `--log-failed` flag on `gh run view` is useful after a failure to see what went wrong.

---

## 9. Kotlin 2.3 / kotlinx-datetime 0.8.0 Gotchas

| Trap | Reality |
|---|---|
| `kotlinx.datetime.Instant` | Deprecated typealias → use `kotlin.time.Instant` from stdlib. No dependency needed. |
| `LocalDate.atStartOfDayIn(TimeZone)` | **Removed** in 0.8.0. Use `LocalDate.toEpochDays().days.inWholeSeconds` with `Instant.fromEpochSeconds()`. |
| `DateTimeFormatException` | **Internal** in 0.8.0. Catch `IllegalArgumentException` instead. |
| `kotlin.time.Duration.seconds` | Needs `import kotlin.time.Duration.Companion.seconds`. `Long.seconds` extension already imported transitively. |
| `kotlin.time.toComponents` | Not available without `@OptIn`. Use `inWholeHours` + `inWholeMinutes % 60` for time decomposition. |

---

## 10. Detekt Alpha Crashes

Detekt `2.0.0-alpha.3` crashes on qualified constant references in `@Preview` annotations:

```
No receiver found in qualified expression
at KtQualifiedExpression.getReceiverExpression(...)
```
No receiver found in qualified expression
at KtQualifiedExpression.getReceiverExpression(...)
```

| Approach | Result |
|---|---|
| `import android.content.res.Configuration` + `@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)` | Detekt parse crash on qualified expression |
| `@Preview(uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)` (fully qualified) | `NoFullyQualifiedNames` rule |

**Fix — static import of the constant:**

```kotlin
import android.content.res.Configuration.UI_MODE_NIGHT_YES

@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
```

This avoids the qualified-expression path that triggers the parser bug. If the static import also triggers the crash, fall back to a literal with an explanatory line comment:

```kotlin
// Configuration.UI_MODE_NIGHT_YES = 32 (0x20)
@Preview(showBackground = true, uiMode = 32)
```

Note: `Configuration.UI_MODE_NIGHT_YES` = `32` (0x20), NOT `2` (0x02 = `UI_MODE_TYPE_DESK`).

---

## 11. ktlint Inline Block Comments

ktlint `standard:comment-wrapping` rule forbids block comments on the same line as other elements:

```kotlin
// ❌ Fails ktlint
@Preview(showBackground = true, uiMode = 32 /* Configuration.UI_MODE_NIGHT_YES */)

// ✅ Passes
// Configuration.UI_MODE_NIGHT_YES = 32
@Preview(showBackground = true, uiMode = 32)
```

---

## 12. Compose Stability Config Format

`config/compose_stability.conf` accepts **one fully-qualified class per line, no comments**:

```text
# ❌ Comments are NOT valid — parse error
kotlin.time.Instant
```

```text
kotlin.time.Instant
```

Wire it in convention plugins:

```kotlin
composeCompiler {
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("config/compose_stability.conf"),
    )
}
```

---

## 13. `MutableStateFlow.update` Needs Explicit Import

`MutableStateFlow.update` is an inline extension in `kotlinx.coroutines.flow`. Even when `MutableStateFlow` is imported, you must also import the extension:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update  // ← required!
```

Without it: `Unresolved reference 'update'`.

**Lambda purity:** the transformation lambda passed to `update {}` may be re-executed if `compareAndSet` fails. Capture external mutable state into local variables before the call:

```kotlin
// ❌ impure — playerController?.isPlaying() may change if lambda retries
_uiState.update { current ->
    current.copy(isPlaying = playerController?.isPlaying() ?: false)
}

// ✅ pure — isPlaying captured once before the atomic block
val playing = playerController?.isPlaying() ?: false
_uiState.update { current -> current.copy(isPlaying = playing) }
```

---

## 14. Architecture Test Patterns

### Source-scanning approach (no ArchUnit needed)

```kotlin
private val allImports: List<String> by lazy {
    val sourceDir = java.io.File("src/main/kotlin")
    sourceDir.walkTopDown()
        .filter { it.extension == "kt" }
        .flatMap { file ->
            file.useLines { lines ->
                lines.filter { it.trimStart().startsWith("import ") }
                    .map { it.trim() }
                    .toList()
            }
        }
        .toList()
}

@Test
fun `no Android framework imports`() {
    val violations = allImports.filter { line ->
        val target = line.removePrefix("import").trim()
        forbiddenPrefixes.any { target.startsWith(it) } &&
            allowedPrefixes.none { target == it }
    }
    assertThat(violations).isEmpty()
}
```

### Banned dependency checks in CI

```bash
grep -rnwI -E '^\s*import\s+(arrow\.|dagger\.|com\.squareup\.moshi\.|com\.google\.gson\.|io\.kotest\.|io\.reactivex\.|androidx\.navigation\.compose\.)' \
    --include='*.kt' --include='*.java' \
    "$target" 2>/dev/null \
  | grep -v '/src/test/' \
  | grep -v '/src/androidTest/'
```

Put in `check-invariants.sh` — runs in `pre-checks` CI job, fails before heavy jobs start.

---

## 15. Version Catalog Merges: Add Entries, Don't Replace

When two PRs add different entries to `libs.versions.toml` or `build.gradle.kts`, the merge conflict is always a "keep both" resolution. Don't drop either entry.

---

## 16. Strict SemVer in Gradle Version Code

```kotlin
// ❌ Silent coercion — malformed tags produce 0
val major = parts.getOrElse(0) { "0" }.toIntOrNull() ?: 0

// ✅ Fail fast — malformed tags reject the build
require(numeric.matches(Regex("""\d+\.\d+\.\d+"""))) {
    "Version '$appVersionName' must be SemVer 'major.minor.patch' (got '$numeric')"
}
val major = parts[0].toInt()
```

Also strip build metadata: `appVersionName.substringBefore("-").substringBefore("+")`.

---

## 17. PR Merge Order: Foundation First

When N PRs touch overlapping files, merge from smallest scope to largest:

1. **Isolated changes first** (e.g. single-file cleanups)
2. **Foundation PRs** (new types, dependencies, convention plugin changes)
3. **Consumers** (PRs that use the new types/changes)

Rebase consumer PRs onto updated main after each foundation merge. Git auto-merges work well when changes are in *different function bodies* — they conflict only when both PRs touch the exact same lines.

---

## 18. `@ReadOnlyComposable` for Pure Composition-Local Readers

Functions that only read `Local*` composition locals and compute a value without emitting nodes should be `@ReadOnlyComposable`:

```kotlin
@Composable
@ReadOnlyComposable
fun currentDeviceFormFactor(): DeviceFormFactor {
    val widthDp = LocalWindowInfo.current.containerSize.width
    return when { ... }
}
```

This signals to the compiler that the function is side-effect-free and can be called from init/memoization contexts.
