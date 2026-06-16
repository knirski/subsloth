# Architecture Specification (delta)

## ADDED Requirements

### Requirement: Session Port
The project SHALL provide a `SessionPort` abstraction that exposes the current session state to the UI and lets the network shell signal login/logout/session-expiry events. The session is the single source of truth for "is the user logged in?"; the UI SHALL observe `state` and route to the login screen when the state transitions to `Anonymous`.

#### Scenario: Network shell opens a session
- **WHEN** a user successfully submits credentials to the network shell
- **THEN** the shell calls `sessionPort.open(credentials)` and the `SessionPort.state` StateFlow emits `Session.Authenticated(userId, openedAtEpochSeconds, credentials)`

#### Scenario: Network shell invalidates the session on 401
- **WHEN** the network shell receives a 401 from the upstream API
- **THEN** the shell calls `sessionPort.invalidate()` and the `SessionPort.state` StateFlow emits `Session.Anonymous`. The UI SHALL route to the login screen.

#### Scenario: UI observes the state
- **WHEN** the UI is in any authenticated-only screen
- **THEN** it collects `sessionPort.state` and re-routes to the login screen on every transition to `Session.Anonymous`
