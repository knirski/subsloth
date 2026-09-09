# Shared Agent Guidance

This directory is the shared source of truth for cross-agent instructions in this repo.

Always read:

1. `AGENTS.md` for repo-wide bootstrap rules.

Then route by task and stop once you have the matching docs:

1. `docs/codestyle.md` - normative policy for code changes.
2. `docs/agent/publishing.md` - normative policy for commits and PRs.
3. `docs/agent/review-loop.md` - execution workflow for existing PRs.
4. `docs/agent/gh-cli.md` - reference only for `gh` syntax.
5. `docs/agent/multi-pr-coordinator.md` - execution workflow for multiple PRs.
6. `docs/agent/capture-workflow.md` - execution workflow for capture/export tasks.
7. `docs/agent/emulator-testing.md` - execution workflow for Android emulator and instrumented tests.
8. `docs/agent/lessons-learned.md` - hard-won API, toolchain, and CI patterns from past PRs.
9. `docs/module-structure.md` - reference for the 24-module architecture, dependency graph, and convention-plugin assignment.
10. `docs/convention-plugins.md` - reference for all precompiled Gradle convention plugins.
11. `docs/testing/benchmarks.md` - execution workflow for macrobenchmarks and baseline profile generation.
12. `docs/testing/screenshot-tests.md` - execution workflow for Compose Preview Screenshot Testing.
13. `docs/navigation3.md` - reference for Navigation3 architecture across all platforms.
14. `docs/troubleshooting.md` - reference for common build, test, emulator, and IDE issues.
15. `docs/architecture/composition-roots.md` - reference for which class constructs concrete adapters per platform, and whether that platform's wiring is production-ready or falls back to a non-production default.

If multiple docs match, read the normative policy docs first, then the execution workflow docs, then the reference docs.

> **Historical note (2026-09-09):** OpenSpec has been retired in this repository.
> `docs/agent/openspec-workflows.md` and the `openspec/` directory are kept for
> historical record only. Do not create new OpenSpec changes or run `openspec`
> validation as a gate.

## Domain Skills

These docs cover architecture and technology-specific conventions. Load the matching skill when working in these domains.

| Doc | Skill | When to use |
|---|---|---|
| `fc-is-architecture.md` | `fc-is-architecture` | Writing or reviewing architecture — FC/IS separation, sealed ADTs, pure functions, port/adapter, `Result<T>`, module deps |
| `compose-performance.md` | `compose-performance` | Writing Compose UI — stability annotations, recomposition, key stability, Flow collection |
| `compose-ui-patterns.md` | `compose-ui-patterns` | Writing Compose UI — state hoisting, slot APIs, UDF, sealed UiState mapping, Material3, adaptive layouts |
| `kotlin-coroutines.md` | `kotlin-coroutines` | Writing async code — scopes, dispatchers, `StateFlow`, cancellation, testing |
| `agp-migration.md` | `agp-migration` | Upgrading AGP, Gradle, or Kotlin — convention plugins, version catalog, toolchain |
| `fc-is-data-layer.md` | `fc-is-data-layer` | Writing data access — Room, Ktor, DataStore, DTO mapping, offline-first |
| `ktor-networking.md` | `ktor-networking` | Writing network code — Ktor plugins, Kodi identity, rate limiting, request coalescing |

Agent-specific skill files (`.codex/skills/`, `.opencode/skills/`, `.agents/skills/`) should stay thin and point back here instead of duplicating policy text.
