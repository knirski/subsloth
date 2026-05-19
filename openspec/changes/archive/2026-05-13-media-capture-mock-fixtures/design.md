## Context

Authenticated Media browser sessions already reveal both the native Kodi-compatible API surface and a separate set of web-only flows. The current problem is not discovery itself, but reuse: the useful evidence lives in transient session artifacts and manual notes instead of a repeatable, reviewable fixture pipeline.

This change adds a local-only pipeline that captures browser traffic, sanitizes it, and emits committed fixtures plus programmatic replay derived from those fixtures. The native app runtime remains unchanged. The output is intended to support future local replay and developer exploration, but not a production feature.

## Goals / Non-Goals

**Goals:**
- Capture browser-session request/response pairs from authenticated Media discovery.
- Sanitize sensitive values before anything is committed.
- Export fixtures in a repo-friendly form that can be reused by contract tests.
- Replay sanitized fixtures locally from the same committed source of truth.
- Keep native-contract fixtures separate from web-only discovery fixtures.

**Non-Goals:**
- Do not build the side-by-side developer UI yet.
- Do not add runtime browser scraping or production web automation.
- Do not store raw traces, HAR files, cookies, credentials, or signed URLs in the repo.
- Do not change `:core:network` runtime networking behavior.

## Decisions

### 1. Use sanitized committed fixtures as the canonical output
The capture pass should produce repository fixtures, not just local archives. That makes the evidence reviewable in code review and usable by downstream tests. Raw capture artifacts remain temporary and must be deleted after sanitization.

Alternatives considered:
- Keep only local raw archives: rejected because the evidence would not be reusable in CI or code review.
- Store both raw and sanitized outputs: rejected because raw session artifacts are too sensitive and too easy to misuse.

### 2. Split fixtures into native and web-only buckets
The native API surface and the web-only discovery surface should not be mixed. The native bucket stays aligned with the handwritten Retrofit contract; the web bucket records browser-only behaviors like comments, `favorite_media`, `statistics`, `speedtests`, and web catalog filtering.

Alternatives considered:
- One combined fixture set: rejected because it blurs the contract boundary and makes reuse harder.
- Ignore web-only traffic entirely: rejected because it is useful discovery evidence even though it remains out of scope for the native app.

### 3. Derive programmatic replay from the sanitized fixtures
The mock layer should be derived from the same sanitized source that we commit. This keeps replay behavior aligned with the fixtures and avoids hand-maintained WireMock drift. The repository does not need separate mapping files on disk as long as the stubs are registered programmatically from endpoint metadata plus fixture files.

Alternatives considered:
- Hand-write mappings: rejected because it duplicates effort and makes fixture updates fragile.
- Commit generated mapping files: rejected because it creates a second artifact that can drift from the fixtures.

### 4. Keep the workflow local-only and developer-controlled
The capture/export tooling should run only in a local developer environment. It should not require new production dependencies or any server-side support from the shipped app.

Alternatives considered:
- Embed the pipeline in the Android app: rejected because it would couple discovery tooling to production runtime.
- Move capture to CI: rejected because authenticated browser discovery and sensitive artifacts do not belong in CI.

## Risks / Trade-offs

- [Risk] Sanitization misses a sensitive field. → Mitigation: use an allowlist-based sanitizer for all committed fixtures, drop unknown fields by default, test the redaction rules in CI, and inspect exported fixtures before committing.
- [Risk] The web-only bucket grows into a second API surface. → Mitigation: keep it explicitly labeled discovery-only and exclude it from the native contract specs.
- [Risk] Replay behavior diverges from fixtures. → Mitigation: derive stubs from the same source artifacts and verify them together.
- [Risk] The capture flow becomes tied to a specific browser tool. → Mitigation: define the output format independently from the capture mechanism so it can be replaced later.

## Migration Plan

1. Add a capture/export path that records Media browser traffic and sanitizes the result.
2. Emit committed fixture files for the native contract endpoints and a separate web-only discovery bucket.
3. Derive programmatic replay from those fixtures.
4. Add verification for sanitization and replay behavior.
5. If the pipeline proves useful, a follow-up change can add a developer UI that consumes the same fixture set and optionally compares it with live requests.

## Open Questions

- Should endpoint metadata stay WireMock-specific, or evolve into a thin internal replay model that could later support other mock servers?
- Should the fixtures preserve exact response ordering and headers, or keep only the fields needed for DTO and mock replay?
- Should the capture/export tooling be a repo CLI command, a local script, or an OpenSpec-executed workflow helper?
