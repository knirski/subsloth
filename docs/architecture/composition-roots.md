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

**Playback is wired.** `AppContainer.playbackPort` exposes an `ApiPlaybackPort`
(`core/network/.../playback/ApiPlaybackPort.kt`), rebuilt together with the session-scoped
`Api`/`CatalogRepository` pair on every session change. It resolves stream sources for movies,
episodes, and shows (a show resolves to its first available episode, re-fetched via
`Api.getEpisode` to obtain the signed URL), mapping the signed top-level HLS URL (or the item's
`qualities` variants, selected via `QualityPolicy`) into `VideoSource` with subtitle tracks and
duration. `SubSlothNavHost`'s player entry passes `prepareSource`/`refreshStreamUrl` as
`PlayerViewModel`'s `fetchVideoSource`/`refreshStreamUrl` lambdas, so HTTP 401s during stream
resolution now route through `PlaybackErrorClassifier` into the auth-repair flow. Renderer-level
transport control (play/pause/seek) remains owned by the platform player bridge in `:core:media`;
the port's `play`/`pause`/`seek` methods are documented no-ops in `ApiPlaybackPort` until a
remote-control consumer exists. Offline playback is wired through the same port:
`currentPlaybackPort` wraps `ApiPlaybackPort` in `OfflineFirstPlaybackPort`
(`core/media/.../playback/OfflineFirstPlaybackPort.kt`), which resolves a verified
local download (`DownloadController.listOfflineAssets()` + file verification via
`OfflineAssetFiles`) into a `PlaybackMode.OFFLINE` `VideoSource` — no network
access, and `refreshStreamUrl` returns the offline source unchanged per
`StreamRefreshPolicy`. Media without a playable download resolves online exactly
as before.

## Desktop — real composition root (`DesktopContainer`)

`desktopApp/src/main/kotlin/net/subsloth/desktop/DesktopContainer.kt` is the desktop
composition root, mirroring `AppContainer`'s structure: DataStore-backed `UserPreferences`
(`~/.local/share/subsloth` on Linux/macOS, `%APPDATA%\subsloth` on Windows), the
machine-keyed AES-GCM `CredentialStore` JVM actual (`~/.subsloth`), `AccountProfileStore`,
a Room `SubSlothDatabase` (BundledSQLiteDriver, next to the preferences file), and a
`ValidatingSessionState` session port with cold-start `recover()`.

The session-scoped `Api`/`CatalogRepository`/`ApiPlaybackPort` trio is rebuilt on every
session change exactly like Android (the previous `Api` is closed after the swap).
`DesktopNavHost`'s entries — catalog, movie/show detail, player, library, settings,
diagnostics, auth repair, offline library — construct their ViewModels from
container-provided ports, reading `catalogRepository`/`playbackPort` live at call time so
rebuilds never leave a stale adapter captured. `Main.kt` gates the nav host on the real
session via `SessionGate`; login is no longer a nav route.

`ValidatingSessionState` is platform-neutral and lives in `:core:data`
(`core/data/.../session/ValidatingSessionState.kt`); androidApp keeps a
`typealias AndroidSessionState = ValidatingSessionState` so its call sites and instrumented
tests are unchanged. Known desktop omissions (deliberate):

- **API base URL** — desktop has no build-config field; a deliberately saved
  `UserPreferences.storedApiBaseUrl` value takes precedence, with the
  `SUBSLOTH_API_BASE_URL` environment variable as the fallback for an absent or
  blank preference (the same presence-aware precedence as Android's
  `BuildConfig.SUBSLOTH_API_BASE_URL`, resolved via `ApiBaseUrlPolicy`
  — see `DesktopContainer.resolveApiBaseUrl`/`apiBaseUrlFlow`).
- The platform-neutral helper subset of `AppContainer` (catalog lambdas, settings writers,
  playback-progress persistence) is mirrored into `DesktopContainer` rather than extracted
  into a shared runtime; consolidating the two containers is a future refactor.

