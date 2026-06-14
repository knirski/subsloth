# Catalog Sync Design

## Purpose

Persist movie and show catalog summaries locally so the app can display content instantly on startup (without waiting for the network), support offline browsing, and provide a manual "Synchronize" button for on-demand refresh.

## Requirements

1. **Startup sync:** On cold start, if the local catalog cache is stale (older than 1 hour), fetch fresh data from the API in the background. Show cached data immediately if available.
2. **Manual sync:** A sync button in the home screen top app bar triggers a full re-fetch regardless of staleness.
3. **Graceful offline:** If the device is offline or the API fails, show cached data without error. Only surface errors if there is no cache at all.
4. **Global cache:** The catalog is account-agnostic — the same movies and shows exist for all users. No per-profile scoping.
5. **Join tables for genres/countries:** Enable future SQL-level filtering by genre or country.

## Scope

- List-level summaries only (from `listMovies()` / `listShows()`).
- Full details (qualities, subtitles, seasons, episodes) remain fetched on demand when the user opens a detail screen.
- No migration needed — the app has not been deployed yet; schema changes are direct replacements.

---

## Architecture

### Data Layer

#### New Entity: `CachedCatalogItemEntity`

Table: `cached_catalog`

| Column | Type | Notes |
|--------|------|-------|
| id | Long (auto PK) | |
| contentId | String | Unique constraint |
| contentType | String | "movie" or "show" |
| title | String | |
| plot | String? | |
| posterUrl | String? | |
| backdropUrl | String? | |
| year | Int? | |
| rating | Double? | |
| durationMinutes | Int? | |
| slug | String? | |
| imdbId | String? | |
| tmdbId | Int? | |
| status | String? | Show only: "ongoing", "ended", "upcoming" |
| updatedAtEpochSeconds | Long? | Movie: updated_at; Show: null |
| newestVideoEpochSeconds | Long? | Show only: newest_video |

#### New Entity: `CachedCatalogGenreEntity`

Table: `cached_catalog_genre`

| Column | Type | Notes |
|--------|------|-------|
| id | Long (auto PK) | |
| catalogItemId | Long | FK to cached_catalog.id |
| genre | String | |

Unique constraint: `[catalogItemId, genre]`

#### New Entity: `CachedCatalogCountryEntity`

Table: `cached_catalog_country`

| Column | Type | Notes |
|--------|------|-------|
| id | Long (auto PK) | |
| catalogItemId | Long | FK to cached_catalog.id |
| country | String | |

Unique constraint: `[catalogItemId, country]`

#### Remove: `CachedOnlineMetadataEntity`

Delete the existing `CachedOnlineMetadataEntity` and its DAO `CachedOnlineMetadataDao`. They are unused in feature code. Replace entirely with the new catalog cache tables.

#### New DAOs

**`CachedCatalogDao`:**
```kotlin
@Dao
interface CachedCatalogDao {
    @Transaction
    @Query("SELECT * FROM cached_catalog WHERE contentType = :contentType ORDER BY title ASC")
    fun getAllByType(contentType: String): Flow<List<CachedCatalogItemWithMetadata>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedCatalogItemEntity>)

    @Query("DELETE FROM cached_catalog")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM cached_catalog")
    suspend fun count(): Int

    /** Atomic replace: delete all + insert new. Wraps in a Room @Transaction. */
    @Transaction
    suspend fun replaceAll(items: List<CachedCatalogItemWithMetadata>) {
        deleteAll()
        deleteAllGenres()
        deleteAllCountries()
        upsertAll(items.map { it.item })
        upsertAllGenres(items.flatMap { item ->
            item.genres.map { genre -> genre.copy(catalogItemId = item.item.id) }
        })
        upsertAllCountries(items.flatMap { item ->
            item.countries.map { country -> country.copy(catalogItemId = item.item.id) }
        })
    }
}
```

**`CachedCatalogGenreDao`:**
```kotlin
@Dao
interface CachedCatalogGenreDao {
    @Query("SELECT * FROM cached_catalog_genre WHERE catalogItemId = :itemId")
    fun getByItemId(itemId: Long): Flow<List<CachedCatalogGenreEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<CachedCatalogGenreEntity>)

    @Query("DELETE FROM cached_catalog_genre")
    suspend fun deleteAll()

    @Query("SELECT DISTINCT catalogItemId FROM cached_catalog_genre WHERE genre = :genre")
    fun getItemIdsByGenre(genre: String): Flow<List<Long>>
}
```

