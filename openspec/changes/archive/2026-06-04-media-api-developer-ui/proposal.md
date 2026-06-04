## Why

The Media OpenAPI file is now serving as validation and documentation, but there is still value in a lightweight developer-facing UI for manually inspecting and trying API endpoints during discovery and debugging. A dev-only explorer keeps that workflow separate from the production app while avoiding another round of ad hoc browser tooling. Now that browser-captured fixtures and fixture-derived replay are being introduced, the explorer can switch the same request between live Media and local mock/replay data without mixing either into the shipped app.

## What Changes

- Add a local-only Media API developer UI built around Stoplight Elements.
- Render the existing `api/subsloth.openapi.yaml` contract in a browser-friendly view for endpoint browsing and manual request execution.
- Support local developer auth input and request inspection without introducing any production runtime dependency or user-facing feature.
- Add an easy source switch that lets a developer send the same request to either the live Media API or the local mock/replay layer and inspect the results with minimal friction.
- Reuse the sanitized fixtures and programmatic replay from the capture pipeline as the mock side of that comparison.
- Keep the existing OpenAPI validation and handwritten network models unchanged.
- Do not add comments endpoints, production API explorer screens, or any browser-based Media interaction in the shipped app.

## Capabilities

### New Capabilities
- `media-api-developer-ui`: local-only OpenAPI-driven UI for browsing and manually testing Media endpoints during development, with easy live-vs-mock source switching using fixture-derived local replay.

## Impact

- Affects developer tooling, local docs, and potentially a small dev-only web or desktop entrypoint.
- May introduce a browser-based UI asset or local dev server, but only for developer use.
- Depends on the capture/mocking change for replay fixtures and programmatic replay.
- No production networking, UI, or API contract behavior changes are intended.
