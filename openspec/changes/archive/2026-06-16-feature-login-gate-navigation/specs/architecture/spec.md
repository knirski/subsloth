# Architecture Specification (delta)

## ADDED Requirements

### Requirement: Session Gate as Navigation Root
The root composable of every app (`androidApp`, `desktopApp`, `webApp`) SHALL wrap its content in a `SessionGate` composable that observes `SessionPort.state`. When the state is `Session.Anonymous`, the gate SHALL render the login screen. When the state is `Session.Authenticated`, the gate SHALL render the gated content. The gate is the only entry point into the app; the user cannot reach a screen that requires authentication without first being authenticated.

#### Scenario: User opens the app for the first time
- **WHEN** the app starts and `SessionPort.state.value` is `Session.Anonymous`
- **THEN** the gate renders the login screen and no other screen is reachable

#### Scenario: User successfully logs in
- **WHEN** the user submits valid credentials and `SessionPort.open(credentials)` returns `Outcome.Success(Unit)`
- **THEN** `SessionPort.state` emits `Session.Authenticated` and the gate switches to render the gated content

#### Scenario: Network shell invalidates the session
- **WHEN** the network shell calls `SessionPort.invalidate()` (e.g. on a 401)
- **THEN** `SessionPort.state` emits `Session.Anonymous` and the gate switches to render the login screen

#### Scenario: User logs out
- **WHEN** the user triggers logout and `SessionPort.close()` is called
- **THEN** `SessionPort.state` emits `Session.Anonymous` and the gate switches to render the login screen
