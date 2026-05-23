# Kotlin Coroutines Conventions

Structured concurrency conventions for the SubSloth Android app. These build on the FC/IS rules in `docs/codestyle.md`. Read that first.

---

## 1. Structured Concurrency

Every coroutine must have a parent scope. No `GlobalScope`. No unstructured `launch` or `async`. A coroutine's lifetime is bounded by its scope, and cancellation of the scope cancels all child coroutines automatically.

**Rule of thumb:** If you can't point to who owns a coroutine's lifecycle, don't launch it.

---

## 2. Scopes

Use the right scope for the right layer. Never create ad-hoc scopes for lifecycle-bound work.

| Scope | Where | Lifecycle | When to use |
|---|---|---|---|
| `viewModelScope` | ViewModel | ViewModel's `onCleared()` | All ViewModel coroutines. Cancel stale work with job handles. |
| `lifecycleScope` | Activity / Fragment / Compose | Lifecycle owner's `onDestroy` | UI-layer work that needs lifecycle awareness. Pair with `repeatOnLifecycle`. |
| `CoroutineScope(SupervisorJob() + Dispatchers.Default)` | Application-level singletons | Application lifetime | Background workers, periodic sync, long-running services. Only for scopes that outlive a single screen. |

In this codebase, all ViewModels use `viewModelScope.launch {}`:

```kotlin
// feature/auth/src/main/kotlin/net/subsloth/auth/LoginViewModel.kt
viewModelScope.launch {
    val hasCredentials = hasStoredCredentials()
    // ...
}
```

For application-level scopes, inject a custom scope with `SupervisorJob()` so a single child failure does not cancel siblings.

---

## 3. Dispatchers

### Current state (gaps found)

This codebase does **not** yet use dispatcher injection. All ViewModel coroutines run on `Dispatchers.Main` (the default for `viewModelScope.launch`). There is no `Dispatchers.IO` or `Dispatchers.Default` usage in ViewModel code.

### Target convention

Hardcode `Dispatchers.IO` in ViewModels only as a temporary measure. The long-term target is constructor-injected dispatchers:

```kotlin
class MyViewModel(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    // ...
}
```

This makes tests predictable: inject `UnconfinedTestDispatcher` instead of depending on real thread pools.

### Default selection

| Operation | Dispatcher | Why |
|---|---|---|
| Pure computation (filter, sort, map) | `Dispatchers.Default` | CPU-bound, thread pool sized to core count |
| I/O (network call, database read) | `Dispatchers.IO` | Blocking or suspend I/O, elastic thread pool |
| UI state update | `Dispatchers.Main.immediate` | Required by StateFlow / Compose. Already the default in `viewModelScope.launch`. |
| Flow collection that triggers UI update | `Dispatchers.Main` | The collector's context, typically via `flowOn` upstream |

Never hardcode `Dispatchers.IO` inside a repository or domain layer function. Those layers should be pure suspend functions. Keep dispatcher decisions at the shell (ViewModel / Controller / Worker).

---

## 4. StateFlow

### Pattern

Expose an immutable `StateFlow` via a private `MutableStateFlow` backed by `asStateFlow()`. This is the universal pattern in this codebase:

```kotlin
// All ViewModels follow this:
private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.LoginForm())
val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
```

### Thread safety

Use `.update {}` to ensure atomic read-modify-write operations. While `.value` assignment is itself thread-safe, updating state via `_uiState.value = _uiState.value.copy(...)` is not atomic — the read and the write are separate steps, and concurrent coroutines (even on `Dispatchers.Main`) can race. The current codebase uses `.value =` directly, which is safe only because all writes happen sequentially on `Dispatchers.Main`. If dispatcher injection or concurrent writes are added, migrate to `.update {}`.

```kotlin
// Preferred for thread safety (future pattern):
_uiState.update { it.copy(positionSeconds = pos, durationSeconds = dur) }

// Current codebase uses this (safe on Main dispatcher):
_uiState.value = state.copy(positionSeconds = pos, durationSeconds = dur)
```

### Sealed UiState ADT

Every ViewModel defines a `sealed interface` for its UI state. This matches the ADT conventions in `docs/codestyle.md`. The sealed interface makes impossible states unrepresentable and forces exhaustive `when` handling.

