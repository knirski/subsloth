# Compose Performance

Three pillars: **stability** (correct skipping), **recomposition control** (narrow state reads), **efficient Flow collection** (lifecycle-aware).

## Stability Annotations

| Annotation | When | Effect |
|---|---|---|
| `@Immutable` | Type produces same result forever (enums, sealed interfaces, data classes with only `val` + stable types) | Compose skips when inputs equal via `equals` |
| `@Stable` | Type is mutable but notifies Compose of changes (mutable state holders, ViewModels with `StateFlow`) | Compose trusts `equals` for skipping despite mutable internals |

Compiler inference: `sealed interface`/`sealed class` inferred stable if all constructor params stable. `data class` with `val` + stable types inferred immutable. `interface` parameter never inferred stable — wrap in `@Stable` or use concrete type. `List` parameter is fragile — use `ImmutableList` from kotlinx.collections.immutable for guarantees.

Project state: `@Immutable` on domain model data classes and UiState data class branches. `@Stable` on sealed UiState types (`LoginUiState`, `HomeUiState`, `SearchUiState`, `DetailUiState`, `PlayerUiState`) and domain sealed interfaces.

## ImmutableList

Use `kotlinx.collections.immutable.ImmutableList` for Compose-stable list state. `List` interface is not trusted by Compose compiler. `ImmutableList` is explicitly stable.

```kotlin
data class Results(val items: ImmutableList<Media>)   // ✓ Compose-infers stable
data class Results(val items: List<Media>)            // ✗ triggers defensive skipping
```

## derivedStateOf

Use when a composable reads a value that changes more often than the derived computation needs to re-run. Lambda re-executes only when captured State/StateFlow inputs change.

```kotlin
val isPastThreshold by remember { derivedStateOf { listState.firstVisibleItemIndex > 5 } }
```

Not helpful when input changes at same rate as output.

## Flow Collection

Default to `collectAsStateWithLifecycle()` from `lifecycle-runtime-compose` — stops collection when lifecycle drops below STARTED.

```kotlin
val state by viewModel.uiState.collectAsStateWithLifecycle()   // ✓ preferred
val state by viewModel.uiState.collectAsState()                // only when above unavailable
```

## Key Stability

Provide stable keys in `LazyColumn`/`LazyRow`:

```kotlin
items(items, key = { it.id }) { item -> ItemRow(item) }    // ✓ stable domain key
items(items.indices) { index -> ItemRow(items[index]) }     // ✗ index key breaks reuse
```

## Recomposition Prevention

| Pattern | Skippable? | Why |
|---|---|---|
| `viewModel::onEvent` | ✅ | Function reference, no allocation |
| `{ viewModel.onEvent(it) }` | ❌ | Lambda allocates each recomposition |
| `remember(viewModel) { { viewModel.onEvent(it) } }` | ✅ | Stable lambda via remember |

## remember Variants

| Variant | Survives |
|---|---|
| `remember { mutableStateOf(...) }` | Recomposition |
| `rememberSaveable { mutableStateOf(...) }` | Recomposition + config change |
| `remember(key) { ... }` | Recomposition; recomputes when key changes |

## Diagnostics

| Tool | Purpose |
|---|---|
| Compose Layout Inspector | Composable tree, recomposition counts |
| Compose compiler metrics | `./gradlew assembleRelease -PcomposeCompilerMetrics=true` — per-file stability reports |
| detekt compose rules | Catches missing stability, unstable lambdas, index-keyed LazyColumn |

## References

- `docs/codestyle.md`: FC/IS architecture
- `docs/agent/README.md`: shared guidance routing
- kotlinx.collections.immutable API docs
- life-cycle-runtime-compose: `collectAsStateWithLifecycle`
