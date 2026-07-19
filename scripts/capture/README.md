# Capture Workflow

Developer tooling for capturing Media API responses and exporting sanitised fixtures.

## Native fixtures (Kodi REST API)

Calls the API directly — no browser needed.

### Using environment variables (automated, recommended)

```bash
export SUBSLOTH_LOGIN=you@example.com
export SUBSLOTH_PASSWORD=your-password
export SUBSLOTH_URL=https://custom-api.example.com/api/v2
./gradlew :testing:api-contract:captureApi
```

Credentials are read from `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD`
environment variables (same as `ApiLiveDriftTest`). The API base URL
is read from `SUBSLOTH_URL` (falls back to the default endpoint).

### Using Gradle properties (manual)

```bash
./gradlew :testing:api-contract:captureApi -Pemail=... -Ppassword=...
```

Writes to `testing/api-contract/src/main/resources/media/`.

## Web-discovery fixtures (browser HAR)

Export HAR from Firefox DevTools (Network tab -> Save All As HAR), then:

```bash
./gradlew :testing:api-contract:exportFixtures -PharFiles=file.har
```

Writes to `testing/api-contract/src/main/resources/media/web-discovery/`.

## Sanitisation rules

`sanitization-rules.json` defines redacted fields, URL rewrites, and host blocklist.
Add new sensitive fields here before committing fixtures.

## Automated offline validation

### Full pipeline: capture + validate (requires credentials)

```bash
./scripts/capture/validate-fixtures.sh
```

Or directly via Gradle:

```bash
./gradlew :testing:api-contract:captureAndValidate
```

### Offline validation only (no capture, no network)

```bash
./scripts/capture/validate-fixtures.sh --validate
```

Or:

```bash
./gradlew :testing:api-contract:validateFixtures
```

This runs:
- `:testing:api-contract:test` -- fixture existence, sanitisation, WireMock replay, URL validation
- `:core:network:jvmTest` -- DTO schema generation, JSON parse validation, schema round-trip

### Capture only

```bash
./scripts/capture/validate-fixtures.sh --capture
```

## Verification (targeted commands)

```bash
./gradlew :testing:api-contract:test
./gradlew :core:network:testDebugUnitTest
```

## Never commit

- Raw HAR files (`*.har`, `*.har.gz`)
- Credentials in any form
- Authenticated screenshots or browser traces
