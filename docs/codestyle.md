# SubSloth Codestyle

Architectural and coding conventions for this repository. Follow these patterns unless the active OpenSpec change explicitly overrides them.

---

## 1. FC/IS (Functional Core / Imperative Shell)

Separate pure transformations (core) from I/O and orchestration (shell).

| Layer | Side effects | Deterministic? | Examples |
|---|---|---|---|
| **Core** | none | yes | `parseHarJson()`, `categorizeEntries()`, `sanitizeBody()`, `buildMapping()` |
| **Shell** | file/network I/O, stdout, `println` | no | `export()`, `regenerateAll()`, CLI `main()` |

**Rule:** If calling a function twice with the same args always returns the same result, it belongs in the core. If it reads a file, writes a file, prints to stdout, or calls an external process, it belongs in the shell.

**Exceptions:** Print-line debugging during development is acceptable. Isolate performance-critical I/O behind an interface so tests can substitute a pure version.

## 2. Sealed Types & ADTs (Algebraic Data Types)

Model domain variants as `sealed interface` with `data object` or `data class` branches. The compiler enforces exhaustiveness at every `when` site.

```kotlin
sealed interface LoadState {
    data object Loading : LoadState
    data class Success(val data: String) : LoadState
    data class Error(val message: String) : LoadState
}
```

| Do | Don't |
|---|---|
| `data object` for singleton variants | `object` with manual `equals`/`hashCode` |
| `data class` for value-carrying variants | `class` where `data class` suffices |
| `val` properties on the sealed interface | Properties only on concrete variants |
| Exhaustive `when` inside the sealed type | `when` blocks scattered across consumers |
| ADTs in a dedicated module | ADTs interleaved with I/O code |
| Single file per sealed type | Variants split across files |

**`sealed class` vs `sealed interface`:** Prefer `sealed interface` when variants carry no shared state. Use `sealed class` when all variants share constructor parameters or you need to prevent external implementations entirely.

---

## 3. Exhaustive Pattern Matching

Every `when` on a sealed type **must** cover all variants — the compiler enforces this.

### Three-site rule

When adding a new variant, update these three sites:

| Site | What to do | Example |
|---|---|---|
| 1. Variant declaration | Add `data object` implementing the sealed interface | `data object Error : LoadState` |
| 2. Classifier | Add branch in every exhaustive `when` | `is Error -> handleError(message)` |
| 3. Parser / factory | Add branch in `parse()` or similar constructor | `path.startsWith("/error") -> Error` |

**Prefer `when` over `if`-`else` chains.** `when` is more readable and the compiler can check exhaustiveness for sealed types.

**Note:** When the variant set is modelled as an `enum` instead of a sealed type, the compiler auto-generates `entries` and guarantees unique constant names — no manual `all` list needed.

### Destructuring in `when`

Use destructuring to extract fields from compound values in `when` branches:

```kotlin
entries.groupBy(
    keySelector = { (ep, _) -> ep },
    valueTransform = { (_, entry) -> entry },
)
```

---

## 4. Pure Functions

- **Input** comes from parameters, not global state or env vars.
- **Output** goes to the return value, not stdout, files, or mutable statics.
- **No mutable shared state** — use `val` and immutable collections by default.
- **Side effects are infectious** — isolate them at the outermost layer.

✅ Good:
```kotlin
fun redactFields(element: JsonElement, fields: List<String>): JsonElement {
    val fieldSet = fields.map { it.lowercase() }.toSet()
    fun walk(e: JsonElement): JsonElement = when (e) {
        is JsonObject -> JsonObject(
            e.entries.associate { (k, v) ->
                if (k.lowercase() in fieldSet) k to JsonPrimitive("[REDACTED]")
                else k to walk(v)
            }
        )
        is JsonArray -> JsonArray(e.map(::walk))
        is JsonPrimitive -> e
    }
    return walk(element)
}
```

❌ Impure mixed with pure:
```kotlin
fun export(entries: List<HarEntry>): List<File> {
    println("Processing ${entries.size} entries")           // side effect in "pure" function
    val sanitized = entries.map { sanitize(it) }
    // ...
}
```

---

## 5. Module Dependencies

Dependencies flow **inward** toward zero-dependency modules.

```
:testing:api-contract      ← ADTs, fixture JSONs, HAR processing, WireMock factory.
:core:network              ← Test-depends on :testing:api-contract for fixtures.
```

- `:testing:api-contract` has dependencies (WireMock, kotlinx-serialization) for the HAR processing and stub factory, but those are kept in the test classpath of consumers.

---

## 6. Naming Conventions

| Construct | Convention | Example |
|---|---|---|
| Sealed interface/class / enum | UpperCamelCase noun | `Endpoint`, `LoadState` |
| Data object (singleton) | UpperCamelCase noun | `Movies`, `Speedtests` |
| Data class (value) | UpperCamelCase noun | `HarEntry`, `SanitizationRules` |
| Pure function | lowerCamelCase verb | `parseHarJson()`, `categorizeEntries()` |
| Imperative shell function | lowerCamelCase verb | `export()`, `create()` |
| Property on ADT / enum | lowerCamelCase noun | `fixtureName`, `urlPattern` |
| Gradle task | lowerCamelCase | `exportFixtures` |

