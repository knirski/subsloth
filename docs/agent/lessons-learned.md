# Lessons Learned

Hard-won patterns from implementing this repo. Read before writing Kotlin/Compose code.

## 1. Verify API Signatures Before Writing

| Assumption | Reality |
|---|---|
| `WindowWidthSizeClass` is in `material3` | ❌ In `material3-window-size-class` |
| `WindowInsets.systemBars.top` returns dp | ❌ Returns pixels — needs `with(LocalDensity)` or `asPaddingValues()` |
| `rememberNavBackStack<AppNavKey>(LoginKey)` | ❌ Use inference: `rememberNavBackStack(LoginKey)` |
| `NavDisplay.onBack` accepts `null` | ❌ Non-nullable `() -> Unit` |
| `focusProperties { left = FocusDirection.Right }` | ❌ `left`/`right` expect `FocusRequester?` |

Fix: grep for usage examples or query API docs before writing.

## 2. Run Checks Locally Before Push

### ktlint/spotless traps

| What fails | Fix |
|---|---|
| `const val` naming | SCREAMING_SNAKE_CASE: `PHONE_WIDTH` not `PhoneWidth` |
| File name ≠ declaration | `DeviceConfig.kt` → `ScreenshotDevices.kt` |
| Composable lambda position | Must be last param, after `modifier` |
| Single-expression functions | `fun foo() = expr` not `fun foo() { return expr }` |

### Detekt traps (fires often)

| Rule | Fix |
|---|---|
| `MagicNumber` | Named constant: `COMPACT_WIDTH_THRESHOLD` |
| `ExpressionBodySyntax` | `=` syntax for single-expression |
| `NoFullyQualifiedNames` | Add import |
| `ClassOrdering` | Properties before methods |
| `ModifierMissing` | `@Composable` → `modifier: Modifier = Modifier` |
| `ComposableParamOrder` | Required → `modifier` → optional → trailing lambda |

## 3. Dependencies: You Need to Add Them

| Feature | Catalog entries | Plugin |
|---|---|---|
| Material 3 Adaptive | `material3.adaptive`, `.adaptive-layout`, `.adaptive-navigation` | — |
| TV UI | `tv-foundation`, `tv-material` | — |
| Kotlin Serialization | `kotlinx-serialization-json` | `kotlin.plugin.serialization` |
| Roborazzi | `roborazzi`, `.compose`, `.junit.rule` | `alias(libs.plugins.roborazzi)` |
| Navigation3 | `navigation3-runtime`, `navigation3-ui` | — |

Always use `alias(libs.plugins.xxx)` not `id("...")`.

## 4. Navigation3 Patterns

```kotlin
@Serializable data object LoginKey : NavKey
@Serializable data class DetailKey(val id: String) : NavKey

val backStack = rememberNavBackStack(LoginKey)
NavDisplay(backStack = backStack,
    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider = entryProvider { entry<LoginKey> { ... } })
```

`onBack` is non-nullable, guard with `size > 1`. `rememberSaveableStateHolderNavEntryDecorator()` preserves composable state.

## 5. Compose Parameter Order

```kotlin
@Composable
fun MyComponent(
    required: String,                    // 1. Required params
    modifier: Modifier = Modifier,       // 2. Modifier
    optional: Int = 0,                   // 3. Optional params
    content: @Composable () -> Unit,      // 4. Trailing lambda
)
```

## 6. Gitignore Traps

`screenshots/` matches ANY `screenshots/` directory. Once tracked, gitignore doesn't apply. Use `git add -f`.

## 7. CI: Use `gh run watch`, Don't Poll

`gh run watch <run-id>` refreshes every 3 seconds and exits on completion. Post-failure: `gh run view --log-failed`.

## 8. Kotlin 2.3 / kotlinx-datetime Gotchas

| Trap | Fix |
|---|---|
| `kotlinx.datetime.Instant` deprecated | Use `kotlin.time.Instant` (stdlib, no dep needed) |
| `LocalDate.atStartOfDayIn(TimeZone)` broken | `LocalDate.toEpochDays().days.inWholeSeconds` + `Instant.fromEpochSeconds()` |
| `DateTimeFormatException` not accessible | Catch `IllegalArgumentException` |
| `kotlin.time.Duration.Companion.seconds` | Needs explicit import |
| `toComponents` needs `@OptIn` | Use `inWholeHours` + `inWholeMinutes % 60` |

## 9. Compose Stability Config

`config/compose_stability.conf` — one fully-qualified class per line. NO comments (parse error). Wire in convention plugins via `composeCompiler { stabilityConfigurationFiles.add(...) }`.

## 10. `MutableStateFlow.update` Needs Explicit Import

```kotlin
import kotlinx.coroutines.flow.update  // required even if MutableStateFlow imported
```

Lambda purity: the transformation lambda may re-execute if CAS fails. Capture external state into locals before calling.

## 11. Architecture Tests (no ArchUnit needed)

Source-scanning approach:
```kotlin
private val allImports: List<String> by lazy { ... walkTopDown, filter .kt, extract import lines }
@Test fun `no Android imports`() { assertThat(allImports.filter { forbidden in it }).isEmpty() }
```

## 12. Strict SemVer in Gradle Version Code

```kotlin
require(numeric.matches(Regex("""\d+\.\d+\.\d+"""))) { "Version must be SemVer" }
```

## 13. PR Merge Order: Foundation First

1. Isolated changes → 2. Foundation (types, deps) → 3. Consumers. Rebase after each foundation merge.

## 14. Issue-Level Comments are Separate from Review Threads

`github-actions[bot]` and `gemini-code-assist` post issue-level comments — they don't appear in `reviewThreads`. Always fetch both: `get_review_comments` (inline) + `get_comments` (issue-level).

## 15. Method Name Shadowing Causes Silent StackOverflow

```kotlin
private val setQuality: (String?) -> Unit = {},
fun setQuality(quality: String?) { setQuality(quality) }  // calls itself
```

Constructor param and method with identical name = infinite recursion. The StackOverflowError kills the test executor thread without a visible crash — tests just hang. Fix: prefix either the param or method (e.g. `writeQuality` / `onQualityChanged`).

## 16. Force-Push Orphans Review Threads

Every `git push --force` makes existing inline review comments unresolvable because they reference commits that no longer exist in the branch. Use incremental commits + normal pushes during review. Squash at merge, not before.

## 17. Stale Gradle Configuration Cache Masks Errors

When a build hangs, run with `--no-configuration-cache` first. In this session, a stale cache hid a StackOverflowError and made it look like a coroutine deadlock. Fresh cache revealed the real error immediately.

## 18. Keep PRs Small

This PR grew to ~2600 lines across 22 files (library + settings + downloads + diagnostics + KMP targets + CI + AGENTS.md). Scope creep makes review harder and merge slower. Split into multiple PRs: one per feature, one per infra change.

## 19. `combine` Not the Enemy

```kotlin
combine(f1, f2) { a, b -> ... }.collect { state = it }
```

`combine` is the correct reactive pattern for settings/preferences screens. Earlier churn between `combine` → `.first()` → `combine` came from misdiagnosing a hang as a combine issue. The hang was a StackOverflowError (see §15). When you need reactive collection from multiple flows, use `combine` — it's correct, not the problem.

## 20. Slider Without `onValueChangeFinished` Writes on Every Pixel

```kotlin
var localValue by remember { mutableStateOf(initial) }
Slider(value = localValue, onValueChange = { localValue = it },
    onValueChangeFinished = { save(localValue) })
```

Without this pattern, every drag pixel triggers a disk write (DataStore). Always debounce sliders with local state + `onValueChangeFinished`.
