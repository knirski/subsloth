## Context

Required CI must be deterministic and offline-only. Live Media drift tests require credentials and must stay local in v1. Release artifacts are internal debug-signed APKs.

## Goals / Non-Goals

Goals:

- Prevent comments support and non-Kodi data-source behavior from entering production code from the very first feature change.
- Keep CI free of Media credentials and live network calls.
- Build debug-signed sideload APKs for internal releases when the app scaffold exists.

Non-goals:

- Do NOT add UI/screenshot/benchmark tests here — those belong in `verification-release` after features land.
- Do NOT create a GitHub Actions live-drift workflow.
- Do NOT store Media credentials in GitHub Actions.
- Do NOT add release signing keys or Play Store distribution.

## Decisions

- Use offline tests and invariant checks as required PR CI.
- Gate live drift by `SUBSLOTH_LOGIN`/`SUBSLOTH_PASSWORD` env vars; tests skip without them.
- Use release-please with `release-type: simple` for one repository/product version.
- Produce debug-signed APKs named `subsloth-vX.Y.Z-debug-<shortsha>.apk`.
- Use `googleapis/release-please-action@v4` in `.github/workflows/release-please.yml`. Authenticate with a dedicated `RELEASE_PLEASE_TOKEN` (PAT or GitHub App token); the default `GITHUB_TOKEN` is not used.
- release-please MAY cut releases for any Conventional-Commit-release-worthy change, including docs and spec changes; before the app scaffold exists, such releases are changelog-only.
- Use `gradle/wrapper-validation-action` (v3+) for Gradle wrapper validation in CI.

## Risks / Trade-offs

- No CI live drift means API drift can be missed until a developer runs local tests — document the local command and keep fixture tests strict.

## Migration Plan

1. Add offline CI workflow.
2. Add secret/artifact scanning and invariant checks.
3. Add release-please workflow + `version.txt` + `CHANGELOG.md`.
4. Document development and release docs scaffolding (`docs/development.md`, `docs/release.md`).

## Open Questions

- A protected manual GitHub live-drift workflow requires a separate future design decision.
