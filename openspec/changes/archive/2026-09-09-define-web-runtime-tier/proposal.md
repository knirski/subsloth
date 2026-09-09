## Why

The GitHub Pages deployment is a static site, while the current Wasm entrypoint
still creates a session gate and the shared Web credential implementation writes
credentials and an encryption key to `localStorage`. The Wasm network client is
also hard-wired to mock data, so the deployed site cannot honestly be presented
as an authenticated production client.

This change defines GitHub Pages as a safe, explicit stateless demo. It makes the
demo boundary visible to users and tests, prevents the demo build from asking
for or persisting service credentials, and records the conditions for a future
authenticated Web deployment without adding a backend or proxy now.

## What Changes

- Add an explicit Web runtime mode whose GitHub Pages production build is
  `Demo` and whose transport is always fixture-backed mock data.
- Bypass the login/session persistence flow in Demo mode and expose the demo
  catalog/navigation entrypoint directly.
- Show a persistent, accessible Demo Mode label explaining that no account or
  live Media data is used.
- Ensure Demo mode never constructs the Web `CredentialStore`, reads or writes
  credential keys in `localStorage`, or sends live API requests.
- Add meaningful Wasm browser coverage for mode labelling, mock-only startup,
  and the absence of credential persistence.
- Update deployment and readiness documentation to distinguish the Pages demo
  from a future authenticated Web tier.

## Non-goals

- No backend, reverse proxy, OAuth flow, or authenticated Web runtime.
- No change to Android or Desktop authentication.
- No claim that GitHub Pages supports production Web persistence or live API
  access.

## Capabilities

### Added Capabilities

- `web-runtime-tier`: explicit demo runtime behavior and promotion gates for a
  future authenticated Web deployment.

## Impact

The change affects the Wasm Web entrypoint, Web runtime configuration, the
Wasm browser-test source set, the Pages deployment documentation, and the
readiness evidence. It does not change the Media API or require a new hosted
service.