**Downloads are wired on desktop.** `DesktopContainer.downloadController` mirrors
`AppContainer`'s adapter: `DownloadController` (now in `:core:media`'s commonMain, since it is
platform-neutral) runs with the JVM storage shell from `:core:media`'s `jvmMain` —
`DesktopDownloadStore` (implements the `DownloadFileStore` port under `<dataDir>/downloads`),
`DesktopStorageProvider` (`StoragePort` over the same directory's disk space), and
`DesktopConnectivityChecker` (`ConnectivityPort`). The desktop connectivity checker has no
portable metered-network API and reports the flat desktop model (online, unmetered) — the
user's Wi-Fi-only preference is the only transfer gate there (documented caveat on the class).
`seasonQueueController`, `listSeasonQueues`, `retryDownload`, and `deleteAllDownloads` mirror
`AppContainer`'s helpers; `DesktopNavHost`'s downloads, library, offline-library, and settings
entries consume them. `listProgress` on the downloads entry stays on its safe default for the
same reason as Android (the shared progress table has no `contentType` column).

**Byte transfers run on both desktop and Android.** `DownloadTransferCoordinator`
(`core/media/.../download/DownloadTransferCoordinator.kt`, in `:core:media`'s jvm-shared
source set) watches the `downloaded_media` table for QUEUED rows and streams each through
`DownloadTransferer` (Ktor → staged `.part` file → rename) with progress events on a
`SharedFlow`. Policy is enforced per transfer: a metered network defers the item as
PAUSED (NeedsWifi, Wi-Fi-only contract), the stream URL is re-resolved per transfer against
the session-scoped `Api` (progressive `download_url` only — `.m3u8` playlists are rejected),
and a pause/remove mid-transfer aborts the stream without overwriting the persisted status.
There is no ranged resume: aborted or failed transfers restart from scratch. Android's
`AppContainer` runs the watcher for the process lifetime and surfaces progress through
`DownloadForegroundService` (started on first transfer, stopped when idle);
`DesktopContainer` runs the same watcher and logs events. Subtitle byte-transfer is not
wired yet — subtitle rows stay metadata-only.

## Web — demo tier and production composition root

Web runs one of two tiers, selected at startup by `createWebApp()` from the build-injected
API base URL (`SUBSLOTH_API_BASE_URL`, carried through `webApp/webpack.config.d/base-url.js`
into a runtime global and read via `WebBaseUrl.kt`):

- **Demo** (default; GitHub Pages): fixture-backed `WebDemoRuntime`
  (`ClientConfig.useMock = true`), defined by the `define-web-runtime-tier` decision record;
  every screen is wired through demo lambdas, and production-only members are safe no-ops.
  A demo banner is always rendered.
- **Production** (dispatch-gated CI build with the repo secret): `WebProductionContainer`
  wires the real wasmJs adapters — localStorage DataStore preferences, the browser
  credential store, the OPFS Room database (sqlite-wasm worker), and a
  `ValidatingSessionState` session port. The session-scoped
  `Api`/`CatalogRepository`/`ApiPlaybackPort` trio rebuilds on session change; playback
  runs with `preferProgressiveDownload = true` (browsers cannot play HLS natively, so the
  progressive `download_url` mp4 variant is preferred). `Main.kt` gates the nav host on
  `SessionGate` (login first), and the full nav graph — including the previously missing
  auth-repair entry — consumes a shared `WebRuntime` interface.

Download **byte transfer** remains jvm-only (no browser equivalent of the staged-file
worker): the web downloads port lists persisted state (empty until transfer lands) and its
controls manage that state without moving bytes. Web production playback depends on the
media exposing a progressive `download_url`; HLS-only items are not playable in browsers
until hls.js support lands in the player bridge.

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
No production startup path falls back to it anymore: Android's `MainActivity`, desktop's
`Main.kt`, and web production's `Main.kt` all pass their platform composition root's real
`sessionPort` explicitly, so the in-memory default remains only for the screenshot suite,
tests, and the dev/demo build flavour (`WebDemoRuntime`'s demo session).

## Summary

| Platform | Data/catalog adapters | Session/auth adapter | Playback adapter |
|---|---|---|---|
| Android | Real (`AppContainer`) | Real (`AndroidSessionState`) | Real (`ApiPlaybackPort`) |
| Desktop | Real (`DesktopContainer`) | Real (`ValidatingSessionState`) | Real (`ApiPlaybackPort`) |
| Web (production) | Real (`WebProductionContainer`) | Real (`ValidatingSessionState`) | Real (`ApiPlaybackPort`, progressive) |
| Web (demo) | Fixture-backed (`WebDemoRuntime`) | In-memory (`InMemorySessionState`) | Demo mock (`WebDemoRuntime`) |
