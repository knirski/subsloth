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
