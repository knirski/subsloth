## MODIFIED Requirements

### Requirement: Session Port
The project SHALL provide a `SessionPort` abstraction that exposes the current session state to the UI and lets the network shell signal login/logout/session-expiry events. The session is the single source of truth for "is the user logged in?"; the UI SHALL observe `state` and route to the login screen when the state transitions to `Anonymous`. `open`, `close`, and `invalidate` SHALL be `suspend` functions so an adapter can await a network validation call or encrypted-storage access before returning.

#### Scenario: Network shell opens a session
- **WHEN** a user successfully submits credentials to the network shell
- **THEN** the shell calls `sessionPort.open(credentials)` and the `SessionPort.state` StateFlow emits `Session.Authenticated(userId, openedAtEpochSeconds, credentials)`

#### Scenario: Network shell invalidates the session on 401
- **WHEN** the network shell receives a 401 from the upstream API
- **THEN** the shell calls `sessionPort.invalidate()` and the `SessionPort.state` StateFlow emits `Session.Anonymous`. The UI SHALL route to the login screen.

#### Scenario: UI observes the state
- **WHEN** the UI is in any authenticated-only screen
- **THEN** it collects `sessionPort.state` and re-routes to the login screen on every transition to `Session.Anonymous`

#### Scenario: Adapter awaits validation before returning
- **WHEN** a production `SessionPort` implementation validates credentials against a real network call
- **THEN** `open` suspends until the call completes and only then returns `Outcome.Success` or `Outcome.Failure`, never optimistically returning before validation finishes

### Requirement: Composition Root Documentation
Each platform's composition root — the class or function responsible for constructing concrete network, persistence, preferences, and platform adapters and injecting them into feature ViewModels — SHALL be documented, including which platforms currently lack a production composition root and what non-production default they fall back to.

#### Scenario: A developer looks for composition-root ownership
- **WHEN** a developer wants to know where Android, Desktop, or Web construct their real adapters
- **THEN** a checked-in doc names the responsible class per platform and states explicitly whether that platform's composition root is production-ready or falls back to a non-production default

#### Scenario: Android's session/auth wiring is documented as complete
- **WHEN** the composition-root doc describes Android
- **THEN** it states that Android's composition root constructs real session, credential, and authenticated-client adapters (no longer the in-memory default), while Desktop and Web remain documented as falling back to the non-production default until their own changes land

## ADDED Requirements

### Requirement: Authenticated Navigation Start Destination
The authenticated content rendered after a platform's session gate SHALL start its own navigation back stack at a real, non-placeholder destination. No navigation entry reachable from production authenticated navigation SHALL have an empty placeholder body.

#### Scenario: User is authenticated
- **WHEN** the session gate switches from the login slot to the authenticated content slot
- **THEN** the authenticated navigation host's initial back-stack entry renders real content (e.g. the catalog), not an empty or login-shaped entry

#### Scenario: A production nav entry is reached
- **WHEN** any navigation entry registered in a production app's `entryProvider` is navigated to
- **THEN** its body constructs a real ViewModel and screen rather than an empty placeholder comment

### Requirement: Composition Root Completeness
A platform's production composition root SHALL NOT construct a feature ViewModel using only its no-op/default port parameters when a real adapter for that port already exists and is reachable from the composition root.

#### Scenario: Production Android startup is inspected
- **WHEN** a test inspects the ViewModels constructed by Android's production navigation host
- **THEN** none of them is bound to `InMemorySessionState` or to a port parameter's no-op/empty-success default when a real adapter exists for that port
