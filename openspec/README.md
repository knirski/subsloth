# OpenSpec

This repo uses OpenSpec as the source of truth for v1 product and engineering behavior.

## Workflow

1. Read current requirements from active changes under `openspec/changes/*/specs/`.
2. Implement and verify each change in order.
3. Archive completed changes with `openspec archive <change-id>` to promote their specs into `openspec/specs/`.
4. After archive, `openspec/specs/` becomes the canonical accepted baseline.
5. Propose future requirement deltas as new changes under `openspec/changes/`.

## Active Changes

The v1 implementation is split into 11 active changes. Step 0 bootstraps the development environment; the first five app changes are sequential; the four feature changes can be developed in parallel; verification gates the release.

Development bootstrap:

0. `dev-environment-bootstrap` - Nix flake, direnv, CLI toolchain, Android SDK tooling, and Android Studio in one development shell.

Sequential foundation:

1. `foundation-api-contract` - project scaffold, toolchain baseline, API discovery, and OpenAPI contract.
2. `release-and-ci-foundation` - offline-only required CI, secret/artifact scanning, release-please, version.txt, CHANGELOG, and debug sideload APK workflow.
3. `core-domain-network` - functional core, typed domain policies, network client, and mappers.
4. `auth-persistence-shell` - login, credentials, account profiles, persistence, logout, and app shell.
5. `android-ui-foundation` - cross-cutting adaptive layout primitives, TV focus, accessibility, edge-to-edge, predictive back, and state restoration.

Parallel feature band (any order, all depend on the foundation):

6. `catalog-details` - catalog, search, filters, sort, details, cache freshness, recency, and comments exclusion.
7. `playback` - playback session, resume, subtitles, quality, speed, next episode, watched scoping, and Media3 boundary.
8. `offline-downloads` - offline catalog, downloads, queues, fallback policy, storage safety, and operational notifications.
9. `library-settings-diagnostics` - library, central Downloads, storage management, settings, and diagnostics.

Final gate:

10. `verification-release` - architecture/UI/TV/accessibility/screenshot tests, baseline profiles, macrobenchmarks, and device acceptance.

Each change has `proposal.md`, `design.md`, `specs/`, and `tasks.md`. The tasks are high-level checkboxes. For step-level implementation detail (exact version catalog entries, Gradle config, Kotlin snippets, CI YAML), reference the original plan archived at `docs/archive/superpowers/plans/2026-05-04-subsloth-android-app-implementation.md`.

Archive each change with `openspec archive <change-id>` after implementation and verification. Archiving promotes the change's delta specs into `openspec/specs/`.

## Spec Lifecycle

`openspec/specs/` is intentionally empty until changes are implemented and archived. Requirements currently live inside the active changes as `## ADDED Requirements` deltas. Once a change is archived, its requirements become part of the canonical baseline in `openspec/specs/`.

To read all current v1 requirements before any change is archived, look across the active changes:

```bash
find openspec/changes/*/specs -name 'spec.md' | sort
```

## Execution Detail

The OpenSpec change tasks define *what* to implement and *how to verify* it. The original Superpowers implementation plan defines *how* to implement it at the code level. Both are needed:

- **Change specs** (`openspec/changes/*/specs/`) — requirements for the current implementation phase.
- **Change tasks** (`openspec/changes/*/tasks.md`) — implementation checklists with verification commands.
- **Archived plan** (`docs/archive/superpowers/plans/`) — step-level implementation detail (file paths, code samples, version catalog, Gradle config, CI workflows).

After archive, `openspec/specs/` becomes the canonical source. If archived source docs and `openspec/specs/` disagree, `openspec/specs/` wins.

## Validation

```bash
openspec validate --all --strict
```

```bash
openspec list --specs   # canonical specs (populated after archive)
openspec list           # active changes
```