**`CachedCatalogCountryDao`:**
Same pattern as genre.

**Room `@Relation` data class:**
```kotlin
data class CachedCatalogItemWithMetadata(
    @Embedded val item: CachedCatalogItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "catalogItemId",
    )
    val genres: List<CachedCatalogGenreEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "catalogItemId",
    )
    val countries: List<CachedCatalogCountryEntity>,
)
```

#### Schema Version

Bump from version 2 to version 3. No migration needed — drop and recreate.

### Domain Layer

#### New Port: `CatalogCachePort`

```kotlin
interface CatalogCachePort {
    fun catalogItems(contentType: String): Flow<List<Media>>
    suspend fun replaceCatalog(items: List<Media>)
    suspend fun clearCatalog()
}
```

**Note:** `replaceCatalog()` deletes all existing cache data and inserts new data atomically (Room `@Transaction`). This prevents empty-cache state on crash.

#### New Port: `CatalogSyncPort`

```kotlin
interface CatalogSyncPort {
    /** Fetches fresh catalog from API, updates cache, returns typed error on failure. */
    suspend fun sync(): Result<Unit>

    /** Whether the local cache is older than the staleness threshold. */
    suspend fun isStale(): Boolean
}
```

#### New Domain Error: `SyncError`

```kotlin
sealed interface SyncError : DomainError {
    data object NoConnectivity : SyncError
    data object Timeout : SyncError
    data class ServerError(val code: Int) : SyncError
    data object Unknown : SyncError
}
```

The repository maps I/O exceptions to `SyncError` subtypes. The ViewModel only emits `SyncError` via the error channel. The UI `when` is exhaustive over the four variants.

#### New Policy: `CatalogSyncPolicy`

```kotlin
object CatalogSyncPolicy {
    const val STALENESS_THRESHOLD_MS = 60 * 60 * 1000L // 1 hour

    fun isStale(lastSyncTimestamp: Long?, nowEpochMs: Long): Boolean =
        lastSyncTimestamp == null || (nowEpochMs - lastSyncTimestamp) > STALENESS_THRESHOLD_MS
}
```

#### Modify `CatalogPort`

No changes — `CatalogPort.listCatalog()` continues to call the API directly. The sync flow uses `CatalogSyncPort` + `CatalogCachePort` instead.

### Shell Layer

#### New Repository: `CatalogRepository`

Implements `CatalogCachePort` and `CatalogSyncPort`. Coordinates API, Room, and DataStore. Catches I/O exceptions and converts to `SyncError` subtypes at the boundary:

```kotlin
class CatalogRepository(
    private val api: Api,
    private val catalogDao: CachedCatalogDao,
    private val genreDao: CachedCatalogGenreDao,
    private val countryDao: CachedCatalogCountryDao,
    private val userPreferences: UserPreferences,
    private val clock: ClockPort,
) : CatalogCachePort, CatalogSyncPort {

    // CatalogCachePort — reads from Room
    override fun catalogItems(contentType: String): Flow<List<Media>> =
        catalogDao.getAllByType(contentType).map { items ->
            items.map { it.toDomainMedia() }
        }

    // CatalogSyncPort — fetch API, upsert Room, update DataStore timestamp
    override suspend fun sync(): Result<Unit> = try {
        val allMovies = paginate { page -> api.listMovies(page = page, perPage = 100) }
        val allShows = paginate { page -> api.listShows(page = page, perPage = 100) }
        // map DTOs → domain → CachedCatalogItemWithMetadata
        val items = mapToCacheEntities(allMovies, allShows)
        catalogDao.replaceAll(items)
        userPreferences.setGlobalCatalogCacheTimestamp(clock.nowEpochMs())
        Result.success(Unit)
    } catch (e: Exception) {
        val error = when (e) {
            is java.net.UnknownHostException -> SyncError.NoConnectivity
            is java.net.SocketTimeoutException -> SyncError.Timeout
            is java.io.IOException -> SyncError.Unknown
            else -> SyncError.Unknown
        }
        Result.failure(DomainResultException(error))
    }

    private suspend fun <T> paginate(fetch: suspend (Int) -> List<T>): List<T> {
        val result = mutableListOf<T>()
        var page = 1
        do {
            val batch = fetch(page)
            result.addAll(batch)
            page++
        } while (batch.isNotEmpty())
        return result
    }

    override suspend fun isStale(): Boolean {
        val timestamp = userPreferences.globalCatalogCacheTimestamp().first()
        return CatalogSyncPolicy.isStale(timestamp, clock.nowEpochMs())
    }
}
```