See these existing types:

```kotlin
sealed interface PlayerUiState {          // PlayerViewModel.kt
    data object Loading : PlayerUiState
    data class Content(...) : PlayerUiState
}

sealed interface LoginUiState {           // LoginViewModel.kt
    data object Loading : LoginUiState
    data class LoginForm(...) : LoginUiState
    data object LoggedIn : LoginUiState
    data class AuthRepair(...) : LoginUiState
}

sealed interface SearchUiState {          // SearchViewModel.kt
    data object Idle : SearchUiState
    data class Results(...) : SearchUiState
}
```

**Rules:**
- `data object` for singleton states (Loading, Idle)
- `data class` for states with parameters (Content, Results, LoginForm)
- No booleans that create invalid flag combinations. Every valid combination should be its own variant.

---

## 5. SharedFlow (for one-shot events)

Not yet used in this codebase, but here is the convention for when it is needed.

Use `SharedFlow` for one-shot events (navigation, snackbar, dialog triggers) that should not be replayed on configuration change:

```kotlin
private val _events = MutableSharedFlow<LoginEvent>()
val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

fun login(login: String, password: String) {
    viewModelScope.launch {
        _events.emit(LoginEvent.NavigateToCatalog)
    }
}
```

Configuration:

| Property | Value | Reason |
|---|---|---|
| `replay` | `0` | No replay on recomposition |
| `extraBufferCapacity` | `0` or `1` | 0 for strict backpressure, 1 to avoid `tryEmit` failures on rare race conditions |

---

## 6. Job Management

### Cancel-before-start pattern

When launching a new coroutine that could overlap with a previous one, cancel the old job first. This prevents duplicate work and stale state updates.

```kotlin
// SearchViewModel.kt - search cancels previous search
searchJob?.cancel()
searchJob = viewModelScope.launch { ... }

// PlayerViewModel.kt - progress tracking cancels previous tracking
progressJob?.cancel()
progressJob = viewModelScope.launch {
    while (isActive) { ... }
}
```

### Cooperative cancellation

Long-running or looping coroutines must check cancellation cooperatively. Use `isActive`, `ensureActive()`, or `yield()` at safe suspension points.

```kotlin
// PlayerViewModel.kt uses isActive in its progress tracking loop:
while (isActive) {
    delay(PROGRESS_UPDATE_INTERVAL)
    // ...
}
```

For suspend function calls, cancellation is already cooperative (suspend functions check `isActive` implicitly). Add explicit checks only when the coroutine runs CPU-bound loops without suspension points.

### Cleanup in onCleared()

Cancel all job handles in `onCleared()`. This is a safety net even though `viewModelScope` cancels itself:

```kotlin
override fun onCleared() {
    progressJob?.cancel()
    countdownJob?.cancel()
    super.onCleared()
}
```

---

## 7. Lifecycle-aware Collection

### Compose screens

Use `collectAsStateWithLifecycle()` in all Compose `@Composable` functions. This API is lifecycle-aware and stops collection when the composable is not started (e.g., screen is in the backstack). This is the universal pattern in this codebase:

```kotlin
// All 5 composable screens use the same pattern:
val state by viewModel.uiState.collectAsStateWithLifecycle()
```

### Non-Compose (Fragment / Activity)

Use `repeatOnLifecycle(Lifecycle.State.STARTED)` inside a `lifecycleScope` launch block. This starts collection when the lifecycle reaches STARTED and cancels when it drops below STARTED.

```kotlin
lifecycleScope.launch {
    repeatOnLifecycle(Lifecycle.State.STARTED) {
        viewModel.uiState.collect { state -> /* update UI */ }
    }
}
```

### Never use these (deprecated)

- `launchWhenStarted` -- deprecated, does not stop collection when lifecycle falls below the started state
- `launchWhenResumed` -- deprecated, same issue
- `launchWhenCreated` -- deprecated, same issue

---

## 8. Cancellation Handling

### Must rethrow CancellationException

If you catch `Throwable` or use `try/catch` around coroutine code, you must rethrow `CancellationException`. Swallowing it breaks structured concurrency and leaves the parent scope unable to cancel the child.

