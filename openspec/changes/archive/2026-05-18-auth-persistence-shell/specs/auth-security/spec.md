## ADDED Requirements

### Requirement: Login Gate
The app SHALL require login before authenticated catalog access when no encrypted credentials exist.

#### Scenario: No credentials exist
- **WHEN** the app starts without stored credentials
- **THEN** it shows the login screen instead of authenticated catalog content

#### Scenario: Credentials validate successfully
- **WHEN** the user submits valid Media credentials
- **THEN** the app stores them encrypted, derives the active local profile key, and navigates to catalog

### Requirement: Kodi Compatible Credential Validation
Credential validation SHALL reproduce the Kodi plugin's normal authenticated startup request sequence rather than adding a special auth-only probe.

#### Scenario: Login validation runs
- **WHEN** new or changed credentials are submitted
- **THEN** validation uses the Kodi-compatible startup sequence and request identity

### Requirement: Credential Storage Protection
Credentials SHALL be stored separately from preferences using Android Keystore-backed encryption compatible with API 26 and excluded from Android Auto Backup and device-to-device transfer.

#### Scenario: Logout clears credentials
- **WHEN** the user logs out
- **THEN** encrypted credential material and in-memory auth state are cleared without deleting non-transient local profile or shared offline data by default

### Requirement: Account Profile Key Derivation
The active local account profile key SHALL be a non-reversible HMAC-SHA256 of the normalized login and app-local profile salt.

#### Scenario: Login is normalized
- **WHEN** a login value is trimmed, Unicode-normalized to NFC, and lowercased for email-style logins
- **THEN** the app derives the same profile key for the same account without storing the raw login/email as a Room identifier, DataStore key, download path component, diagnostic field, or log value

### Requirement: Persistence Scope Separation
The system SHALL separate account-scoped local data from shared offline device-local data.

#### Scenario: Same account logs in again
- **WHEN** the same Media account logs back in after logout
- **THEN** the same local account profile key is derived, retained account-scoped local data becomes accessible again, and no automatic server-to-local state synchronization occurs unless proven Kodi plugin parity exists for each data type

#### Scenario: Different account logs in
- **WHEN** a different Media account logs in on the same device
- **THEN** preferences, favorites, watch later, watched state, subscriptions/server mirrors, account-scoped online progress, and online cached metadata are isolated by profile key while shared offline downloads and shared offline progress remain visible

### Requirement: Logout Cleanup Scopes
Logout cleanup choices SHALL be independent, local-only, and SHALL NOT mutate Media server-side library, progress, favorites, watch-later, watched-state, or subscription state.

#### Scenario: Delete downloads cleanup is selected
- **WHEN** the user chooses to delete downloaded videos/subtitles during logout
- **THEN** shared offline media, partial files, shared offline display metadata, and shared offline playback progress are deleted while account-scoped preferences and watch/library data remain

#### Scenario: Reset preferences cleanup is selected
- **WHEN** the user chooses to reset preferences during logout
- **THEN** only active-profile DataStore-backed preferences are cleared

#### Scenario: Clear watch/library cleanup is selected
- **WHEN** the user chooses to clear watch/library data during logout
- **THEN** only active-profile favorites, watch later, watched state, subscriptions/server mirrors, account-scoped progress, cached catalog/detail metadata, and local-only library records are cleared

### Requirement: Auth Failure Repair
The app SHALL route to auth repair when normal authenticated requests return `401` or an equivalent auth failure.

#### Scenario: Auth expires
- **WHEN** a normal request reports unauthorized
- **THEN** cached auth state is cleared, offline downloads/progress and local account data remain intact, and the user is routed to repair login

### Requirement: Offline Library From Login
The logged-out login screen SHALL show an Offline Library entry only when at least one playable shared offline download exists.

#### Scenario: Playable download exists
- **WHEN** the user is logged out and at least one verified playable shared download exists
- **THEN** the login screen offers Offline Library without sending Media requests or validating credentials

#### Scenario: No playable downloads exist
- **WHEN** the user is logged out and no playable shared downloads exist
- **THEN** the login screen does not show Offline Library

### Requirement: Login Input Safety
The login screen SHALL support standard Android Autofill/password-manager behavior and SHALL NOT inspect or manipulate clipboard contents.

#### Scenario: User enters password
- **WHEN** the user types or autofills a password
- **THEN** the app uses secure text entry and never logs login text, password text, Autofill payloads, IME suggestions, clipboard contents, or validation request bodies

### Requirement: Sensitive Screen Policy
The app SHALL apply Android `FLAG_SECURE` only while login, auth repair, diagnostics, or logout cleanup confirmation screens are visible.

#### Scenario: Non-sensitive screen is visible
- **WHEN** catalog, details, library, general settings, or playback is visible
- **THEN** `FLAG_SECURE` is not applied by v1 policy

### Requirement: Recoverable Verification Challenge State
The app SHALL treat redirects, HTML bodies, and non-JSON API responses as typed recoverable service states rather than opening WebView verification flows.

#### Scenario: API returns HTML
- **WHEN** a Kodi API request returns HTML or a redirect
- **THEN** the app shows a recoverable state and keeps offline downloads usable without scraping, browser automation, or cookie import