**Note:** The `sync()` implementation uses `try`/`catch` at the I/O boundary. Exceptions from paginated `api.listMovies()` / `api.listShows()` calls are caught and converted to `SyncError` subtypes via `DomainResultException`. The ViewModel only sees `SyncError` types — never Java exceptions. Pagination ensures all items are fetched regardless of catalog size.

#### Modify `UserPreferences`

Add a global (non-profile-scoped) timestamp key:

```kotlin
private val globalCatalogCacheTimestampKey = longPreferencesKey("global_catalog_cache_timestamp")

fun globalCatalogCacheTimestamp(): Flow<Long?> = dataStore.data.map { prefs ->
    prefs[globalCatalogCacheTimestampKey]
}

suspend fun setGlobalCatalogCacheTimestamp(timestamp: Long) {
    dataStore.edit { prefs ->
        prefs[globalCatalogCacheTimestampKey] = timestamp
    }
}
```

### Feature Layer

#### Modify `HomeViewModel`

Constructor changes — add new dependencies via ports. All dependencies are injectable lambdas for testability:

```kotlin
class HomeViewModel(
    private val listCatalog: suspend () -> Result<List<Media>> = { ... },
    private val getDetails: suspend (Media.MediaId) -> Result<MediaDetails> = { ... },
    private val listLibrary: suspend () -> Result<List<LibraryItem>> = { ... },
    private val listDownloads: suspend () -> Result<List<DownloadState>> = { ... },
    private val catalogItems: (String) -> Flow<List<Media>> = { flowOf(emptyList()) },
    private val syncCatalog: suspend () -> Result<Unit> = { Result.success(Unit) },
    private val isCatalogStale: suspend () -> Boolean = { true },
    private val isMetered: () -> Boolean = { false },
    private val now: () -> Instant = { ... },
    private val savedState: Map<String, String> = mapOf(...),
) : ViewModel() {
```

State changes:

```kotlin
@Stable
sealed interface HomeUiState {
    data object Loading : HomeUiState

    @Immutable
    data class Content(
        val rows: ImmutableList<HomeRow>,
        val selectedTab: HomeTab,
    ) : HomeUiState
}
```

Additional state:

```kotlin
private val _isSyncing = MutableStateFlow(false)
val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

private val _syncErrors = MutableSharedFlow<SyncError>(replay = 1)
val syncErrors: Flow<SyncError> = _syncErrors

private var syncJob: Job? = null
```

No `Success` event — successful sync is silent. The updated Flow content is the confirmation. Errors are typed `DomainError` subtypes for testability; user-facing strings are mapped at the UI boundary only.

Init changes — collect cached Flow first, then silently sync if stale. Only manual `sync()` emits errors:

```kotlin
init {
    viewModelScope.launch {
        catalogItems("movie").combine(catalogItems("show")) { movies, shows ->
            buildHomeContent(movies, shows)
        }.collect { content ->
            _uiState.value = content
        }
    }
    viewModelScope.launch {
        if (isCatalogStale()) {
            syncInternal(silent = true)
        }
    }
}
```

New sync functions — cancels any in-flight sync before starting a new one:

```kotlin
fun sync() {
    syncInternal(silent = false)
}

private fun syncInternal(silent: Boolean) {
    syncJob?.cancel()
    syncJob = viewModelScope.launch {
        _isSyncing.value = true
        try {
            syncCatalog()
                .onFailure { error ->
                    log.e(error) { "Sync failed" }
                    if (!silent) {
                        // syncCatalog wraps SyncError in DomainResultException
                        val syncError = (error as? DomainResultException)?.domainError as? SyncError
                            ?: SyncError.Unknown
                        _syncErrors.emit(syncError)
                    }
                }
        } finally {
            _isSyncing.value = false
        }
    }
}

fun retrySync() {
    viewModelScope.launch { sync() }
}
```