```kotlin
try {
    fetchVideoSource(mediaId)
} catch (e: CancellationException) {
    throw e  // must rethrow
} catch (e: Exception) {
    _uiState.value = errorState(e.message)
}
```

### runCatching and CancellationException

`runCatching` catches `CancellationException` and returns it as `Result.failure`. This is problematic in coroutine contexts. The current codebase avoids `runCatching` in ViewModels (using `.fold()` on `Result<T>` instead), which is the correct pattern.

```kotlin
// Good - fold does not swallow CancellationException
fetchVideoSource(mediaId).fold(
    onSuccess = { startPlayback(it) },
    onFailure = { showError(it.message) },
)

// Bad - runCatching swallows CancellationException
runCatching { fetchVideoSource(mediaId) }
```

If you must use a try/catch pattern, prefer explicit try-catch-with-rethrow or `.fold()` on `Result<T>` over `runCatching`. The project uses Kotlin stdlib `Result<T>` rather than external FP libraries for error handling, see `docs/codestyle.md` §7.

---

## 9. Testing

### Test dispatcher setup

Every ViewModel test class uses `UnconfinedTestDispatcher` with `Dispatchers.setMain` / `resetMain` in `@BeforeEach` / `@AfterEach`. This is the standard pattern across all ViewModel tests:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid credentials navigate to catalog`() = runTest(testDispatcher) {
        // ...
    }
}
```

Benefits of `UnconfinedTestDispatcher`:
- Coroutines launch eagerly without yielding
- No need for `advanceUntilIdle()` or `yield()` calls in most tests
- StateFlow values are available immediately after the action

### runTest

Always use `runTest(testDispatcher)` from `kotlinx-coroutines-test`. The dispatcher argument ensures all coroutines use the test dispatcher:

```kotlin
@Test
fun `loads catalog and emits content with rows`() = runTest(testDispatcher) {
    val viewModel = HomeViewModel(listCatalog = { ... })
    // state is immediately available
    assertThat(viewModel.uiState.value).isInstanceOf(...)
}
```

### Turbine for Flow assertions

Use `app.cash.turbine.test` for asserting on Flow emissions. This is already the standard for StateFlow testing in this codebase:

```kotlin
viewModel.uiState.test {
    assertThat(awaitItem()).isInstanceOf(HomeUiState.Content::class.java)
    cancelAndIgnoreRemainingEvents()
}
```

Key Turbine functions:
- `awaitItem()` -- wait for and return the next emission
- `awaitComplete()` -- wait for the Flow to complete
- `cancelAndIgnoreRemainingEvents()` -- clean up at the end of each test block
- `awaitError()` -- wait for and return an error

---

## 10. Triage Workflow

When a coroutine-related bug or misbehavior is reported, follow this order:

1. **Identify the scope.** Is the coroutine attached to `viewModelScope`, `lifecycleScope`, or an application scope? If the scope is already cancelled, the coroutine will not start or will be cancelled immediately.

2. **Check cancellation.** Is the coroutine being silently cancelled? Look for:
   - A parent scope cancelling (e.g., ViewModel cleared, lifecycle destroyed)
   - A job handle being cancelled before launch (check for cancel-before-start)
   - `CancellationException` being swallowed by `runCatching` or broad `catch(Exception)` blocks

3. **Verify the dispatcher.** Is the coroutine running on the expected dispatcher? If a heavy computation runs on `Dispatchers.Main`, it will block the UI. If a UI update runs on `Dispatchers.IO`, it may cause race conditions or lifecycle issues.

4. **Apply the smallest fix.** Match the convention:
   - Add cancel-before-start if a long-running job could overlap
   - Add `isActive` check if a loop lacks suspension points
   - Rethrow `CancellationException` if catching broadly
   - Use `collectAsStateWithLifecycle()` if collection is missing lifecycle awareness
   - Use `.update {}` if a concurrent write race is suspected

---

## 11. References

- `docs/codestyle.md` -- FC/IS architecture, ADTs, sealed types, and pure function rules
- [kotlinx.coroutines structured concurrency](https://kotlinlang.org/docs/coroutines-basics.html#structured-concurrency)
- [kotlinx.coroutines testing guide](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-test/)
- [Turbine README](https://github.com/cashapp/turbine) -- Flow testing assertions
