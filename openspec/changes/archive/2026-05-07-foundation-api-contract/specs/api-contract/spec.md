## ADDED Requirements

### Requirement: Kodi Compatible API Surface
The native app SHALL use the Media Kodi-compatible API surface as its v1 data source.

#### Scenario: Allowed endpoints are used
- **WHEN** the network layer fetches catalog or detail data
- **THEN** it uses `https://front.media-mirror.tv/api/v2` endpoints for movies, shows, and episodes that match the Kodi add-on contract

#### Scenario: Browser identity is excluded
- **WHEN** the app sends production Media API requests
- **THEN** it does not use WebView, Chrome, headless browser, automation, test-runner, OkHttp/Dalvik, or emulator-debug request identity

---

### Requirement: API Discovery Gate
Before implementation locks DTOs, persistence schema, or UI fields, credentialed API discovery SHALL verify Media response shapes, media URL behavior, subtitle structure, status codes, and request metadata.

#### Scenario: Discovery precedes DTO lock-in
- **WHEN** network DTOs or mappers are implemented
- **THEN** sanitized fixtures and `api/subsloth.openapi.yaml` have been updated from Kodi-compatible discovery evidence

#### Scenario: Discovery-gated feature is unsupported
- **WHEN** discovery disproves quality selection, confirmed season download queues, or precise recency data
- **THEN** downstream UI hides the control or shows an unavailable reason instead of simulating support

---

### Requirement: DTO Contract
Network DTOs SHALL be handwritten from `api/subsloth.openapi.yaml` evidence and shall use the project's standard serialization library. No Moshi artifact shall appear in any module's production dependency graph.

#### Scenario: DTOs are handwritten from the OpenAPI spec
- **WHEN** `:core:network:test` runs against the committed Media fixtures
- **THEN** typed DTO classes in the `subsloth.core.network.media.api.model` package deserialize the contract responses and the build compiles without error

#### Scenario: Moshi is absent
- **WHEN** the network module's production dependency graph is inspected
- **THEN** no `com.squareup.moshi:*` artifact appears

---

### Requirement: Comments Exclusion
The native app SHALL NOT fetch, show, post, count, sort, model, or depend on movie or TV-series comments.

#### Scenario: Web comments resource exists
- **WHEN** authenticated web pages auto-load web-only comments resources such as `/api/frontend/comments`
- **THEN** the native app excludes those resources from its OpenAPI contract, tests, mappers, and production client

---

### Requirement: Sanitized Fixtures
API fixtures SHALL include only sanitized response shapes and non-sensitive examples.

#### Scenario: Fixture is committed
- **WHEN** a fixture is added under the API contract test resources
- **THEN** it contains no credentials, auth headers, signed stream URLs, signed download URLs, private account data, raw browser logs, HAR files, snapshots, or authenticated screenshots

---

### Requirement: Offline Contract Tests
Fixture validation tests SHALL run offline, deterministically, and without credentials.

#### Scenario: Offline tests always pass
- **WHEN** `./gradlew :core:network:test` is executed without any environment credentials set
- **THEN** all fixture schema tests pass

---

### Requirement: Local Live Drift Tests
Live Media drift tests SHALL be optional, local-only, and gated by developer environment variables.

#### Scenario: Credentials are absent
- **WHEN** `SUBSLOTH_LOGIN` or `SUBSLOTH_PASSWORD` is not set
- **THEN** live drift tests are skipped automatically and all offline contract tests still pass

#### Scenario: Credentials are present locally
- **WHEN** a developer runs the test suite with `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD` set
- **THEN** live drift tests verify Kodi-compatible endpoint availability, request metadata, response shapes, required fields, and status-code behaviour against the live API