**`buildHomeContent` — pure function, testable without ViewModel:**

```kotlin
internal fun buildHomeContent(
    movies: List<Media>,
    shows: List<Media>,
): HomeUiState.Content {
    val movieItems = movies.filterIsInstance<MovieSummary>()
    val showItems = shows.filterIsInstance<ShowSummary>()

    val recencyRows = buildRecencyRows(movieItems, showItems)

    val rows = buildList {
        buildContinueWatchingItems(movies + shows).takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.ContinueWatching(it.toImmutableList())) }
        buildOfflineItems(movies + shows).takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.AvailableOffline(it.toImmutableList())) }
        addAll(recencyRows)
        movieItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Movies(it.toImmutableList())) }
        showItems.takeIf { it.isNotEmpty() }
            ?.let { add(HomeRow.Shows(it.toImmutableList())) }
    }.toImmutableList()

    return HomeUiState.Content(rows = rows, selectedTab = HomeTab.MOVIES)
}
```

Tests can call `buildHomeContent(movies, shows)` directly — no ViewModel, no coroutines, no dispatcher setup needed.

#### Modify `HomeScreen`

Add top app bar with sync button and snackbar with Retry on failure:

```kotlin
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier,
    onMovieClick: (Media.MediaId.Movie) -> Unit = {},
    onShowClick: (Media.MediaId.Show) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.syncErrors.collect { error ->
            val message = when (error) {
                is SyncError.NoConnectivity -> "No internet connection"
                is SyncError.Timeout -> "Request timed out"
                is SyncError.ServerError -> "Server error (${error.code})"
                is SyncError.Unknown -> "Sync failed"
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Retry",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.retrySync()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("SubSloth") },
                actions = {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(
                            onClick = { viewModel.sync() },
                            enabled = !isSyncing,
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = "Synchronize")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is HomeUiState.Loading -> { /* centered progress */ }
            is HomeUiState.Content -> CatalogContent(
                state = s,
                modifier = modifier.padding(padding),
                onMovieClick = onMovieClick,
                onShowClick = onShowClick,
            )
        }
    }
}
```

### Wiring

In `SubSlothNavHost` or the DI container, construct `HomeViewModel` with:
- `catalogItems = catalogRepository::catalogItems` (via `CatalogCachePort`)
- `syncCatalog = catalogRepository::sync` (via `CatalogSyncPort`)
- `isCatalogStale = catalogRepository::isStale` (via `CatalogSyncPort`)

---

## Edge Cases

### 1. Init race condition — empty cache flash

Two concurrent `viewModelScope.launch` blocks: one collects the Flow, one checks staleness and syncs. On first launch with an empty cache, the Flow collector emits `Content(empty rows)` before sync completes, causing a flash of empty content.

**Fix:** Use a single `launch` that collects the Flow first, then triggers sync. Or: start in `Loading` state until the first Flow emission arrives, then switch to `Content`.

### 2. Channel event loss

`Channel<SyncError>(Channel.BUFFERED)` drops events if no collector is active (screen in background). When the user returns, the error is lost.

**Fix:** Replace `Channel` with `MutableSharedFlow<SyncError>(replay = 1)`. The latest error is replayed when the collector reconnects.

### 3. Concurrent syncs

If the user taps Sync while a startup sync is in progress, both `syncInternal` calls run concurrently. The second may complete first, showing stale-then-fresh data.

**Fix:** Track a `syncJob: Job?`. Cancel any in-flight sync before starting a new one:

```kotlin
private var syncJob: Job? = null

private fun syncInternal(silent: Boolean) {
    syncJob?.cancel()
    syncJob = viewModelScope.launch {
        _isSyncing.value = true
        try { ... } finally { _isSyncing.value = false }
    }
}
```

### 4. Join table atomicity

`deleteAll()` + `upsertAll()` across `cached_catalog`, `cached_catalog_genre`, `cached_catalog_country` isn't atomic. A crash between delete and insert leaves an empty cache.

**Fix:** Wrap `replaceCatalog()` in a Room `@Transaction`:

