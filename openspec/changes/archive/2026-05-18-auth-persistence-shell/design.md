## Context

The imported baseline requires login before authenticated catalog access, encrypted credentials at rest, account-scoped preferences/library state, shared device-local downloads/progress, and an Offline Library entry after logout when playable downloads exist.

## Goals / Non-Goals

Goals:

- Store credentials separately from preferences and local profile data.
- Keep raw login/email out of Room IDs, DataStore keys, download paths, diagnostics, and logs.
- Preserve non-transient local data on logout by default.
- Make each optional logout cleanup local-only and scoped.

Non-goals:

- Do not implement catalog, details, playback, or download UI beyond stub navigation destinations needed by login/offline entry.
- Do not call Media server mutation endpoints during logout cleanup.
- Do not implement custom clipboard handling or WebView verification flows.

## Decisions

- Derive account profile keys using `HMAC-SHA256(appLocalProfileSalt, normalizedLogin)` because account data needs stable local isolation without storing raw login identifiers.
- Keep shared offline downloads and shared offline progress outside account profiles because downloaded media is device-local and visible across accounts/logged-out Offline Library.
- Apply `FLAG_SECURE` only to credential-sensitive screens because global secure mode would unnecessarily block screenshots for non-sensitive browsing, library, settings, and playback.
- Store credentials with Android Keystore-backed encryption compatible with API 26, with direct platform Keystore preferred over deprecated encrypted-preferences APIs.

## Risks / Trade-offs

- Account-scoped and shared offline stores are easy to mix accidentally -> DAO and repository tests must verify scope boundaries.
- Credential storage requires instrumented verification -> unit tests cover boundaries, emulator/device tests verify Android Keystore behavior.
- Logout cleanup UX can become ambiguous -> each cleanup choice must state exactly what local data it deletes and what it retains.

## Migration Plan

1. Add persistence and credential tests first.
2. Add Room schema for account-scoped and shared offline tables.
3. Add profile key derivation and DataStore preferences.
4. Add Keystore-backed credential store and backup exclusion resources.
5. Add app shell, navigation, login, auth repair, and offline library entry behavior.

## Open Questions

- Exact credential validation startup sequence depends on Kodi-compatible discovery from `foundation-api-contract`.
