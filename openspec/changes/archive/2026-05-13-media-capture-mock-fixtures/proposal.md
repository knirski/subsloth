## Why

Manual browser discovery has already produced useful Media traffic, but the evidence is currently ephemeral and hard to reuse. We need a repeatable way to turn those authenticated browser sessions into sanitized fixtures and fixture-derived local replay so future contract work can be reviewed, tested, and iterated without re-running the same live session.

## What Changes

- Add a browser-traffic capture and export workflow that saves sanitized request/response fixtures from authenticated Media discovery sessions.
- Split captured fixtures into native contract data and web-only discovery data so the native API surface stays isolated.
- Replay the sanitized fixtures locally through programmatic WireMock stubs derived from the same fixture set.
- Keep the workflow local-only and dev-only; do not commit raw traces, signed URLs, credentials, or other sensitive session artifacts.
- **BREAKING**: none for the production app runtime; this change only adds developer tooling and sanitized fixture assets.

## Capabilities

### New Capabilities

- `media-capture-mock-fixtures`: browser capture, sanitization, fixture export, and fixture-derived local replay for Media discovery sessions.

### Modified Capabilities

- None.

## Impact

- Adds developer tooling for capture/export and fixture-derived replay.
- Introduces new sanitized fixture assets for native contract endpoints and web-only discovery endpoints.
- May add a local-only mock server or replay entrypoint that consumes the committed fixtures.
- No production networking, UI, or runtime contract behavior should change.