```kotlin
@Transaction
suspend fun replaceCatalog(items: List<CachedCatalogItemWithMetadata>) {
    deleteAll()
    deleteAllGenres()
    deleteAllCountries()
    upsertAll(items.map { it.item })
    upsertAllGenres(items.flatMap { item -> item.genres.map { genre -> genre.copy(catalogItemId = 0) } })
    upsertAllCountries(items.flatMap { item -> item.countries.map { country -> country.copy(catalogItemId = 0) } })
}
```

### 5. Pagination — silent truncation

`listMovies(perPage = 1000)` silently drops movies beyond page 1. If the catalog has >1000 items, the cache is incomplete.

**Fix:** Paginate until `meta.totalPages` is exhausted:

```kotlin
override suspend fun sync(): Result<Unit> = try {
    val allMovies = paginate { page -> api.listMovies(page = page, perPage = 100) }
    val allShows = paginate { page -> api.listShows(page = page, perPage = 100) }
    // upsert...
    Result.success(Unit)
} catch (e: Exception) { ... }

private suspend fun <T> paginate(fetch: suspend (Int) -> List<T>): List<T> {
    val result = mutableListOf<T>()
    var page = 1
    do {
        val batch = fetch(page)
        result.addAll(batch)
        page++
    } while (batch.isNotEmpty())
    return result
}
```

### 6. `buildHomeContent` references ViewModel stubs

The extracted pure function calls `buildContinueWatchingItems()` and `buildOfflineItems()` which are private ViewModel stubs. These must be standalone pure functions or lambda parameters.

**Fix:** Make them standalone pure functions with default implementations (returning empty list):

```kotlin
internal fun buildContinueWatchingItems(catalog: List<Media>): List<Media> = emptyList()
internal fun buildOfflineItems(catalog: List<Media>): List<Media> = emptyList()
```

### 7. Silent startup sync failure — user has no way to know

If the startup sync fails silently and the cache is stale, the user sees old data with no indication. The only way to trigger a refresh is the manual Sync button.

**Acceptable:** This is by design — startup syncs are best-effort. The manual Sync button is the explicit refresh mechanism.

### 8. Configuration change (rotation) during sync

ViewModel survives configuration changes. `viewModelScope` is NOT cancelled on rotation — only on ViewModel clear. The sync continues uninterrupted. The new Activity re-collects `uiState`, `isSyncing`, and `syncErrors`. The `SharedFlow(replay = 1)` replays the latest error if one occurred during the transition.

**No fix needed.**

### 9. Process death during sync

The OS kills the process. ViewModel is destroyed, `syncJob` is lost. On recreation:
1. New ViewModel is created
2. Collects cached Flow → shows stale cached data
3. Checks staleness → triggers new sync if stale
4. `SharedFlow` starts empty (no replay from previous process)

**No fix needed** — correct behavior.

### 10. Network loss mid-sync

Connectivity drops during `api.listMovies()` or `api.listShows()`. The call throws `UnknownHostException` or `SocketTimeoutException`. Caught by the `try`/`catch` in `sync()`. The atomic `replaceAll()` was never called, so the cache stays in its previous state. No data corruption.

For manual sync: `SyncError.NoConnectivity` is emitted, snackbar shows.
For startup sync: error is logged silently.

**No fix needed.**

### 11. App backgrounded during sync

`viewModelScope` uses `Dispatchers.Main.immediate` and is NOT cancelled when the Activity goes to background — only when the ViewModel is cleared. The sync continues. If an error occurs while backgrounded, `SharedFlow(replay = 1)` buffers it. When the user returns, the error is replayed and the snackbar shows.

**No fix needed.**

---

## Files Changed

