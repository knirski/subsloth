# media-api-developer-ui Specification

## Purpose
TBD - created by archiving change media-api-developer-ui. Update Purpose after archive.
## Requirements
### Requirement: Local Media API Developer UI
The system SHALL provide a local-only developer UI for browsing Media API endpoints and manually executing requests against the OpenAPI contract.

#### Scenario: Developer opens the explorer
- **WHEN** a developer launches the API explorer in a local environment
- **THEN** the UI renders endpoint groups, request/response schemas, and example payloads from `api/subsloth.openapi.yaml`

#### Scenario: Developer tries a request
- **WHEN** a developer submits an endpoint request from the explorer
- **THEN** the UI sends the request to the configured Media base URL and displays the response or error details

### Requirement: Stoplight Elements-Based Rendering
The developer UI SHALL use Stoplight Elements as its OpenAPI rendering layer.

#### Scenario: UI is rendered
- **WHEN** the developer UI loads the OpenAPI contract
- **THEN** the visual reference and request console are rendered by Stoplight Elements components

#### Scenario: OpenAPI contract changes
- **WHEN** `api/subsloth.openapi.yaml` changes
- **THEN** the developer UI reflects the updated endpoints and schemas without requiring a separate generated runtime DTO step

### Requirement: Live And Mock Source Switching
The developer UI SHALL provide a local-only mode that lets a developer switch the same request between live Media and responses served from the local fixture-derived replay layer.

#### Scenario: Developer switches source
- **WHEN** a developer changes the active source for a request
- **THEN** the next request uses either live Media or the local replay layer, as selected

#### Scenario: Switching uses fixture-derived replay
- **WHEN** the developer selects the local replay layer
- **THEN** the mock side is served from the sanitized capture fixtures and programmatic replay stubs, not handwritten sample payloads

### Requirement: Developer Configuration
The developer UI SHALL allow local configuration of the Media base URL and authentication inputs needed for manual request testing.

#### Scenario: Developer supplies local auth
- **WHEN** a developer enters local authentication data in the explorer
- **THEN** the UI uses that configuration for subsequent manual requests during the current local session only

#### Scenario: Developer changes base URL
- **WHEN** a developer switches the target Media base URL
- **THEN** the next request uses the new base URL without affecting production app configuration

### Requirement: Explorer Safety Boundaries
The developer UI SHALL remain outside the production app runtime and SHALL not introduce comments endpoints or other web-only Media capabilities.

#### Scenario: Production app is built
- **WHEN** the production Android application is assembled
- **THEN** the developer UI assets and dependencies are excluded from the app runtime

#### Scenario: Comments are requested
- **WHEN** a developer searches the explorer for comments-related endpoints or resources
- **THEN** no comments endpoint or web-only comments workflow is available

#### Scenario: Source switching remains dev-only
- **WHEN** the production Android application is assembled
- **THEN** source switching and the local mock/replay entrypoint are excluded from the shipped runtime

### Requirement: Sensitive Data Hygiene
The developer UI SHALL not persist or commit credentials, auth headers, signed media URLs, or request traces.

#### Scenario: Request inspection is used
- **WHEN** a developer inspects a manual API request or response
- **THEN** sensitive values remain local to the session and are not written to committed files or release artifacts

#### Scenario: Explorer state is stored
- **WHEN** the explorer stores local UI state
- **THEN** it excludes raw credentials and signed Media URLs from persisted project state

