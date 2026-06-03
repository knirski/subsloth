# Compose UI Patterns

Conventions for writing Compose UI. Compose is the Imperative Shell's rendering layer — consumes sealed UiState from ViewModels, emits user actions back.

## Unidirectional Data Flow

State DOWN (ViewModel → Composable via `StateFlow`). Events UP (Composable → ViewModel via lambda callbacks). Never mutate state from composables.

```kotlin
// ViewModel exposes sealed UiState as StateFlow
private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

// Composable collects, passes lambdas for actions
@Composable
fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) { /* render */ }
}
```

Every screen: one sealed UiState, one ViewModel, one composable entry point with lambda callbacks.

## State Hoisting

Lift state to nearest common ancestor. ViewModel holds screen-level state. Composables receive state as parameters, emit events through lambdas. A composable reading its own `remember`/`mutableStateOf` should be a leaf widget.

Do not pass ViewModel down more than one level. Extract the state slice each child needs.

## Slot APIs

Use composable lambda parameters (`content: @Composable () -> Unit`) for flexible composition. Prefer slots over boolean flags or enum-based configuration.

```kotlin
@Composable
fun PhoneScaffold(modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (padding: PaddingValues) -> Unit)
```

When you see a parameter selecting between rendering strategies, replace with a slot.

## Sealed UiState to Compose Mapping

Exhaustive `when` on sealed UiState maps each variant to a distinct render tree. Adding a new variant produces a compilation error at every `when` site.

```kotlin
when (val s = state) {
    is HomeUiState.Loading -> { Box { CircularProgressIndicator() } }
    is HomeUiState.Content -> { CatalogContent(state = s) }
}
```

Pattern: `Loading` / `Content` / `Error`. No `else` branch. No rendering logic in ViewModel.

## Material3 Conventions

- `Scaffold` for screen-level chrome. `TopAppBar` (not CenterAligned except tablet/TV).
- `NavigationBar` for phone, `NavigationRail` for tablet, leanback for TV.
- `MaterialTheme.colorScheme` for all colors. No hardcoded values.
- `MaterialTheme.typography` for all text styles.
- `Card` with `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)` for media cards.

## Adaptive Layouts

Phone (compact <600dp), Tablet (medium 600-840dp), TV (expanded >840dp). Classify via `LocalWindowInfo.current.containerSize.width`. Phone: single-pane `PhoneScaffold` + bottom nav. Tablet: `SubSlothListDetailLayout` side-by-side. TV: leanback with `TvRow`, `TvLargeCard`, `TvActionRail`.

## Compose Previews

`@Preview` for every UiState variant. Include light/dark mode (`uiMode = Configuration.UI_MODE_NIGHT_YES`) and font scale (`fontScale = 1.5f`) variants for accessibility.

## References

- `docs/codestyle.md`: FC/IS architecture rules
- `docs/agent/fc-is-architecture.md`: core/shell boundary
- `docs/agent/compose-performance.md`: stability, ImmutableList, collectAsStateWithLifecycle
- Official Jetpack Compose docs: state hoisting, slot APIs, Material3, adaptive layouts
