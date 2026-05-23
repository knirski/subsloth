# Compose Performance: Agent Instructions

Conventions for writing performant Jetpack Compose UI. Compose performance is determined by three pillars: **stability** (correct skipping), **recomposition control** (narrow state reads), and **efficient Flow collection** (lifecycle-aware).

This project uses the FC/IS architecture, see `docs/codestyle.md`. The Compose UI layer sits in the Imperative Shell; stability annotations and collection patterns keep it efficient without violating the core/shell boundary.

---

## Stability Annotations

The Compose compiler infers stability from type structure. When inference fails or is conservative, annotate explicitly.

| Annotation | When to use | What it means |
|---|---|---|
| `@Immutable` | Type produces the same result forever (enum, data class with only `val` properties of known-stable types) | Compose skips recomposition when every input is equal via `equals`. Use on enums, sealed interfaces, data classes whose constructor params are all stable. |
| `@Stable` | Type is mutable but can notify Compose of changes (mutable state holders, view models with `StateFlow`, third-party wrappers) | Compose trusts `equals` for skipping, but the type may be mutated via `MutableState`. Use when Compose compiler assumes instability but the runtime contract is stable. |

**Compiler inference rules:**
- `sealed interface` / `sealed class` variants: inferred stable if all constructor params are stable.
- `data class` with only `val` + stable types: inferred immutable.
- `data class` with `List` parameter: The Kotlin 2.3+ Compose compiler can infer `List` as stable when content comes from an immutable source (e.g., `StateFlow<List<T>>` backed by `listOf()`), but this is fragile. For compile-time guarantees, use `ImmutableList` from kotlinx.collections.immutable instead of annotating with `@Stable`.
- `interface` parameter: never inferred stable. Wrap in `@Stable` marker or use a concrete stable type.

**Project state:** `@Immutable` is used on `DeviceFormFactor` and `ContrastState` enums. `@Stable` is not used anywhere. Screen-level sealed interfaces (`SearchUiState`, `HomeUiState`, `DetailUiState`) and their data class branches lack stability annotations. Add `@Stable` on the sealed interface and `@Immutable` on data class branches when their constructor params are stable.

```kotlin
@Stable
sealed interface SearchUiState {
    @Immutable
    data class Results(
        val query: String,
        val items: ImmutableList<Media>,
        val isLoading: Boolean = false,
    ) : SearchUiState
}
```

---

## ImmutableList

Use `kotlinx.collections.immutable.ImmutableList` (version catalog alias: `persistentlist`) for Compose-stable list state. The `List` interface is not trusted by the Compose compiler. `ImmutableList` is explicitly stable, so the compiler can skip recomposition of elements that haven't changed.

```kotlin
// Preferred — Compose-infers stable:
data class Results(val items: ImmutableList<Media>)

// NOT preferred — List interface triggers defensive skipping:
data class Results(val items: List<Media>)
```

**Project state:** All current UI models use `List<Media>`, no `ImmutableList` usage. Migrate UI state data classes as `kotlinx.collections.immutable` is added to consuming feature modules.

---

## derivedStateOf

Use `derivedStateOf` when a composable reads a value that changes more often than the derived computation needs to re-run. The lambda is re-executed only when one of its captured `State` or `StateFlow` inputs changes, and the result fires recomposition only when the derived output differs.

```kotlin
// Without derivedStateOf — recomposes on every scroll pixel change:
val firstVisibleItem = listState.firstVisibleItemIndex

// With derivedStateOf — recomposes only when the derived threshold is crossed:
val isPastThreshold by remember {
    derivedStateOf { listState.firstVisibleItemIndex > 5 }
}
```

**When to use:**
- Scroll position → visibility triggers (show/hide FAB, sticky header)
- Filtering or transforming frequently-changing state
- Deriving booleans or enums from raw state

**When not to use:** If the computation is trivial and reads from a seldom-changing source, a plain `val` or `remember` is sufficient.

**Project state:** `derivedStateOf` is not used in the codebase. Scroll-based visibility (e.g., "show scroll-to-top" on HomeScreen) is a candidate.

---

## Flow Collection

Default to `collectAsStateWithLifecycle()` from `androidx.lifecycle:lifecycle-runtime-compose` for collecting Kotlin Flows into Compose state. This stops collection when the lifecycle drops below `STARTED`, preventing wasted work and respecting Android lifecycle guarantees.

