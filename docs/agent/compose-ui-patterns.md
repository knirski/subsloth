# Compose UI Patterns: Agent Instructions

Conventions for writing Jetpack Compose UI in the subsloth project. Compose is the Imperative Shell's rendering layer. It consumes sealed UiState ADTs from ViewModels and emits user actions back.

This project uses the FC/IS architecture (see `docs/codestyle.md` and `docs/agent/fc-is-architecture.md`). The core/shell boundary matters for Compose: domain state stays in pure model types, and the Compose layer is where those types become render trees. See `docs/agent/compose-performance.md` for efficiency rules (stability, recomposition, Flow collection).

---

## Unidirectional Data Flow

State flows DOWN (ViewModel to Composable). Events flow UP (Composable to ViewModel via lambda callbacks). Never mutate state from composables.

The pattern used across the project:

```kotlin
// ViewModel exposes a sealed UiState as StateFlow.
private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

// Composable collects with collectAsStateWithLifecycle().
// Lambda callbacks flow events UP.
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val s = state) {
        // ...
    }
}
```

Every screen follows this structure: one sealed UiState, one ViewModel, one composable entry point with lambda callbacks for actions.

---

## State Hoisting

Lift state to the nearest common ancestor. The ViewModel is the state holder for screen-level state. Composables receive state as parameters and emit events through lambdas. A composable that reads its own `remember` or `mutableStateOf` should be a leaf widget with no screen-level responsibility.

```kotlin
// ViewModel holds uiState, composable receives it.
@Composable
fun HomeScreen(state: HomeUiState, onAction: (HomeAction) -> Unit)

// Leaf widgets receive the slice they need.
@Composable
fun MediaCard(media: Media, onClick: () -> Unit)
```

Do not pass the ViewModel down more than one level. Extract the state slice each child needs and pass it as a parameter.

---

## Slot APIs

Use composable lambda parameters (`content: @Composable () -> Unit`) for flexible component composition. Prefer slot APIs over boolean flags or enum-based configuration. A component that toggles between two layouts with a boolean will grow into three, then four variants. A slot component stays open.

Project examples:

```kotlin
// PhoneScaffold uses a content slot.
@Composable
fun PhoneScaffold(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable (padding: PaddingValues) -> Unit,
)

// ListDetailLayout uses two slots for list and detail panes.
@Composable
fun SubSlothListDetailLayout(
    listContent: @Composable () -> Unit,
    detailContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    onBackFromDetail: () -> Unit = {},
)
```

When you see a parameter that selects between rendering strategies, replace it with a composable slot.

---

## Sealed UiState to Compose Mapping

A `when` block on the sealed UiState branches maps each state variant to a distinct Compose render tree. The compiler enforces exhaustiveness: adding a new variant to the sealed interface produces a compilation error at every `when` site until all branches are covered.

```kotlin
when (val s = state) {
    is HomeUiState.Loading -> {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    is HomeUiState.Content -> {
        CatalogContent(state = s, modifier = modifier, ...)
    }
}
```

UiState variants in the project follow a `Loading` / `Content` / `Error` pattern. Some screens add domain-specific content branches like `MovieContent` vs `ShowContent` in `DetailUiState`. Each branch is a `data class` with the data the render tree needs. No rendering logic lives in the ViewModel.

---

## Material3 Conventions

Use Material3 components throughout. Key conventions:

- `Scaffold` for screen-level chrome (TopAppBar, NavigationBar, FAB).
- `TopAppBar` (not `CenterAlignedTopAppBar` except on tablet/TV).
- `NavigationBar` for phone, `NavigationRail` for tablet, leanback/tv navigation for TV surfaces.
- `MaterialTheme.colorScheme` for all colors. Do not hardcode color values.
- `MaterialTheme.typography` for all text styles.
- `Card` with `CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)` for media cards.
- Surface variants for containers: `surfaceVariant`, `surfaceContainerHigh`, `surfaceContainerLow`.

---

## Adaptive Layouts

The project targets phone, tablet, and TV with different layouts. The `DeviceFormFactor` enum (`Phone`, `Tablet`, `Tv`) classifies the current window width using `LocalWindowInfo.current.containerSize.width`. Thresholds follow Material3 window size classes: compact <600dp, medium 600-840dp, expanded >840dp.

```kotlin
@Composable
fun currentDeviceFormFactor(): DeviceFormFactor {
    val widthDp = LocalWindowInfo.current.containerSize.width
    return when {
        widthDp < COMPACT_WIDTH_THRESHOLD -> DeviceFormFactor.Phone
        widthDp < MEDIUM_WIDTH_THRESHOLD -> DeviceFormFactor.Tablet
        else -> DeviceFormFactor.Tv
    }
}
```

Phone uses `PhoneScaffold` with a single-pane layout and bottom navigation. Tablet uses `SubSlothListDetailLayout` with side-by-side list and detail panes. TV uses leanback navigation patterns. Use `isTabletOrWider()` for single-condition branching in layout code.

---

## Compose Previews

Add `@Preview` functions for every variant of the sealed UiState. Each variant gets its own preview composable. This catches rendering regressions at edit time without running the app.

```kotlin
@Preview(showBackground = true)
@Composable
fun HomeScreenLoadingPreview() = HomeScreen(
    state = HomeUiState.Loading,
    onMovieClick = {},
    onShowClick = {},
)

@Preview(showBackground = true)
@Composable
fun HomeScreenContentPreview() = HomeScreen(
    state = HomeUiState.Content(rows = previewRows, selectedTab = HomeTab.MOVIES),
    onMovieClick = {},
    onShowClick = {},
)
```

Include light/dark mode previews and font scale previews for accessibility. Use `@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)` for dark mode. Use `@Preview(fontScale = 1.5f)` for large text. The project does not currently have `@Preview` annotations; add them when creating or editing screen composables.

---

## References

- `docs/codestyle.md`: FC/IS architecture rules, sealed types, pure functions.
- `docs/agent/fc-is-architecture.md`: core/shell boundary, sealed ADTs, port/adapter.
- `docs/agent/compose-performance.md`: stability annotations, `ImmutableList`, `derivedStateOf`, `collectAsStateWithLifecycle`, recomposition prevention.
- `docs/agent/README.md`: shared agent guidance routing for domain skills.
- Official Jetpack Compose docs: state hoisting, slot APIs, Material3, adaptive layouts.
