# Capture Workflow — Agent Instructions

This document describes how AI agents should work with the Media fixture-capture and export pipeline in the `subsloth` repository.

## Overview

The pipeline captures Media API responses, sanitizes them, and exports committed fixture files that WireMock can serve via programmatic stubs. It is:

- **Local-only** — runs on developer workstations, never in CI.
- **Developer-controlled** — credentials must never enter the repository.
- **Split into two fixture buckets** — native-contract fixtures (movies, shows, details) and web-only discovery fixtures (comments, statistics, etc.).

## Two capture paths

### Native contract fixtures (`captureApi` Gradle task)

Talks directly to the Kodi-plugin REST API at `front.media-mirror.tv/api/v2/` using Basic auth.

```bash
nix develop --command ./gradlew :testing:api-contract:captureApi \
    -Pemail=you@example.com -Ppassword=your-password
```

This calls the five Kodi endpoints (`/movies`, `/shows`, `/movies/{id}`, `/shows/{id}`, `/episodes/{id}`), applies sanitisation rules from `scripts/capture/sanitization-rules.json`, and writes fixture JSON directly to:

- `testing/api-contract/src/main/resources/media/` (native contract)

The implementation lives in `testing/api-contract/src/main/kotlin/net/subsloth/testing/contract/CaptureApi.kt`.

### Web-discovery fixtures (`exportFixtures` Gradle task)

Web-frontend-only endpoints (comments, filtered catalog, statistics, etc.) are captured from browser HAR files.  Export HAR recordings from your browser's DevTools (Firefox or Chrome), then run:

```bash
nix develop --command ./gradlew :testing:api-contract:exportFixtures \
    -PharFiles=session.har,another.har
```

The export pipeline categorizes entries by URL pattern via `Endpoint.parse`, applies sanitisation rules, and writes fixtures to the web-discovery directory.

**Never commit raw HAR files.**  They are git-ignored (`*.har`, `*.har.gz`) and contain session cookies.  Process them through `exportFixtures` and delete the originals.

## Agent Rules

### 1. Never commit raw session artifacts

Raw HAR files, `.har.gz` archives, browser trace files, authenticated screenshots, and any file containing real credentials or session cookies **must never** be committed.

If you see a raw artifact in `git status`, raise it as a blocking issue.

### 2. Always sanitise before committing

Every fixture that enters the repository must pass through `captureApi` or `exportFixtures`.  Never copy a response body manually into a fixture.

### 3. `Endpoint` enum — quick reference

All known API endpoints are modelled in `testing/api-contract/src/main/kotlin/net/subsloth/testing/contract/Endpoint.kt`.

Each constant defines:
- `urlPattern` — WireMock URL matching pattern
- `category` — `Native` or `WebDiscovery`
- `kodiSource` — `true` for the five Kodi-plugin endpoints, `false` for browser-frontend-only endpoints
- `methods` / `responseStatus` / `responseKind` — replay metadata used to register the programmatic WireMock stubs from fixtures

**Adding a new endpoint** is a single-site change: declare a new enum constant and add a matching branch to `Endpoint.parse`.

### 4. Security rules

| Category | Must be redacted |
|---|---|
| Credentials | `password`, `auth_token`, `access_token`, `refresh_token`, `api_key`, `session_id` |
| Auth headers | `authorization`, `bearer`, `cookie`, `set-cookie` |
| PII | `email`, `phone`, `first_name`, `last_name`, `address`, `ip_address`, `geolocation`, `device_id`, `fingerprint` |
| Payment | `credit_card`, `card_number`, `cvv`, `expiry`, `payment_method`, `billing_address`, `transaction_id` |
| Signed URLs | `exp`, `sig`, `signature`, `X-Amz-Signature`, `X-Amz-Credential` |
| Real hostnames | Rewritten to `*.subsloth.invalid` (IETF-reserved `.invalid` TLD) |

If you encounter a new sensitive field type, add it to `scripts/capture/sanitization-rules.json` before committing fixtures.

### 5. Verification before claiming completion

After any fixture changes:

```bash
nix develop --command ./gradlew :testing:api-contract:test
nix develop --command ./gradlew :core:network:testDebugUnitTest
```

### 6. Captured real data wins over existing fixtures

When a real capture produces a different fixture than the committed one, **the newly captured real API response is the source of truth**.  Update the fixture and, if necessary, the DTO.  Never keep an old fixture that a real capture has replaced.

### 7. Module boundaries

| Module | Role |
|---|---|
| `:testing:api-contract` | `Endpoint` enum, `CaptureApi`, `HarProcessor`, `ExportFixtures`, `WireMockServerFactory`, fixture JSONs, verification tests |
| `:core:network` | Typed DTOs, `Api` Retrofit interface, `FixtureTest` |

### 8. Pipeline troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `captureApi` returns 401 | Wrong credentials | Verify email/password |
| `exportFixtures` writes 0 fixtures | `Endpoint.parse` doesn't recognise the URL | Update `Endpoint.parse` with the new URL pattern |
| `WebDiscoveryFixtureTest` fails | Real hostname leaked into a fixture | Add hostname to sanitisation rules and re-export |
| `FixtureTest` fails | Fixture doesn't decode against DTO | Update fixture shape or DTO |
| `MockMappingVerificationTest` fails | Missing fixture or invalid replay metadata | Verify the fixture exists and the endpoint method/status/content-type metadata matches the real response |
