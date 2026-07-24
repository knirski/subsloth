# Composition Roots

A composition root is the place where concrete adapters (network clients, databases,
preferences stores) are constructed and injected into feature ViewModels through domain port
constructor parameters. `:feature:*` modules never construct adapters themselves — see
`docs/module-structure.md` and the `architecture` spec's "Feature Adapter Isolation" requirement.

This doc names each platform's composition root and states plainly whether it wires real
adapters or falls back to a non-production default.

## Android — production composition root

`androidApp/src/main/java/net/subsloth/AppContainer.kt`'s `AppContainer` class is the reference
composition root for **session, credential, data/catalog, library, downloads, settings, and
player** adapters. Initialised once in `SubSlothApplication.onCreate` and exposed via the
`Application` instance so it survives configuration changes and Activity recreation, it
constructs:

- `dataStore` / `userPreferences` — DataStore-backed `UserPreferences`.
- `database` — the Room `SubSlothDatabase`, backing `cachedCatalogDao`, `favoriteDao`,
  `localLibraryRecordDao`, `downloadedMediaDao`/`downloadedSubtitleDao`/
  `offlineDisplayMetadataDao`, `accountPlaybackProgressDao`, `seasonQueueDao`, and the other
  profile-scoped library DAOs.
- `accountProfileStore` — an `AccountProfileStore` (`core/preferences`) reusing `dataStore`,
  deriving the non-reversible HMAC-SHA256 account profile key every session/library/settings
  adapter below uses to scope per-account data. No adapter derives a profile key any other way
  (in particular, none use a raw or partially-reversible fragment of the login).
- `sessionPort` — an `AndroidSessionState` (`androidApp/.../AndroidSessionState.kt`): a real,
  Keystore-backed `SessionPort` implementation. It persists credentials via
  `CredentialsStoreAdapter`/`CredentialStore` (Android Keystore-backed `EncryptedSharedPreferences`,
  API 26+), validates them using the Kodi-compatible startup request (`Api.listMovies`, not a
  dedicated auth-only probe) rather than accepting any non-blank pair, derives `Session.Authenticated.userId`
  via `accountProfileStore`, and performs cold-start recovery (`recover()`, invoked exactly once
  from `AppContainer`'s `init`) — attempting silent re-authentication from persisted credentials
  before falling back to the login screen, clearing credentials only on a genuine rejection (not
  on a transient network/timeout failure).
- `api` / `catalogRepository` — an authenticated (or, before login, unauthenticated) `Api`/
  `CatalogRepository` pair that rebuilds itself whenever `sessionPort`'s credentials change
  (login, logout, account switch), closing the superseded Ktor `HttpClient` on each rebuild.
- `libraryPortAdapter` (wrapping `favoriteDao`/`localLibraryRecordDao`/`sessionPort`),
  `downloadController` (wrapping `DownloadStorageManager`/`StorageProvider`/`ConnectivityChecker`
  and the download-related DAOs), and `seasonQueueController` — real, session-scoped `LibraryPort`/
  `DownloadsPort` adapters, previously built (in the archived `offline-downloads` change) but never
  constructed by any composition root until now.
- `clock` — a `kotlin.time.Clock` (not an adapter in the port/adapter sense, but listed here
  since it's still a container-owned dependency other constructed objects consume).

`MainActivity` builds `RootContainerViewModel` via a `ViewModelProvider.Factory` injecting
`container.sessionPort` (replacing the bare default factory that used to fall back to the
in-memory session). `SubSlothNavHost`'s authenticated back stack starts at `CatalogKey` (not the
dead `LoginKey` placeholder it used to), and every reachable nav entry — catalog, movie/show
detail, auth repair, library, downloads, settings, player — constructs its ViewModel from a real
`AppContainer` adapter rather than a no-op/default port binding.

**One narrow, known-and-documented gap remains.** `core/domain/.../PlaybackPort` (stream-source
resolution and playback control) has no implementation anywhere in the tree — `PlayerViewModel`'s
`fetchVideoSource`/`refreshStreamUrl` stay on their safe no-op defaults. Building a real adapter
means inventing Kodi-compatible stream-URL resolution and quality/DRM selection from scratch, which
is out of proportion for a session/runtime-wiring change and is left for a future change scoped to
the `playback` capability. Everything else `PlayerViewModel` needs (episode listing, playback
progress, playback speed/subtitle-language preferences, auth-failure handling) is wired to real
adapters.

## Desktop and Web — no composition root yet

`desktopApp/src/main/kotlin/net/subsloth/desktop/Main.kt` and
`webApp/src/wasmJsMain/kotlin/net/subsloth/web/Main.kt` both construct their root composable the
same way Android's session wiring used to, before this change: `val root: RootContainerViewModel =
viewModel()`. Neither platform has an `AppContainer`-equivalent class constructing real
network/database/preferences adapters at all — `:webApp`'s `commonMain.dependencies` block does
depend directly on `:core:network` (a platform app is expected to depend on concrete adapters;
that's what a composition root does), but nothing in `Main.kt` currently constructs or wires an
`Api`, database, or preferences instance into a ViewModel.

Building Desktop's real composition root — following the same "construct concrete adapters,
inject ports into feature ViewModels" pattern `AppContainer` already demonstrates for Android's
data layer — is `Change 3A` (`wire-desktop-production-runtime`)'s scope, not this change's.
`Change 3B` (`define-web-runtime-tier`) covers Web's production-connectivity decision record and
demo/production mode separation, which may or may not include a full `AppContainer`-equivalent
composition root depending on what that change decides — see that change's own scope, not this
doc, for the authoritative plan.

## The shared non-production default

`core/ui/src/commonMain/kotlin/net/subsloth/core/ui/RootContainerViewModel.kt`'s
`RootContainerViewModel` holds an in-memory `SessionPort` for the lifetime of its
`ViewModelStoreOwner` — it survives configuration changes (e.g. Activity recreation) but not
process death, since each `ViewModelStoreOwner` gets its own instance:

```kotlin
open class RootContainerViewModel(sessionPort: SessionPort? = null) : ViewModel() {
    val sessionPort: SessionPort = sessionPort ?: InMemorySessionState()
}
```

When constructed via the platform-default `viewModel()` factory with a `null` `sessionPort`
argument, this falls back to `InMemorySessionState()` —
`core/domain/src/commonMain/kotlin/net/subsloth/core/domain/port/InMemorySessionState.kt`'s own
doc comment states plainly: "Production wires a persistent-backed implementation; this is the
no-frills reference for tests, the screenshot suite, and the dev/demo build flavour." It accepts
any non-blank login/password pair and never persists session state across process restarts.
Desktop and Web still construct `RootContainerViewModel` this way today (falling back to this
default); Android no longer does — `MainActivity` now passes `container.sessionPort` (the real
`AndroidSessionState`) explicitly, so `InMemorySessionState` is unreachable from Android's
production startup path.

## Summary

| Platform | Data/catalog adapters | Session/auth adapter |
|---|---|---|
| Android | Real (`AppContainer`) | Real (`AndroidSessionState`, Change 2) |
| Desktop | None yet (Change 3A scope) | In-memory default (Change 3A scope) |
| Web | None yet (Change 3B scope) | In-memory default (Change 3B scope) |
