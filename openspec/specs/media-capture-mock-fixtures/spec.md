# media-capture-mock-fixtures Specification

## Purpose
Browser traffic capture pipeline for authenticated Media API traffic, sanitized fixture export with sensitive data removal, native/web fixture separation, programmatic fixture replay for local development, and developer-only scope ensuring capture tooling stays outside production builds.
## Requirements
### Requirement: Browser Traffic Capture Pipeline
The system SHALL provide a local-only workflow for capturing authenticated Media browser traffic and exporting sanitized request/response fixtures.

This pipeline produces the sanitized fixtures required by the `api-contract` spec's "Sanitized Fixtures" requirement.

#### Scenario: Capture is performed locally
- **WHEN** a developer runs the capture workflow in a local environment
- **THEN** the workflow records request/response pairs from the authenticated browser session

#### Scenario: Sensitive values are removed
- **WHEN** captured traffic is exported
- **THEN** the exported fixtures contain no credentials, cookies, auth headers, signed URLs, raw browser traces, HAR files, user identifiers, account IDs, usernames, email addresses, phone numbers, IP addresses, geolocation data, device identifiers, fingerprints, viewing history, behavioral data, payment information, transaction IDs, or other private account data

### Requirement: Native And Web Fixture Separation
The capture workflow SHALL separate native-contract fixtures from web-only discovery fixtures.

#### Scenario: Native contract evidence is exported
- **WHEN** the workflow captures `movies`, `shows`, `movie detail`, `show detail`, or `episode detail`
- **THEN** those fixtures are written to the native contract fixture set used by `:core:network`

#### Scenario: Web-only discovery evidence is exported
- **WHEN** the workflow captures web-only Media behaviors such as comments, `favorite_media`, `statistics`, `speedtests`, subtitle-download responses, or browser catalog filters
- **THEN** those fixtures are written to a separate discovery-only fixture set

### Requirement: Programmatic Fixture Replay
The system SHALL replay sanitized fixtures locally through programmatic mock stubs derived from the same committed fixture set.

#### Scenario: Replay stubs are derived from fixtures
- **WHEN** sanitized fixtures are exported
- **THEN** the replay layer can serve the same captured responses locally from those fixtures without separate mapping files

#### Scenario: Fixture updates refresh replay behavior
- **WHEN** a sanitized fixture changes
- **THEN** the local replay behavior changes from the same fixture source of truth

### Requirement: Developer-Only Scope
The capture and mock pipeline SHALL remain outside the production app runtime.

#### Scenario: Production app is built
- **WHEN** the Android application is assembled for production
- **THEN** the capture workflow, raw session artifacts, and programmatic replay tooling are excluded from the shipped runtime

#### Scenario: No raw session artifacts are committed
- **WHEN** the workflow finishes
- **THEN** any raw session data needed for capture is deleted, temporary capture paths are ignored via `.gitignore`, and only sanitized committed artifacts remain

