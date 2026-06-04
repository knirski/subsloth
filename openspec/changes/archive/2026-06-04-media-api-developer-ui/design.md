## Context

The current Media API work treats `api/subsloth.openapi.yaml` as the contract source for validation and documentation, while the native app uses handwritten Retrofit models in `:core:network`. That leaves a gap for manual endpoint exploration during discovery and debugging, especially when checking live behavior against the sanitized contract.

This change adds a developer-only API UI that stays outside the shipped Android app and does not change production networking, storage, or UI behavior. The explorer must respect the existing security boundaries: no comments endpoints, no browser scraping flows, and no committed secrets or signed URLs. It can also consume the local fixture-derived replay layer so a developer can switch the same request between live and replay sources.

## Goals / Non-Goals

**Goals:**
- Provide a lightweight local UI for browsing Media endpoints and manually trying requests.
- Reuse the committed OpenAPI document as the source of endpoint metadata and examples.
- Support easy live-vs-mock source switching using fixture-derived local replay.
- Keep the tool strictly dev-only so it never becomes part of the production APK or runtime app shell.
- Support local authentication input without storing credentials in committed artifacts.

**Non-Goals:**
- Do not replace the handwritten `:core:network` API models or client.
- Do not reintroduce OpenAPI code generation for runtime use.
- Do not add comments support, browser automation, scraping, or web-only Media endpoints.
- Do not ship the explorer inside the production Android app.

## Decisions

### 1. Use Stoplight Elements as the embedded UI
Stoplight Elements gives a modern OpenAPI viewer with a built-in request console and a smaller footprint than a broader docs platform. It is a better fit for a local developer explorer than building a custom UI or pulling in a heavier documentation system.

Alternatives considered:
- Scalar: more polished and broader in scope, but heavier and more platform-oriented than we need for a local dev tool.
- Swagger UI: familiar, but older-looking and less aligned with the current design direction.

### 2. Host the explorer as a dev-only web asset
The explorer should live in a local-only entrypoint that can be run during development without modifying the Android app runtime. That keeps the feature isolated from the production dependency graph and makes it easy to open against a local or remote Media base URL when needed.

Alternatives considered:
- Add a screen to the Android app: rejected because it would mix developer tooling into production navigation and packaging.
- Build a desktop wrapper: possible later, but unnecessary for the first version and more complex than a small web asset.

### 3. Route requests through explicit developer configuration
The UI should accept a manually supplied API base URL and credentials during local use, rather than hard-coding environment-specific assumptions. The explorer should make the active request configuration visible and disposable, not persisted in repo files or release artifacts.

Alternatives considered:
- Embed fixed credentials or a fixed host: rejected because it would be unsafe and brittle.
- Proxy through the production app shell: rejected because it would couple dev tooling to app runtime code.

### 4. Keep OpenAPI as the source of endpoint metadata, not runtime generation
The OpenAPI file remains the authoritative contract for validation, endpoint descriptions, and explorer metadata. Runtime networking continues to use the handwritten Retrofit interface and models already established in `:core:network`.

Alternatives considered:
- Switch back to generated DTOs: rejected because the current core/network implementation already proved handwritten models are the stable path.
- Duplicate the contract in a second docs format: rejected because it would create divergence from the existing source of truth.

### 5. Use captured fixtures as the switching baseline
The source-switching mode should send the same request either to live Media or to the replayed response derived programmatically from sanitized capture fixtures. That keeps the workflow grounded in the same captured evidence and avoids maintaining a second mock dataset by hand.

Alternatives considered:
- Compare against handwritten sample JSON: rejected because it would drift from the captured evidence.
- Query only the live API: rejected because the mock/replay path is the point of the workflow.

## Risks / Trade-offs

- [Risk] A browser-based tool can drift into production expectations. → Mitigation: keep the explorer in a dev-only path and exclude it from the Android app module.
- [Risk] Manual request execution can leak sensitive headers or signed URLs if logs are careless. → Mitigation: default to redaction, keep state local, and do not commit session artifacts.
- [Risk] The explorer may tempt future web-only feature creep. → Mitigation: explicitly scope it to endpoint browsing and manual requests only.
- [Risk] Source switching can become a hidden second test harness. → Mitigation: keep it developer-only and derive the mock side from the same sanitized fixtures used for replay.
- [Risk] Stoplight Elements may be heavier than a minimal custom viewer. → Mitigation: accept the extra weight because the goal is a usable developer console, not the smallest possible bundle.

## Migration Plan

1. Add a new OpenSpec capability and validate the desired scope before implementation.
2. Create a dev-only explorer entrypoint that reads the existing OpenAPI file and renders it with Stoplight Elements.
3. Wire local authentication and base URL configuration so developers can point the UI at the live Media service or a local mock.
4. Add a source-switching view that sends the same request to live Media or the local mock/replay layer, then makes the active source obvious for manual inspection.
5. Verify the explorer stays outside the production app build and does not change `:core:network` runtime behavior.
6. If the tool proves too heavy or too coupled, rollback by removing the dev-only entrypoint without touching the contract or network modules.

## Open Questions

- Should the explorer be a static HTML/JS asset, a small local web server, or a dedicated dev script that launches the browser?
- Should it read the OpenAPI file directly from disk, or from a locally served URL for easier browser integration?
- Should we add a companion dev workflow for auth headers and base URLs, or keep configuration entirely manual for v1?
