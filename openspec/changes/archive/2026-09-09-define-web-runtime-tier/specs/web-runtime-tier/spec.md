# web-runtime-tier Specification

## Purpose

Define the GitHub Pages Web runtime as a credential-free, mock-backed demo and
the evidence required before any Web deployment may be promoted to an
authenticated production tier.

## ADDED Requirements

### Requirement: GitHub Pages Demo Mode

The GitHub Pages deployment SHALL run in an explicit Demo mode that uses only
deterministic fixture-backed mock data and SHALL NOT contact the live Media API.
Demo mode SHALL be selected by the Web composition root and SHALL NOT be
switchable to live mode through a public runtime setting or query parameter.

#### Scenario: Pages starts in Demo mode

- **WHEN** the deployed Web application starts
- **THEN** it selects Demo mode before constructing feature dependencies
- **AND** the network client is the fixture-backed mock client
- **AND** no live Media API request is made

#### Scenario: A user attempts to select live mode

- **WHEN** a user changes a URL parameter, browser storage value, or visible
  runtime control intended to select live mode
- **THEN** the Pages application remains in Demo mode
- **AND** it does not construct a live API client

### Requirement: Demo Mode Labelling

The Pages application SHALL display a persistent, accessible label that states
the application is a demo using sample data and that Media credentials are not
requested or stored. Documentation describing the Pages deployment SHALL link
to or repeat the same Demo-mode status without calling the deployment a
production client.

#### Scenario: A visitor identifies the runtime tier

- **WHEN** a visitor opens the Pages application
- **THEN** the Demo mode label is visible in the application UI
- **AND** the label is available to accessibility tooling as text
- **AND** the visitor is not required to inspect source code or browser logs to
  discover the tier

### Requirement: No Demo Credential Boundary

Demo mode SHALL NOT construct the Web `CredentialStore`, render an interactive
Media login flow, read or write the known Web credential storage keys, or retain
raw Media credentials in browser storage.

#### Scenario: Demo starts with empty browser storage

- **WHEN** the Pages application starts with no existing browser storage
- **THEN** it reaches the Demo experience without rendering a credential form
- **AND** the known credential ciphertext and key entries remain absent

#### Scenario: Demo starts with stale credential-shaped storage

- **WHEN** browser storage contains the known Web credential keys before startup
- **THEN** Demo mode does not read, decrypt, refresh, or use those values
- **AND** the Demo experience remains unauthenticated

### Requirement: Web Promotion Gate

An authenticated Web deployment SHALL NOT be described as production-ready
until it has an approved authentication/API boundary, verified CORS behavior
for its deployed origin, safe credential handling, meaningful browser tests,
and a deployed acceptance check. If OPFS-backed persistence is claimed, the
deployment SHALL also provide and verify the required cross-origin isolation
headers and a reload-persistence scenario.

#### Scenario: A future deployment requests promotion

- **WHEN** a Web deployment is proposed as authenticated production
- **THEN** the proposal links evidence for each promotion gate
- **AND** the GitHub Pages Demo deployment remains labelled Demo unless those
  gates apply to that exact deployment

#### Scenario: A promotion gate is missing

- **WHEN** any required authentication, CORS, credential, browser-test,
  isolation, or persistence evidence is missing
- **THEN** the deployment remains a Demo or Preview tier
- **AND** documentation does not claim production readiness