---

## 7. Error Handling

### Three-tier strategy

| Scenario | Mechanism | Example |
|---|---|---|
| Expected absence | `null` return | `Endpoint.parse(url): Endpoint?` |
| Programmer mistake | `?: error("descriptive message")` / `requireNotNull()` | `requireField("version") ?: error("Missing version")` |
| Recoverable failure in pure code | `Result<T>` | `fun loadSanitizationRules(json: String): Result<SanitizationRules>` |

**Keep `throw` for programmer mistakes** (precondition violations, config errors) that should crash fast. Do not use exceptions for expected runtime failures.

**Keep error handling in the imperative shell.** Pure functions may throw for invalid input but should not catch. Let exceptions propagate to the outermost I/O layer.

**`Result<T>` rules:**
- Use in the functional core when callers should handle both success and failure paths.
- Convert exceptions to `Result.failure(e)` at the I/O boundary.
- **Do not use `Result` in the imperative shell** — `try`/`catch` around I/O is the right pattern there.

---

## 8. ViewModel State Management

Minimise mutable state in the imperative shell. Encode session-scoped state as fields of the immutable UI state data class, not as standalone `var` properties on the ViewModel.

### 8.1 Session flags belong in the state class, not in `var` fields

Boolean flags that track a session's lifecycle (e.g. whether a stream refresh or quality fallback has been used) must be fields of the sealed UI state data class, not standalone `var` properties.

✅ Correct:
```kotlin
sealed interface PlayerUiState {
    data object Loading : PlayerUiState

    @Immutable
    data class Content(
        val streamRefreshUsed: Boolean = false,
        val qualityFallbackUsed: Boolean = false,
        // ...
    ) : PlayerUiState
}
```

❌ Wrong — state drift risk:
```kotlin
class PlayerViewModel : ViewModel() {
    private var streamRefreshUsed = false
    private var qualityFallbackUsed = false
}
```

**Rationale:** Standalone `var` fields in ViewModels create two sources of truth — the `StateFlow` and the mutable field. They can drift apart when one is updated without the other. Embedding them in the state data class ensures every transition is atomic and observable.

### 8.2 One-shot caches use `Deferred`, not `var`

A value fetched once and cached for the lifetime of the screen must use `async(start = LAZY)` + `await()`.

✅ Correct:
```kotlin
private val catalogDeferred = viewModelScope.async(start = CoroutineStart.LAZY) {
    listCatalog().getOrDefault(emptyList())
}

// Usage:
val catalog = catalogDeferred.await()
```

❌ Wrong:
```kotlin
private var catalog: List<Media> = emptyList()
// ...assignments scattered across methods
```

### 8.3 Request cancellation uses `flatMapLatest`, not `var Job`

When a new input should cancel an in-flight operation, use `Channel` + `flatMapLatest`.

✅ Correct:
```kotlin
private val searchChannel = Channel<String>(Channel.CONFLATED)

init {
    viewModelScope.launch {
        searchChannel.consumeAsFlow()
            .flatMapLatest { query -> searchInternal(query) }
            .collect { state -> _uiState.value = state }
    }
}

fun search(query: String) {
    searchChannel.trySend(query)
}
```

❌ Wrong:
```kotlin
private var searchJob: Job? = null

fun search(query: String) {
    searchJob?.cancel()
    searchJob = viewModelScope.launch { ... }
}
```

### 8.4 Local collection construction uses immutable pipelines

When building a list of items inside a pure function, use `takeIf` + `?.let` + `listOfNotNull` or `buildList` rather than `mutableListOf` + `add`.

✅ Correct:
```kotlin
fun buildRows(): ImmutableList<HomeRow> {
    val movies = items.filterIsInstance<MovieSummary>()
    val shows = items.filterIsInstance<ShowSummary>()

    val moviesRow = movies.takeIf { it.isNotEmpty() }
        ?.let { HomeRow.Movies(it.toImmutableList()) }
    val showsRow = shows.takeIf { it.isNotEmpty() }
        ?.let { HomeRow.Shows(it.toImmutableList()) }

    return listOfNotNull(moviesRow, showsRow).toImmutableList()
}
```

❌ Wrong:
```kotlin
val rows = mutableListOf<HomeRow>()
if (movies.isNotEmpty()) rows.add(HomeRow.Movies(movies.toImmutableList()))
if (shows.isNotEmpty()) rows.add(HomeRow.Shows(shows.toImmutableList()))
return rows.toImmutableList()
```

### 8.5 Cardinality matching (sealed types over booleans)

Prefer sealed types over boolean combinations. Each boolean doubles the state space; most combinations are invalid. Sealed types make invalid states unrepresentable.

