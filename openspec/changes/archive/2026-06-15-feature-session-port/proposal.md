## Why

Authentication in subsloth today is implicit: the network shell attaches
the saved credentials from `CredentialsPort` to every request, and the
UI gates on `AuthError.SessionExpired` after a request fails. There is
no explicit "the user is logged in" state in the domain. The login
screen exists but it's reached via deep-link or backstack pop, not via
a navigation gate.

The cleanest FP model is a `SessionPort` that the network shell can
write to and the UI can read from. When the network shell sees a 401,
it calls `session.invalidate()`; the UI observes the change and routes
to the login screen. On successful login, the network shell calls
`session.open(credentials)`; the UI observes the change and routes to
home.

This change ships the `SessionPort` interface and an in-memory
`InMemorySessionState` implementation. The navigation-gate wiring per
app (Android, Desktop, WASM) is a follow-up change that consumes the
port; doing them all in one PR would make the change unreviewable.

## What Changes

- **`:core:domain` — add `SessionPort`** with `current(): Session`,
  `open(credentials: Credentials): Outcome<Unit>`, `close()`,
  `invalidate(): Outcome<Unit>`, and `state: StateFlow<Session>` for
  observation.
- **`:core:domain` — `Session` sealed type** with `Anonymous` and
  `Authenticated(userId, openedAtEpochSeconds, credentials)` variants.
- **`:core:domain` — `InMemorySessionState`** as a default
  implementation (production wires the real one from a platform shell).
- **`:core:domain` — `SessionStateTest`** covering: anonymous is the
  default; `open` transitions to Authenticated; `invalidate` returns
  to Anonymous; concurrent `open` calls are idempotent.

## Capabilities

### Added Capabilities

- `session-port`: defines a session abstraction that the network shell
  writes to and the UI reads from. Codifies the "login once, reuse
  until rejected" lifecycle.

## Impact

- Affected modules: `:core:domain` (new port + implementation + test).
  No other module changes; the port is consumed by the follow-up
  navigation-gate PR.
- Risk: low. The port is additive.
