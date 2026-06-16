## Why

The login screen exists (`feature/auth/`) but it isn't a navigation
gate. Apps can land on Home/Catalog/Library without ever authenticating.
That's wrong for a streaming app — every screen depends on
`session.userId`. The fix is to put a `SessionGate` composable at the
top of each app's `NavHost` that shows the login screen when
`SessionPort.state.value is Anonymous` and the gated content when
`Authenticated`.

The gate consumes `SessionPort`, which is already in `:core:domain`
from #141. No new domain types are needed. What is needed is:

1. A `SessionGate` composable in `:core:ui` that observes the session
   StateFlow and conditionally renders the gated content. (One
   composable, used by all three apps.)
2. Each app (`androidApp`, `desktopApp`, `webApp`) moves its root
   `NavHost` inside the gate so the gate intercepts every navigation.
3. The existing `LoginViewModel` keeps its state machine but is
   rewired to use the new `SessionPort`-driven flow instead of the
   injected `hasStoredCredentials` lambda.

## What Changes

- **`:core:ui` — new `SessionGate` composable** (`:core:ui` module
  created from the existing `core/ui` source set if needed; see
  tasks.md). Takes `sessionPort: SessionPort` and
  `authenticated: @Composable () -> Unit` slot. Observes
  `sessionPort.state` and either calls `authenticated()` or renders
  the login composable. Exposes a test-friendly API.
- **`feature/auth` — `LoginViewModel` rewired** to use `SessionPort`
  instead of `hasStoredCredentials` / `validateCredentials` lambdas.
  The state machine is preserved; the data sources change.
- **App shells — wire `SessionGate`** in `androidApp`, `desktopApp`,
  `webApp` `MainActivity`/`App` composables. Existing `NavHost`
  composables become the `authenticated` slot of the gate.

## Capabilities

### Added Capabilities

- `session-gate`: the navigation root of every app observes the
  session state and routes to login or to gated content. The login
  screen is the only way into the app; the gate is the only way out.

## Impact

- Affected modules: `:core:ui` (new composable), `feature/auth` (VM
  rewired), `androidApp`, `desktopApp`, `webApp` (root wiring).
- Backward-compat: the existing `LoginViewModel` tests change but the
  state machine is preserved.
- Risk: medium. Three app shells change; the UI is verified by the
  screenshot suite.
