# Kotlin Coroutines Conventions

Structured concurrency conventions for SubSloth. Builds on FC/IS rules in `docs/codestyle.md`.

## Structured Concurrency

Every coroutine must have a parent scope. No `GlobalScope`. No unstructured `launch`/`async`. If you can't point to who owns a coroutine's lifecycle, don't launch it.

## Scopes

| Scope | Where | Lifecycle | When |
|---|---|---|---|
| `viewModelScope` | ViewModel | `onCleared()` | All ViewModel coroutines |
| `lifecycleScope` | Activity / Compose | `onDestroy` | UI-layer work + `repeatOnLifecycle` |
| `CoroutineScope(SupervisorJob() + Dispatchers.Default)` | Application singletons | App lifetime | Background work, long-running services |

## Dispatchers

| Operation | Dispatcher | Why |
|---|---|---|
| Pure computation (filter, sort, map) | `Dispatchers.Default` | CPU-bound |
| I/O (network, database) | `Dispatchers.IO` | Elastic thread pool |
| UI state update | `Dispatchers.Main.immediate` | Default for `viewModelScope.launch` |
| Flow collection triggering UI | `Dispatchers.Main` | Collector's context |

Target: constructor-injected dispatchers for testability (`UnconfinedTestDispatcher`). Keep dispatcher decisions at shell (ViewModel), never in domain layer.

## StateFlow

Expose immutable `StateFlow` via private `MutableStateFlow` + `asStateFlow()`:

```kotlin
private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.LoginForm())
val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
```

Use `.update {}` for atomic read-modify-write (thread-safe). `.value` assignment is not atomic across read+write.

Sealed UiState ADT per ViewModel: `data object` for singletons, `data class` for parameterized states. No booleans creating invalid flag combinations.

## SharedFlow (one-shot events)

```kotlin
private val _events = MutableSharedFlow<LoginEvent>(replay = 0)
val events = _events.asSharedFlow()
```

`replay=0` (no replay on recomposition), `extraBufferCapacity=0` (strict backpressure).

## Job Management

Cancel-before-start pattern for overlapping work:

```kotlin
searchJob?.cancel()
searchJob = viewModelScope.launch { ... }
```

Long-running loops must check cancellation cooperatively (`isActive`, `ensureActive()`). Cancel handles in `onCleared()` as safety net.

## Lifecycle-aware Collection

Compose: `collectAsStateWithLifecycle()` (stops when lifecycle drops below STARTED). Non-Compose: `repeatOnLifecycle(Lifecycle.State.STARTED)` inside `lifecycleScope`. Never use deprecated `launchWhen*` APIs.

## Cancellation Handling

Must rethrow `CancellationException` — swallowing it breaks structured concurrency. Prefer `.fold {}` on `Result<T>` over `runCatching` (which swallows CancellationException).

```kotlin
result.fold(onSuccess = { }, onFailure = { })        // ✓ correct
runCatching { }.onFailure { }                          // ✗ swallows CancellationException
```

## Testing

Use `UnconfinedTestDispatcher` + `Dispatchers.setMain`/`resetMain` in every ViewModel test (standard pattern). Always use `runTest(testDispatcher)`. Use Turbine's `.test {}` for Flow assertions: `awaitItem()`, `cancelAndIgnoreRemainingEvents()`.

Standard pattern:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    @BeforeEach fun setUp() { Dispatchers.setMain(testDispatcher) }
    @AfterEach fun tearDown() { Dispatchers.resetMain() }
    @Test fun `behavior`() = runTest(testDispatcher) { ... }
}
```

## Triage

When a coroutine bug surfaces: 1. Identify scope (cancelled parent?). 2. Check cancellation (swallowed `CancellationException`?). 3. Verify dispatcher (blocking Main?). 4. Apply smallest fix (cancel-before-start, cooperative check, rethrow CE, lifecycle-aware collection, `.update {}`).

## References

- `docs/codestyle.md`: FC/IS architecture, ADTs, pure functions
- kotlinx.coroutines structured concurrency docs
- Turbine README: Flow testing assertions