```kotlin
// Preferred — lifecycle-aware:
val state by viewModel.uiState.collectAsStateWithLifecycle()

// Only when collectAsStateWithLifecycle is unavailable:
val state by viewModel.uiState.collectAsState()
```

**Project state:** `collectAsStateWithLifecycle()` is used in `PlayerScreen`, `HomeScreen`, `SearchScreen`, `MovieDetailScreen`, `SeriesDetailScreen`. `LoginScreen` uses `collectAsState()` instead. This is a known gap. Migrate when the lifecycle dependency is accessible from that module.

---

## Key Stability

Always provide stable keys in `LazyColumn` / `LazyRow` items. Index-based keys cause composition of items that should be reused, breaking scroll state and animation.

```kotlin
// Preferred — stable domain key:
LazyColumn {
    items(items, key = { it.id }) { item -> ItemRow(item) }
}

// NOT preferred — index key defeats structural reuse:
items(items.indices) { index -> ItemRow(items[index]) }
```

**Project state:** `HomeScreen` uses `item(key = row.label)` for row headers and `item(key = media.id)` for media items. `SearchScreen` uses `items(s.items, key = { it.id })`. Both follow the stable-key convention.

---

## Recomposition Prevention

| Pattern | Composable skip? | Detail |
|---|---|---|
| `viewModel::onEvent` (function reference) | ✅ Skippable | Pass function reference, no lambda allocation |
| `{ viewModel.onEvent(it) }` (lambda) | ❌ Not skippable | Lambda captures `viewModel`, new allocation each recomposition |
| `remember(viewModel) { { viewModel.onEvent(it) } }` | ✅ Skippable | Stable lambda via `remember` |
| `Modifier.` extension (defined at file or class scope) | ✅ Skippable | No lambda capture, stable reference |

Prefer function references (`::`) or `remember`-stabilised lambdas. Inline lambdas that capture state force recomposition of the parent.

---

## remember Variants

| Variant | Use case |
|---|---|
| `remember { mutableStateOf(...) }` | State that survives recomposition but not configuration change |
| `rememberSaveable { mutableStateOf(...) }` | State that survives both recomposition and configuration change (saved instance state) |
| `remember(key) { ... }` | Value tied to a specific key, recomputed when key changes |

```kotlin
// Survives configuration change (rotation, theme switch):
var login by rememberSaveable { mutableStateOf("") }
var password by rememberSaveable { mutableStateOf("") }
```

**Project state:** `LoginScreen` uses `remember { mutableStateOf("") }` for login/password fields. These should be `rememberSaveable` to survive config changes. `PlayerScreen` correctly uses `remember` for transient UI state (picker toggles, drag position only).

---

## LaunchedEffect and DisposableEffect

Use `LaunchedEffect` for side effects tied to a lifecycle or state key. The coroutine is cancelled and relaunched when the key changes.

```kotlin
LaunchedEffect(unit) { viewModel.events.collect { event -> handleEvent(event) } }
```

For cleanup (removing listeners, unregistering receivers), use `DisposableEffect` with an `onDispose` block. Prefer `rememberUpdatedState` when the effect captures a lambda that might close over stale state.

```kotlin
val currentOnClick by rememberUpdatedState(onClick)
LaunchedEffect(unit) {
    someCallback.register { currentOnClick() }
}
```

---

## Diagnostics

| Tool | Purpose |
|---|---|
| Compose Layout Inspector (Android Studio) | Real-time composable tree, recomposition counts, state inspection |
| Compose compiler metrics | `./gradlew assembleRelease -PcomposeCompilerMetrics=true` generates per-file stability and restrictiveness reports |
| `compose-rules-detekt` | Detekt rules from `io.nlopez.compose.rules:detekt`. Enable in `detekt.yml` to catch missing stability annotations, unstable lambdas, and index-keyed LazyColumn items |

Enable compose compiler metrics in `gradle.properties` during optimisation passes:
```properties
kotlin.composeCompilerMetrics=true
composeCompilerReportOutput=build/compose-metrics
```

---

## References

- `androidx.compose.runtime`: stability, `remember`, `derivedStateOf`, `LaunchedEffect`, `DisposableEffect`
- `androidx.lifecycle.compose`: `collectAsStateWithLifecycle` reference, lifecycle-runtime-compose API docs
- `kotlinx.collections.immutable`: `ImmutableList`, `ImmutableSet`, `ImmutableMap`, GitHub README and API docs
- `docs/codestyle.md`: FC/IS architecture rules for this project
- `docs/agent/README.md`: shared agent guidance routing
