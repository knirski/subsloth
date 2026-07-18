# Navigation3 Deep-Dive

The project uses `androidx.navigation3` (1.1.x) for navigation across **all three platforms**: Android, Desktop (JVM), and Web (WasmJS). Navigation3 is a Compose-first, type-safe, serializable navigation framework that replaces Navigation Compose.

---

## Architecture

```
┌────────────────────────────────────────────────────────────────┐
│                      NavKey (sealed interface)                  │
│  Each route is a @Serializable data object or data class        │
└──────────────────────────────────────┬─────────────────────────┘
                                       │
┌──────────────────────────────────────▼─────────────────────────┐
│                     rememberNavBackStack()                      │
│  Typed MutableList<NavKey> — source of truth for current stack  │
└──────────────────────────────────────┬─────────────────────────┘
                                       │
┌──────────────────────────────────────▼─────────────────────────┐
│                        NavDisplay()                              │
│  Composable that reads backStack and renders the matching entry  │
└──────────────────────────────────────┬─────────────────────────┘
                                       │
┌──────────────────────────────────────▼─────────────────────────┐
│                     entryProvider { ... }                        │
│  Maps each NavKey subtype to its corresponding Compose screen    │
└─────────────────────────────────────────────────────────────────┘
```

---

## NavKey Hierarchy

All navigation keys extend `AppNavKey` (a sealed interface implementing `NavKey`), defined in `:core:ui`:

```kotlin
@Serializable
sealed interface AppNavKey : NavKey

@Serializable
data object LoginKey : AppNavKey

@Serializable
data class MovieDetailKey(val movieId: String) : AppNavKey

@Serializable
data class PlayerKey(val contentId: String, val contentType: String) : AppNavKey
```

**Pattern rules:**
- **Singleton routes** (no arguments) → `data object` (e.g. `LoginKey`, `CatalogKey`)
- **Parameterised routes** → `data class` with a primitive/String parameter (e.g. `MovieDetailKey(val movieId: String)`)
- **All keys** must be `@Serializable` — required for back-stack persistence across process death
- **All keys** live in `:core:ui` so every platform module can reference them without circular dependencies

---

## SavedStateConfiguration

Every `NavKey` subtype must be registered in `subslothNavConfig` (defined in `:core:ui`) so the back stack survives process death:

```kotlin
val subslothNavConfig: SavedStateConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule {
        polymorphic(NavKey::class) {
            subclass(LoginKey::class, LoginKey.serializer())
            subclass(CatalogKey::class, CatalogKey.serializer())
            subclass(MovieDetailKey::class, MovieDetailKey.serializer())
            subclass(ShowDetailKey::class, ShowDetailKey.serializer())
            subclass(PlayerKey::class, PlayerKey.serializer())
            subclass(LibraryKey::class, LibraryKey.serializer())
            subclass(DownloadsKey::class, DownloadsKey.serializer())
            subclass(SettingsKey::class, SettingsKey.serializer())
            subclass(DiagnosticsKey::class, DiagnosticsKey.serializer())
            subclass(AuthRepairKey::class, AuthRepairKey.serializer())
            subclass(OfflineLibraryKey::class, OfflineLibraryKey.serializer())
        }
    }
}
```

> **Rule:** Every time you add a new `AppNavKey` subtype, register it in `subslothNavConfig`. Missing a registration causes a runtime crash when `rememberNavBackStack` tries to restore the back stack.

---

## Platform NavHosts

Each platform has its own NavHost composable following the same pattern:

**Android** (`SubSlothNavHost.kt`):
```kotlin
fun SubSlothNavHost(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(LoginKey)  // no SavedStateConfiguration on Android

    NavDisplay(
        modifier = modifier,
        backStack = backStack,
        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<LoginKey> { LoginScreen(onLoginSuccess = { backStack += CatalogKey }) }
            entry<CatalogKey> { CatalogScreen(onMovieClick = { id -> backStack += MovieDetailKey(id) }) }
            // ... remaining routes
        },
    )
}
```

