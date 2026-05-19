# `api-contract` — Testing Strategy

This module holds the **sanitized Media fixtures**, the **programmatic WireMock stub factory**, and the **verification suite** that proves the production HTTP client (`:core:network`) can deserialize the fixture payloads correctly.

## Layers of testing

| Layer                    | What it validates                                                              | Where                           | Credentials needed? |
|--------------------------|--------------------------------------------------------------------------------|---------------------------------|---------------------|
| **Unit / fixture**       | Each fixture JSON decodes into the typed DTO without data loss                 | `FixtureTest` (in `:core:network`) | no                  |
| **Contract / mock**      | The real Retrofit client makes real HTTP calls against WireMock stubs and gets back typed DTOs | `WireMockIntegrationTest` (in `:core:network`) | no |
| **Drift / live**         | The real Retrofit client against the actual Media API — catches schema changes | `ApiLiveDriftTest`         | yes (`SUBSLOTH_LOGIN` / `SUBSLOTH_PASSWORD`) |

## Why mock-server contract tests?

The `FixtureTest` already proves that a JSON string can be deserialized into a Kotlin data class. But that tests only the **parser** — not the full **transport**: HTTP headers, status codes, interceptors, or query-parameter encoding.

The integration tests in this suite:

1. **Start a real HTTP server** (`WireMockServerFactory`) that serves the same sanitized fixture files that live in this module.
2. **Create the real Retrofit client** (`ClientFactory`) pointing at that server. The client carries the same interceptors (Kodi User-Agent, Basic auth) that the production app uses — they just run against fake credentials.
3. **Exercise every native API endpoint** — list movies, list shows, movie detail, show detail, episode detail — and assert the returned DTO fields match the fixture content.

This catches problems that unit-level fixture tests miss:

- URL path templating (`movies/{id}` → `/movies/12345`)
- Query-parameter encoding (`page=2&per_page=50`)
- HTTP header handling (`Content-Type` must be `application/json`)
- Interceptor execution (does the auth header break the request?)
- Retrofit / kotlinx.serialization integration (the converter factory)

## Edge-case coverage

| Scenario | What breaks | How it's tested |
|---|---|---|
| **Non-matching path (404)** | The Retrofit client, interceptors, and error-handling code must not crash on a 404 response. | A bare `WireMockServer` (no stubs) returns WireMock's default 404. The client receives an `HttpException(404)`. |
| **Connection refused** | Network reachability, retry logic, or error-reporting code may assume the server is always up. | Point the client at `localhost:1` (no server). Retrofit throws `IOException`. |
| **Malformed response body** | `kotlinx.serialization` may produce a cryptic exception. The error-reporting layer must handle it. | A WireMock stub returns `{invalid json` — the client receives a `SerializationException`. |
| **Query parameters** | The server (or stub) must not reject requests with extra query params. | Call `listMovies(page = 2, perPage = 50)`. WireMock's `urlPathMatching` ignores query strings, so the fixture is returned as-is. |

## Sealed `Endpoint` type

All known API endpoints are modelled as a sealed ADT in [`Endpoint.kt`](src/main/kotlin/net/subsloth/testing/contract/Endpoint.kt).

Each variant defines:
- `resourcePath` — the classpath fixture location for single-method endpoints
- `resourcePathFor(method)` — the classpath fixture location for a specific verb when an endpoint has multiple replay fixtures
- `urlPattern` — the WireMock URL pattern (e.g. `"/movies"`, `"/speedtest.*"`)
- `category` — `Native` (goes to `:core:network`) or `WebDiscovery`
- `methods`, `responseStatus`, and `responseKind` — the replay metadata that drives programmatic stubs

**Adding a new endpoint** is a single-site change: declare a new endpoint with its fixture path and replay metadata. The sealed type keeps call sites exhaustive across the capture, export, and replay layers.

The `Endpoint.parse` method must also be updated with a URL-matching branch so that captured HAR traffic can be routed to the new endpoint.

## How to run

```bash
# All contract and unit tests (no credentials needed)
./gradlew :testing:api-contract:test :core:network:testDebugUnitTest

# Live drift detection (requires credentials)
SUBSLOTH_LOGIN="..." SUBSLOTH_PASSWORD="..." ./gradlew :core:network:testDebugUnitTest --tests "*ApiLiveDriftTest"
```

## Module structure

```
testing/api-contract/
├── build.gradle.kts
├── README.md                              ← this file
├── src/
│   ├── main/
│   │   ├── kotlin/net/subsloth/testing/contract/
│   │   │   ├── Endpoint.kt                ← sealed ADT (all API variants)
│   │   │   ├── ExportFixtures.kt          ← CLI entry point (HAR → fixtures)
│   │   │   ├── FixtureLoader.kt           ← classpath fixture loader
│   │   │   ├── HarProcessor.kt            ← HAR parsing + sanitization
│   │   │   ├── SanitizationRules.kt       ← redaction + URL rewriting
│   │   │   └── WireMockServerFactory.kt   ← programmatic stub registration
│   │   └── resources/media/web-discovery/ ← web-only discovery fixtures
│   ├── resources/media/                    ← native-contract fixture JSONs
│   └── test/kotlin/net/subsloth/testing/contract/
│       ├── MockMappingVerificationTest.kt ← per-endpoint fixture + replay checks
│       └── WebDiscoveryFixtureTest.kt     ← web-discovery content validation
```

> **Note:** The native-contract fixture JSONs live alongside the web-discovery fixtures in `src/main/resources/media/`. Consumers depend on the module directly via `testImplementation(project(":testing:api-contract"))`. The WireMock stubs are registered **programmatically** — there are no mapping JSON files on disk.
