## Why

The current `ClockPort.now(): Instant` is correct but limited. ViewModels and tests that need "5 minutes ago" or "is the cache fresh?" workarounds use `kotlin.time.Clock.System.now()` directly inside the shell, which leaks platform time into domain logic. A richer `CurrentTimePort` is a small addition with high test value.

Separately, the project has no in-memory mock of the API. The Ktor `MockEngine` in `core:network` is used for unit tests but the apps (Android, Desktop, WASM) all require a real `httpClient` instance and a `BASE_URL`. For feature parity, CI, and the planned screenshot suite we need a deterministic in-memory mock that implements the same domain ports the production network does. Today the Android app would crash without a real backend, which blocks the screenshot tests in `androidApp/screenshotTest/`.

This change ships:
- `CurrentTimePort` with a single `now()` returning `Instant` (just the existing `ClockPort` renamed for clarity at the call site) and a new `millisNow(): Long` for second-precision timestamps.
- A `MockApi` in a new `:testing:mock-api` module that implements `CatalogPort`, `CatalogSyncPort`, `CatalogCachePort`, `LibraryPort`, `DownloadsPort`, `CredentialsPort`, and a new `SessionPort`. Deterministic seed data, in-memory state, support for `expireSession()` to simulate 401/403 on the next call.

## What Changes

- **`:core:domain` — rename `ClockPort` to `CurrentTimePort`** and add `millisNow(): Long`. The rename is a follow-up; the new method is the only new API.
- **`:testing:mock-api` (new) — `MockApi`** with seed catalog (10 movies, 5 shows, 20 episodes), `login()`/`expireSession()`/`invalidateSession()` lifecycle, a single `currentUser: User?` mutable state, and pure `Result`-returning port methods (no coroutines for in-memory access).
- All ports implemented in `MockApi` use the same in-memory backing so the screenshot suite can exercise "user has library items", "user has downloads", "session expired" scenarios deterministically.

## Capabilities

### Modified Capabilities

- `architecture`: requires the time abstraction to be richer (adds `millisNow`).
- `testing-release`: requires a mock API at the testing layer for the screenshot suite.

## Impact

- Affected modules: `:core:domain` (rename + new method), new `:testing:mock-api` module.
- Risk: low. The `CurrentTimePort` rename is a one-line diff in the Android shell. The mock is additive — production code paths are unchanged.
