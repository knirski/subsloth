# Capture Workflow

Media fixture-capture and export pipeline. Local-only, never in CI. Credentials must never enter the repository.

## Two Capture Paths

### Native contract fixtures (`captureApi`)

Talks to Kodi-plugin REST API at `front.media-mirror.tv/api/v2/` using Basic auth.

```bash
nix develop --command ./gradlew :testing:api-contract:captureApi
```

Credentials are read from `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD` environment
variables by default. The API base URL is read from `SUBSLOTH_API_BASE_URL` (falls back to
the default `front.media-mirror.tv` endpoint). Fallback to `-Pemail=... -Ppassword=...`
Gradle properties.

Calls 5 Kodi endpoints (`/movies`, `/shows`, `/movies/{id}`, `/shows/{id}`, `/episodes/{id}`), applies sanitization, writes to `testing/api-contract/src/main/resources/media/`. Implementation in `CaptureApi.kt`.

### Web-discovery fixtures (`exportFixtures`)

Captured from browser HAR files. Export HAR from DevTools (Firefox/Chrome), then:

```bash
nix develop --command ./gradlew :testing:api-contract:exportFixtures -PharFiles=session.har,another.har
```

Never commit raw HAR files — they're git-ignored (`*.har`, `*.har.gz`) and contain session cookies. Process and delete originals.

## Agent Rules

1. Never commit raw session artifacts (HAR, `.har.gz`, browser traces, authenticated screenshots, credentials, cookies).
2. Always sanitize before committing — every fixture must pass through `captureApi` or `exportFixtures`. Never copy a response body manually.
3. `Endpoint` enum in `Endpoint.kt` models all known API endpoints. Adding a new endpoint = single-site change: declare enum constant + add branch to `Endpoint.parse`.
4. Security redaction rules:

| Category | Redact |
|---|---|
| Credentials | `password`, `auth_token`, `access_token`, `refresh_token`, `api_key`, `session_id` |
| Auth headers | `authorization`, `bearer`, `cookie`, `set-cookie` |
| PII | `email`, `phone`, `first_name`, `last_name`, `address`, `ip_address`, `geolocation`, `device_id`, `fingerprint` |
| Payment | `credit_card`, `card_number`, `cvv`, `expiry`, `payment_method`, `billing_address`, `transaction_id` |
| Signed URLs | `exp`, `sig`, `signature`, `X-Amz-Signature`, `X-Amz-Credential` |
| Real hostnames | Rewrite to `*.subsloth.invalid` |

5. After fixture changes: `./gradlew :testing:api-contract:test && ./gradlew :core:network:testDebugUnitTest`.
6. Captured real data wins over existing fixtures. When a real capture differs, update fixture and DTO.
7. Module boundaries: `:testing:api-contract` (Endpoint, CaptureApi, HarProcessor, fixtures), `:core:network` (DTOs, Api, FixtureTest).

## Automated Offline Validation

A combined validation pipeline verifies all fixtures offline (no network):

```bash
# Full pipeline: capture + validate
./scripts/capture/validate-fixtures.sh

# Or via Gradle directly
./gradlew :testing:api-contract:validateFixtures
```

What it checks:
- Native JSON fixtures deserialize into their typed DTOs (`MovieListResponse`, `ShowListResponse`, `Movie`, `Show`, `Episode`)
- All JSON fixtures produce valid generated schemas (via `SerializationClassJsonSchemaGenerator`)
- All JSON fixtures parse as valid `JsonElement` and round-trip through serialization
- All non-JSON fixtures (JavaScript, SRT, RedirectLocation) exist and are non-empty
- Fixture bodies contain no sensitive fields (enforced by `WebDiscoveryFixtureTest`)

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `captureApi` 401 | Wrong credentials | Verify email/password |
| `exportFixtures` 0 fixtures | `Endpoint.parse` doesn't recognize URL | Update `Endpoint.parse` |
| `WebDiscoveryFixtureTest` fails | Real hostname leaked | Add to sanitization rules, re-export |
| `FixtureTest` fails | Fixture doesn't decode against DTO | Update fixture shape or DTO |
| `MockMappingVerificationTest` fails | Missing fixture or bad replay metadata | Verify fixture exists and metadata matches real response |