**Desktop** (`DesktopNavHost.kt`) and **Web** (`WebNavHost.kt`) follow the same pattern **except** they pass `subslothNavConfig` to `rememberNavBackStack`:
```kotlin
val backStack = rememberNavBackStack(subslothNavConfig, LoginKey)
```

This is because Android's `SavedStateHandle` provides automatic serialization for the back stack, while Desktop and Web targets require explicit `SavedStateConfiguration` registration. All three platforms share the same `entryProvider` structure.

### Key Patterns

| Aspect | Pattern |
|---|---|
| **Initial key** | Passed as argument to `rememberNavBackStack` — determines the start screen |
| **Navigating forward** | `backStack += DestinationKey(...)` — appends to the mutable list |
| **Navigating back** | `if (backStack.size > 1) backStack.removeLastOrNull()` — guard against popping the root |
| **State preservation** | `rememberSaveableStateHolderNavEntryDecorator()` preserves Composable state across back-stack operations |
| **Parameters** | Navigation3 uses the `@Serializable` data class properties — no string templates or URI encoding needed |
| **SavedStateConfiguration** | Required on Desktop/Web; optional on Android (SavedStateHandle does it automatically) |

### Platform-Specific Concerns

| Platform | Module | File | Key difference |
|---|---|---|---|
| **Android** | `:androidApp` | `SubSlothNavHost.kt` | `rememberNavBackStack(LoginKey)` without config; back press handled by system via `BackHandler` |
| **Desktop** | `:desktopApp` | `DesktopNavHost.kt` | `rememberNavBackStack(subslothNavConfig, LoginKey)`; `onBack` must be manually guarded |
| **Web (WasmJS)** | `:webApp` | `WebNavHost.kt` | `rememberNavBackStack(subslothNavConfig, LoginKey)`; `savedstate-compose` required for `SavedStateConfiguration` support |

---

## Adding a New Route

1. **Add a NavKey** in `:core:ui` — extend `AppNavKey` with `@Serializable`:
   ```kotlin
   @Serializable
   data object NewScreenKey : AppNavKey
   ```

2. **Register** in `subslothNavConfig`:
   ```kotlin
   subclass(NewScreenKey::class, NewScreenKey.serializer())
   ```

3. **Add an entry** to all three NavHosts (`SubSlothNavHost.kt`, `DesktopNavHost.kt`, `WebNavHost.kt`):
   ```kotlin
   entry<NewScreenKey> { NewScreen() }
   ```

4. **Navigate** from any screen:
   ```kotlin
   backStack += NewScreenKey
   ```

---

## ViewModel Scoping

Navigation3 integrates with `androidx.lifecycle.viewmodel.navigation3` for ViewModel scoping:

```kotlin
import androidx.lifecycle.viewmodel.navigation3.viewModel

entry<PlayerKey> {
    val vm: PlayerViewModel = viewModel(key = contentId) { ... }
    PlayerScreen(viewModel = vm)
}
```

The `viewModel()` function scopes the ViewModel to the navigation entry. The `key` parameter ensures a separate ViewModel instance per unique route parameter (e.g. per movie ID).

> **Note for Desktop/Web:** The `@Suppress("VIEWMAKER_INJECTION")` annotations in `DesktopNavHost.kt` and `WebNavHost.kt` are necessary because `viewModel(key = contentId)` derives the ViewModel key from composable state — a false positive from detekt. Do not remove these suppressions (see `docs/known-gaps.md` §5).

---

## Comparison with Navigation Compose

| Aspect | Navigation Compose (v2) | Navigation3 (v1.1) |
|---|---|---|
| Route definition | String templates: `"movie/{id}"` | `@Serializable data class` |
| Type safety | No — runtime string matching | Yes — compile-time type matching |
| Arguments | `NavArgs` / `navArgument {}` | Constructor parameters |
| Back stack | `NavController` (opaque) | `MutableList<NavKey>` (transparent) |
| Cross-platform | Android only | Android + Desktop + WasmJS (via KMP) |
| Saved state | `SavedStateHandle` + auto-restore | `SavedStateConfiguration` + manual registration |
