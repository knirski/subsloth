# Capture Workflow

Developer tooling for capturing Media API responses and exporting sanitised fixtures.

## Native fixtures (Kodi REST API)

Calls the API directly — no browser needed.

```bash
./gradlew :testing:api-contract:captureApi -Pemail=... -Ppassword=...
```

Writes to `testing/api-contract/src/main/resources/media/`.

## Web-discovery fixtures (browser HAR)

Export HAR from Firefox DevTools (Network tab → Save All As HAR), then:

```bash
./gradlew :testing:api-contract:exportFixtures -PharFiles=file.har
```

Writes to `testing/api-contract/src/main/resources/media/web-discovery/`.

## Sanitisation rules

`sanitization-rules.json` defines redacted fields, URL rewrites, and host blocklist.
Add new sensitive fields here before committing fixtures.

## Verification

```bash
./gradlew :testing:api-contract:test
./gradlew :core:network:testDebugUnitTest
```

## Never commit

- Raw HAR files (`*.har`, `*.har.gz`)
- Credentials in any form
- Authenticated screenshots or browser traces