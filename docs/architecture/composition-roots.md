# Composition Roots

A composition root is the place where concrete adapters (network clients, databases,
preferences stores) are constructed and injected into feature ViewModels through domain port
constructor parameters. `:feature:*` modules never construct adapters themselves — see
`docs/module-structure.md` and the `architecture` spec's "Feature Adapter Isolation" requirement.

This doc names each platform's composition root and states plainly whether it wires real
adapters or falls back to a non-production default.

## Android — partial production composition root

`androidApp/src/main/java/net/subsloth/AppContainer.kt`'s `AppContainer` class is the reference
composition root for **data and catalog** adapters. Initialised once in
`SubSlothApplication.onCreate` and exposed via the `Application` instance so it survives
configuration changes and Activity recreation, it constructs:

- `dataStore` / `userPreferences` — DataStore-backed `UserPreferences`.
- `database` / `cachedCatalogDao` — the Room `SubSlothDatabase`.
- `api` — a `ClientFactory`-built Ktor `Api` client (created without credentials; no BasicAuth
  is configured yet).
- `catalogRepository` — a `CatalogRepository` combining the API client, Room cache, and
  preferences.

`MainActivity` reads `container.userPreferences` from `SubSlothApplication` and passes it into
`LoginViewModel`, and a `HomeViewModelFactory` injects `catalogRepository` into `HomeViewModel` —
this part of the composition root is genuinely production-grade, real-adapter wiring.

**Session/auth is the exception.** `MainActivity` constructs its `RootContainerViewModel` with
the bare default factory (`val root: RootContainerViewModel = viewModel()`), which falls back to
`RootContainerViewModel`'s in-memory `SessionPort` default (see below) — identically to Desktop
and Web. `AppContainer` does not currently construct or inject a persistent-backed `SessionPort`
implementation anywhere. Wiring Android's production session/auth adapter is `Change 2`
(`wire-android-production-runtime`)'s scope, not this change's.

## Desktop and Web — no composition root yet

`desktopApp/src/main/kotlin/net/subsloth/desktop/Main.kt` and
`webApp/src/wasmJsMain/kotlin/net/subsloth/web/Main.kt` both construct their root composable the
same way as Android's session wiring: `val root: RootContainerViewModel = viewModel()`. Neither
platform has an `AppContainer`-equivalent class constructing real network/database/preferences
adapters at all — `:webApp`'s `commonMain.dependencies` block does depend directly on
`:core:network` (a platform app is expected to depend on concrete adapters; that's what a
composition root does), but nothing in `Main.kt` currently constructs or wires an `Api`,
database, or preferences instance into a ViewModel.

Building Desktop's and Web's real composition roots — following the same "construct concrete
adapters, inject ports into feature ViewModels" pattern `AppContainer` already demonstrates for
Android's data layer — is `Change 3A` (`wire-desktop-production-runtime`) and `Change 3B`
(`wire-web-production-runtime`)'s scope respectively, not this change's.

## The shared non-production default

`core/ui/src/commonMain/kotlin/net/subsloth/core/ui/RootContainerViewModel.kt`'s
`RootContainerViewModel` holds a process-wide `SessionPort` that survives configuration changes:

```kotlin
open class RootContainerViewModel(sessionPort: SessionPort? = null) : ViewModel() {
    val sessionPort: SessionPort = sessionPort ?: InMemorySessionState()
}
```

When constructed via the platform-default `viewModel()` factory (as all three platforms do
today), the `sessionPort` parameter is `null`, so every platform currently falls back to
`InMemorySessionState()` —
`core/domain/src/commonMain/kotlin/net/subsloth/core/domain/port/InMemorySessionState.kt`'s own
doc comment states plainly: "Production wires a persistent-backed implementation; this is the
no-frills reference for tests, the screenshot suite, and the dev/demo build flavour." It accepts
any non-blank login/password pair and never persists session state across process restarts.

## Summary

| Platform | Data/catalog adapters | Session/auth adapter |
|---|---|---|
| Android | Real (`AppContainer`) | In-memory default (Change 2 scope) |
| Desktop | None yet (Change 3A scope) | In-memory default (Change 3A scope) |
| Web | None yet (Change 3B scope) | In-memory default (Change 3B scope) |
