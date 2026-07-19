# Handoff — subsloth PR #194 & Skip Intro/Outro Feature

## Goal

The user is building **subsloth**, a native Kotlin Multiplatform app for the some-content-provider.com
media streaming service. This session covered:

1. **PR #194** — adding API drift CI workflow, Kodi client alignment, and `SUBSLOTH_URL` support
2. **Skip intro/outro feature exploration** — reverse-engineering the web player for future implementation
3. **Various PR #194 review loops, CI fixes, and merge** (PR #194 is **merged**)

---

## State

### PR #194 (Merged) — feat/api-drift-ci

- Branch `feat/api-drift-ci` was rebased onto main and squash-merged into PR #194
- Branch still exists locally (worktree conflict prevents deletion)
- **Merged PR**: https://github.com/knirski/subsloth/pull/194

### What PR #194 delivered

| Change | Files |
|--------|-------|
| API drift CI workflow (`workflow_dispatch`) | `.github/workflows/api-drift.yml` |
| ApiLiveDriftTest reads `SUBSLOTH_URL`, uses `apiCall{}` wrapper, has connectivity check | `core/network/src/jvmTest/.../ApiLiveDriftTest.kt` |
| `Content-Type: application/json` header added | `core/network/.../client/ClientFactory.kt` |
| HTTP 402 handling added to `ResponseValidationPlugin` | `core/network/.../client/ResponseValidationPlugin.kt` |
| `DEFAULT_BASE_URL` made `internal` for test access | `core/network/.../client/ClientFactory.kt` |
| `CaptureApi` reads `SUBSLOTH_URL` from env | `testing/.../CaptureApi.kt` |
| `--info` flag for verbose test output in CI | `.github/workflows/api-drift.yml` |
| Docs updated | `scripts/capture/README.md`, `docs/agent/capture-workflow.md`, `docs/project-assessment.md` |

### Current blockers

**API Drift CI workflow** fails with `ResponseValidationException` because the
`SUBSLOTH_URL` GitHub secret points to the web frontend hostname (e.g. `https://some-content-provider.com`)
instead of the Kodi API endpoint (`https://front.some-content-provider.com/api/v2`). The
diagnostic message now clearly tells the user this. The user needs to update the
`SUBSLOTH_URL` secret to include `/api/v2/` path, or the test will keep failing.

---

## Context

### Kodi Plugin API
- API base: `https://front.some-content-provider.com/api/v2/` (mirror/proxy, not the main site)
- Endpoints: `GET /movies`, `GET /shows`, `GET /movies/{id}`, `GET /shows/{id}`, `GET /episodes/{id}`
- Auth: Basic auth with login/password
- User-Agent: `"Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1"`
- Headers: Accept `application/json`, Content-Type `application/json`
- Response shapes documented in `api/subsloth.openapi.yaml`
- Kodi plugin repository: `https://some-content-provider.com/kodi/plugin.video.some-content-providertv/` (v4.0.4)

### Credentials & Secrets
- GitHub secrets: `SUBSLOTH_LOGIN`, `SUBSLOTH_PASSWORD`, `SUBSLOTH_URL`
- kermit Logger for KMP logging
- `${{ secrets.* }}` syntax in CI, `System.getenv("SUBSLOTH_*")` in Kotlin tests
- Prefer `providers.environmentVariable()` in Gradle config over `System.getenv()` for config-cache compatibility

### Architecture — Functional Core / Imperative Shell
- `:core:model` — pure data types, sealed ADTs, domain errors
- `:core:domain` — pure business logic, ports
- `:core:network` — shell module, Ktor client, API, repositories
- `:feature:*` — shell modules, ViewModels, Compose UI
- Errors: `ResponseValidationException` thrown in Ktor plugin, caught by `NetworkErrorClassifier`, mapped to `Outcome.Failure`
- Prefer `Result<T>` / `Outcome<T>` over exceptions for recoverable failures

### Important Patterns
- Gradle tasks use `project.providers.gradleProperty().orElse(providers.environmentVariable()).orElse("")` for config cache safety
- Spotless for formatting, Detekt for linting
- Pre-commit: `./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :androidApp:assembleDebug test`
- CI runs 7 checks: changes, conventional-title, pre-checks, JVM/Desktop, Web/wasmJs, Android, CodeRabbit
- Keep `SUBSLOTH_URL` — the actual site hostname — out of committed files per user instruction

---

## Skip Intro/Outro Feature (Future Work)

### Discovery
The web player embeds skip data via Rails `gon_media.skip` — NOT from the Kodi API.
Full analysis in `docs/features/skip-intro-outro.md`.

### Key Findings
- Data structure per segment: `{ s: float, e: float, l: label, f: flagged }`
- Segment types: `intro`, `outro`, `recap`, `credits`, `scene`
- Button text mapping: `previously_on`/`cold_open_recap` → "Skip Recap";
  `title_sequence`/`studio_bump` → "Skip Intro"; `credits` → "Skip Credits";
  `mid_credit_scene` → "Skip Scene"
- Manual skip only (no auto-skip), triggered by timeupdate events
- Intro+recap merging when gap < 5 seconds
- Segments under 3 seconds discarded
- The `gon_media.skip` data is NOT available via any API endpoint — only embedded in the HTML player page

### Implementation Hurdles
- No Kodi API endpoint for skip data → violates Kodi-only policy if added
- Requires authenticated session on the some-content-provider.com web frontend
- Needs caching for offline playback
- Significant cross-cutting change: data model, network layer, player UI, offline storage

---

## Next Steps

1. **Fix SUBSLOTH_URL in GitHub secrets** — the user needs to update it to include `/api/v2/` path
2. **Delete the local branch** — PR #194 is already merged. The local branch `feat/api-drift-ci` can be cleaned up.
3. **Skip intro/outro** — not prioritized for v1, document exists at `docs/features/skip-intro-outro.md`

---

## Pitfalls & Things That Didn't Work

| Thing | Why it failed |
|-------|---------------|
| `agent-browser` on NixOS | Binary dynamically linked, NixOS stub-ld rejects it |
| Curl to some-content-provider.com | Cloudflare challenge blocks direct access (JS challenge) |
| `ClientFactory.DEFAULT_BASE_URL` private | Made `internal` so tests can reference it |
| `System.getenv()` in Gradle config | Breaks config cache; use `providers.environmentVariable()` |
| Empty string args from Gradle to CaptureApi | `args.getOrNull(0)` returns `""` not `null`; added `?.takeIf { it.isNotBlank() }` |
| Concurrent capture+validate tasks | Added `mustRunAfter` to prevent race |
| `platform(libs.junit.bom)` in KMP jvmTest deps | KMP doesn't support `platform()` in dependencies; use direct lib reference |
| `println()` in test not visible in CI | `System.err.println()` or `--info` flag needed for visibility |
| `git checkout -b feat/x origin/main` sets tracking to main | Later `git push` fails; use `git push origin HEAD` |
