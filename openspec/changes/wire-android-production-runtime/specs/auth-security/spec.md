## MODIFIED Requirements

### Requirement: Account Profile Key Derivation
The active local account profile key SHALL be a non-reversible HMAC-SHA256 of the normalized login and app-local profile salt.

#### Scenario: Login is normalized
- **WHEN** a login value is trimmed, Unicode-normalized to NFC, and lowercased for email-style logins
- **THEN** the app derives the same profile key for the same account without storing the raw login/email as a Room identifier, DataStore key, download path component, diagnostic field, or log value

#### Scenario: The real Android session chain uses the HMAC derivation
- **WHEN** the Android session adapter opens or recovers a session
- **THEN** `Session.Authenticated.userId` is the HMAC-SHA256-derived profile key from the existing `AccountProfileStore`, not a directly-recoverable fragment of the raw login, and every profile-scoped Room table and DataStore key is scoped by that same derived value

### Requirement: Credential Storage Protection
Credentials SHALL be stored separately from preferences using Android Keystore-backed encryption compatible with API 26 and excluded from Android Auto Backup and device-to-device transfer.

#### Scenario: Logout clears credentials
- **WHEN** the user logs out
- **THEN** encrypted credential material, in-memory auth state, and the authenticated HTTP client are cleared without deleting non-transient local profile or shared offline data by default

#### Scenario: Credentials are stored via a real Android adapter
- **WHEN** the app runs on Android and the user submits valid credentials
- **THEN** `CredentialsPort` is backed by an `androidMain` implementation using `android.security.keystore`-generated key material, not a generic file-backed `KeyStore` or an in-memory default

### Requirement: Auth Failure Repair
The app SHALL clear cached auth state and route to the login screen when normal authenticated requests return `401` or an equivalent auth failure. A dedicated auth repair screen SHALL be reachable and SHALL NOT be a placeholder navigation entry, but reaching it is an explicit, user-initiated action rather than an automatic consequence of every auth failure — see the scenarios below for the currently-implemented, tested paths.

#### Scenario: Auth expires
- **WHEN** a normal request reports unauthorized
- **THEN** cached auth state is cleared, offline downloads/progress and local account data remain intact, the session transitions to `Anonymous`, and the session gate switches to the ordinary login screen (`LoginForm`)

#### Scenario: Auth repair screen is reachable
- **WHEN** the user explicitly retries authentication (e.g. via the login screen's repair path, or a screen-level "sign in again" action taken before the session gate has switched away)
- **THEN** a real screen (not a placeholder navigation entry) offers retry-login and dismiss actions backed by the existing repair state machine

#### Scenario: Known rough edge — player-initiated repair navigation can race the automatic session switch
- **WHEN** a playback auth failure both invalidates the session (routing to the ordinary login screen per the "Auth expires" scenario) and offers a screen-level "sign in again" button that separately navigates to the dedicated repair screen
- **THEN** the button's navigation can race against the session gate already switching away from the screen that button lives on; this is a known, documented gap for a future change to resolve (e.g. by having the player-driven path route directly to auth repair instead of triggering both reactions independently), not a claim that it is already unified today

## ADDED Requirements

### Requirement: Authenticated Client Lifecycle
The Android app SHALL build authenticated HTTP clients only after credential recovery or a successful login, and SHALL rebuild or invalidate authenticated clients when credentials change.

#### Scenario: Client is unauthenticated before login
- **WHEN** the app starts with no recovered session
- **THEN** no authenticated HTTP client is constructed and no request carries credentials

#### Scenario: Client is rebuilt after login
- **WHEN** the session transitions from `Anonymous` to `Authenticated`
- **THEN** the composition root rebuilds the HTTP client (and any repository wrapping it) using the newly opened credentials

#### Scenario: Client is torn down on logout or invalidation
- **WHEN** the session transitions to `Anonymous` via logout or invalidation
- **THEN** the composition root discards the authenticated client and reverts to an unauthenticated one

### Requirement: Cold Start Session Recovery
On Android app launch, the session adapter SHALL attempt to recover a persisted encrypted session and validate it before deciding whether to show the login screen.

#### Scenario: Valid persisted credentials exist
- **WHEN** the app launches with previously saved encrypted credentials that still validate against the upstream service
- **THEN** the app enters the authenticated state without requiring the user to re-enter credentials

#### Scenario: Persisted credentials are rejected or expired
- **WHEN** the app launches with saved encrypted credentials that the upstream service no longer accepts
- **THEN** the app clears the stale credentials and shows the login screen (or, if at least one playable offline download exists, the existing Offline Library entry point) rather than hanging or silently retrying indefinitely

### Requirement: Account Switching
The Android app SHALL support logging in as a different account after logout without requiring an app restart, isolating account-scoped data per the existing Persistence Scope Separation requirement.

#### Scenario: A different account logs in after logout
- **WHEN** the user logs out and then logs in with a different account's credentials in the same app session
- **THEN** the newly derived account profile key scopes all account-scoped reads and writes, and no data from the previous account's profile key is visible
