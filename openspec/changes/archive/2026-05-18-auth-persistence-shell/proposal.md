## Why

Authentication, credential storage, account-scoped data, and shared offline data define the app's privacy boundary. This change isolates those decisions before catalog, playback, downloads, and settings depend on them.

## What Changes

- Add login and auth repair behavior.
- Add Android Keystore-backed credential storage and backup exclusion.
- Add non-reversible local account profile key derivation.
- Add Room/DataStore persistence separation for account-scoped and shared offline data.
- Add logout retention and optional cleanup scopes.
- Add app shell navigation, offline library entry from login, and credential-sensitive `FLAG_SECURE` policy.

## Capabilities

### New Capabilities

- `auth-security`: Authentication, credential protection, account profiles, persistence, logout cleanup, app shell, and sensitive-screen policy.

### Modified Capabilities

- None.

## Impact

- Affects `:app`, `:feature:auth`, `:core:database`, `:core:preferences`, Android manifest backup rules, Metro wiring, and auth/persistence tests.
- Depends on the core ports and typed error patterns from `core-domain-network`.