✅ Correct:
```kotlin
sealed interface LoadState {
    data object Loading : LoadState
    data class Success(val data: String) : LoadState
    data class Error(val message: String) : LoadState
}
```

❌ Wrong — 16 combinations, only 3 valid:
```kotlin
data class LoadState(
    val isLoading: Boolean,
    val isError: Boolean,
    val data: String?,
    val errorMessage: String?,
)
```

### 8.6 Dead code

Remove unused declarations promptly. A type that is declared but never referenced adds maintenance cost, may confuse readers, and requires baseline suppressions.

---

## 9. Testing

- **Parameterised tests** for sealed type variants (`WebDiscoveryFixtureTest`, `MockMappingVerificationTest`).
- **Classpath scanning** for fixture discovery — adding a `.json` file auto-creates a test case.
- **Pure functions** tested with simple input/output assertions — no mocking needed.
- **Imperative shell** tested through integration tests with temp directories and known fixture content.

---

## 10. Modern Kotlin FP Techniques

### 11.1 Higher-order functions

Functions that accept or return lambdas. This is the core of Kotlin FP composition.

```kotlin
fun <T, R> List<T>.mapNotNull(transform: (T) -> R?): List<R>
fun <T, K, V> Iterable<T>.groupBy(
    keySelector: (T) -> K,
    valueTransform: (T) -> V,
): Map<K, List<V>>
```

Prefer passing function references (`::`) over lambdas when the function already exists:

```kotlin
// Good
entries.map(::sanitize)

// Acceptable when logic is one-off
entries.map { sanitize(it) }
```

### 11.2 Scope functions

| Function | Receiver | Return value | Use case |
|---|---|---|---|
| `let` | `it` | lambda result | Nullable transform: `?.let { transform(it) }` |
| `run` | `this` | lambda result | Scoped computation: `?: run { computeFallback() }` |
| `also` | `it` | receiver itself | Side-effect in chain: `.also { log(it) }` |
| `apply` | `this` | receiver itself | Object configuration: `File(dir).apply { mkdirs() }` |

### 11.3 Extension functions

Add behavior to existing types without inheritance or wrappers. Place in the same package or a dedicated `extensions` package.

```kotlin
fun String.applyUrlRewrites(rules: List<UrlRewriteRule.Compiled>): String {
    var result = this
    for (rule in rules) result = result.replace(rule.regex, rule.replacement)
    return result
}
```

Private extensions are fine for domain-specific parsing inside a single file:
```kotlin
private fun JsonElement.parseUrlPattern(): UrlRewriteRule { ... }
```

### 11.4 `by lazy` for deferred computation

Compute once on first access and cache the result. Use for memoization and initialisation that depends on properties not available at construction time.

```kotlin
val compiledUrlRules: List<UrlRewriteRule.Compiled> by lazy {
    urlPatterns.map { it.compile() }
}
```

### 11.5 Collection pipelines

Prefer declarative functions over imperative loops with mutable accumulators:

```kotlin
// ✅ Good — immutable pipeline
fun categorizeEntries(entries: List<HarEntry>): Map<Endpoint, List<HarEntry>> =
    entries.mapNotNull { entry ->
        Endpoint.parse(entry.url)?.let { ep -> ep to entry }
    }.groupBy(
        keySelector = { (ep, _) -> ep },
        valueTransform = { (_, entry) -> entry },
    )

// ❌ Avoid — mutable loop
fun categorizeEntries(entries: List<HarEntry>): Map<Endpoint, List<HarEntry>> {
    val result = mutableMapOf<Endpoint, MutableList<HarEntry>>()
    for (entry in entries) {
        val ep = Endpoint.parse(entry.url) ?: continue
        result.getOrPut(ep) { mutableListOf() }.add(entry)
    }
    return result
}
```

### 11.6 `data class copy()` for immutable updates

```kotlin
fun updatePriority(mapping: WireMockMapping, newPriority: Int) =
    mapping.copy(priority = newPriority)
```

### 11.7 Local functions

Define helpers inside the function that uses them to prevent namespace pollution:

```kotlin
fun loadSanitizationRules(json: String): Result<SanitizationRules> {
    fun requireField(name: String) =
        root[name] ?: error("Missing required field \"$name\"")
    // ...
}
```

### 11.8 `?.` safe access + `?:` Elvis

Null-safe chains are the idiomatic alternative to nested `if (x != null)`:

```kotlin
val entries = root["log"]?.jsonObject
    ?.get("entries")?.jsonArray
    ?: emptyList()
```

### 11.9 `require()` and `check()` for preconditions

```kotlin
fun process(entries: List<HarEntry>): List<HarEntry> {
    require(entries.isNotEmpty()) { "entries must not be empty" }
    // ...
}
```

- `require(condition) { message }` — throws `IllegalArgumentException` for invalid arguments.
- `check(condition) { message }` — throws `IllegalStateException` for invalid state.

---

> **Maintenance:** When introducing a new architectural pattern, add it to this doc and reference it from the relevant agent-specific docs in `docs/agent/`.