| File | Action | Module |
|------|--------|--------|
| `core/model/.../error/DomainError.kt` | Add `SyncError` sealed interface | core/model |
| `core/database/.../entity/LibraryEntities.kt` | Remove `CachedOnlineMetadataEntity`, add `CachedCatalogItemEntity`, `CachedCatalogGenreEntity`, `CachedCatalogCountryEntity`, `CachedCatalogItemWithMetadata` | core/database |
| `core/database/.../dao/LibraryDao.kt` | Remove `CachedOnlineMetadataDao`, add `CachedCatalogDao` (with `@Transaction replaceAll`), `CachedCatalogGenreDao`, `CachedCatalogCountryDao` | core/database |
| `core/database/.../SubSlothDatabase.kt` | Update entities list, bump version, update DAO accessors | core/database |
| `core/domain/.../port/CatalogCachePort.kt` | New file | core/domain |
| `core/domain/.../port/CatalogSyncPort.kt` | New file | core/domain |
| `core/domain/.../policy/CatalogSyncPolicy.kt` | New file | core/domain |
| `core/network/.../CatalogRepository.kt` | New file implementing `CatalogCachePort` + `CatalogSyncPort` | core/network |
| `core/preferences/.../UserPreferences.kt` | Add `globalCatalogCacheTimestamp` key | core/preferences |
| `feature/catalog/.../HomeViewModel.kt` | Add sync dependencies, Flow-based loading, sync action, error channel, extract `buildHomeContent` as pure function | feature/catalog |
| `feature/catalog/.../HomeScreen.kt` | Add TopAppBar with sync button, SnackbarHost for sync feedback | feature/catalog |
| `feature/catalog/src/jvmTest/.../HomeViewModelTest.kt` | Update tests for new constructor params | feature/catalog |

## Error Handling

- **Network failure during sync:** Log the error, keep showing cached data. Send `SyncError` subtype to channel. Snackbar shows with "Retry" action button, auto-dismisses after ~5 seconds.
- **Retry tapped:** ViewModel re-triggers `sync()` without a connectivity pre-check. The sync will fail with a typed `SyncError` if the device is still offline.
- **Empty catalog from API:** Replace cache with empty list. UI shows empty state. No error event (sync succeeded).
- **All-or-nothing failure:** If any API call fails (movies, shows, or pagination), the entire sync fails. The atomic `replaceAll()` transaction is never called, so the cache retains its previous state. No partial updates occur.
- **Automatic startup syncs:** Silent — no snackbar on failure or success. Errors are logged only.

## Testing

### `CatalogSyncPolicy` (pure, no dependencies)
- `isStale(null, now)` → true (never synced)
- `isStale(now - 30min, now)` → false (fresh)
- `isStale(now - 2h, now)` → true (stale)
- `isStale(now - 1h - 1ms, now)` → true (exactly at threshold)

### `buildHomeContent` (pure function, no ViewModel)
- Empty movies + empty shows → Content with empty rows
- Movies only → Movies row present, no Shows row
- Shows only → Shows row present, no Movies row
- Movies with `updatedAtEpochSeconds` → Recency "Recently Added" row
- Shows with `newestVideoEpochSeconds` → Recency "Shows with recent episodes" row
- Mixed movies + shows → both rows present

### `HomeViewModel` (coroutines + turbine)
- Init with stale cache → `syncInternal(silent = true)` called, no errors emitted
- Init with fresh cache → no sync triggered
- Manual `sync()` with failure → `syncErrors` emits `SyncError` subtype
- Manual `sync()` with success → `syncErrors` receives nothing, `isSyncing` returns to false
- `retrySync()` → calls `sync()`
- `isSyncing` true during sync, false after

### `CatalogRepository` (integration, mocked API + Room)
- `sync()` success → `replaceAll` called atomically, DataStore timestamp updated
- `sync()` with `UnknownHostException` → `Result.failure(DomainResultException(SyncError.NoConnectivity))`
- `sync()` with `SocketTimeoutException` → `Result.failure(DomainResultException(SyncError.Timeout))`
- `sync()` paginates until empty page → all items captured
- `catalogItems("movie")` → returns Flow from Room query
- `isStale()` with old timestamp → true
- `isStale()` with fresh timestamp → false

### Edge case tests
- **Concurrent syncs:** Start sync A, then start sync B. Assert sync A is cancelled, only B completes.
- **Channel replay:** Emit error, then subscribe. Assert error is replayed (SharedFlow replay = 1).
- **Empty cache + failed sync:** Init with empty cache, sync fails. Assert `Loading` or `Content(empty rows)`, no crash.
- **Pagination:** Mock API to return 3 pages of 100 items. Assert all 300 items are in the cache.
- **Atomic replace:** Crash simulation — assert cache is never in a half-deleted state (@Transaction).
- **Process death simulation:** Create ViewModel, start sync, clear ViewModel, create new ViewModel. Assert cached data is shown and sync is re-triggered if stale.
- **Network loss mid-sync:** Start sync, simulate network loss mid-call. Assert cache is not corrupted, error is emitted (manual) or logged (startup).
