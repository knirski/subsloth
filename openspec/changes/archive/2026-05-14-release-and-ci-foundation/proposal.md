## Why

Required CI must be deterministic and offline-only, and it must protect every implementation change. Landing CI, secret scanning, release-please, and the no-comments / Kodi-parity invariant scaffolding right after the project foundation ensures every later change is verified consistently. Final-gate verification (UI tests, screenshots, benchmarks, device acceptance) stays in `verification-release`.

## What Changes

- Offline-only required CI workflow.
- Secret/artifact scanning.
- No-comments and Kodi-parity invariant checks.
- Local-only live drift policy.
- Release-please workflow with `release-type: simple`.
- `version.txt` and `CHANGELOG.md` at repository root.
- Debug-signed sideload APK naming.
- Sensitive-artifact exclusion.

## Capabilities

### New Capabilities

- `testing-release` (initial slice — final-gate verification requirements land later in `verification-release`).

### Modified Capabilities

- None.

## Impact

- Affects `.github/workflows/ci.yml`, `.github/workflows/release-please.yml`, `version.txt`, `CHANGELOG.md`, repository hygiene scripts.
- Depends on `foundation-api-contract` (project scaffold).
- Must land before any feature change so its CI guardrails protect them.
