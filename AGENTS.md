# Agent Instructions for `subsloth`

**Mission**: deliver verified work and do not overwrite unrelated user state.

> **Historical note (2026-09-09):** OpenSpec has been retired in this repository.
> The `openspec/` directory is kept as a historical record of past requirements
> and changes only. Do NOT create new OpenSpec changes, do NOT run `openspec`
> validation as a commit or verification gate, and do NOT treat `openspec/`
> specs as active requirements. Refer to `docs/agent/README.md` for the docs
> that are actually in force.

## Policy Cascade

1. User instructions > repository docs (`docs/agent/README.md` routes to the matching doc).
2. If conflict: surface it, stop.

## Bootstrap (run before every edit/write session)

1. Read `AGENTS.md`, `best_practices.md`, `docs/agent/README.md`.
2. `git status --short`. Confirm branch and worktree.
3. Choose verification from **Verification Selection** below.
4. Unclear? Stop and report a blocker.

## Format-Before-Commit Rule

Before every commit, amend auto-formatting in:

```bash
./gradlew spotlessApply
if ! git diff --quiet; then git add -A && git commit --amend --no-edit; fi
```

## Pre-Commit Checks (MUST pass before every commit touching Kotlin/build files)

| # | Check | Command |
|---|---|---|
| 1 | Format | `./gradlew spotlessApply spotlessCheck` |
| 2 | Detekt | `./gradlew detekt` |
| 3 | Core KMP | `./gradlew :core:model:compileKotlinJvm :core:domain:compileKotlinJvm` |
| 4 | Full build | `./gradlew :androidApp:assembleDebug` |
| 5 | Tests | `./gradlew test` (or `:feature:X:jvmTest` for a single feature) |

One-liner: `./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :androidApp:assembleDebug test`

## Verification Selection

| Situation | Command |
|---|---|
| Code changed | `./gradlew test` (or narrower task-specific checks) |
| Docs only | Narrowest docs validation or say so |
| PR monitoring/review/merge | `gh` checks, no Gradle build |
| Unsure which check applies | Say so in the final response |

## Stop Conditions

- Request touches files outside the agreed scope or another PR owner's files.
- Dirty worktree affecting scope. Correct branch/base/worktree unclear.
- Verification command unclear. Action is destructive without explicit approval.
- Cannot determine authoritative doc.

## Workflow

- One change → one branch → one PR. Stack only for explicit dependencies.
- Branch: `feat/`, `fix/`, `docs/` prefix. Commit: `feat:`, `fix:`, `refactor:`, `chore:`, etc.
- Format-before-commit (see above), then run pre-commit checks. Do not commit until they pass.
- PR title must be conventional commit. Squash+merge only. Merge when CI is green and no "changes requested" review is active (approval not required).
- Before merging: verify CI green, all review threads resolved, bot comments addressed, no stale CI.
- When resolving review comments: reply explaining fix, then resolve thread.
- Final response format:
  ```text
  changed-files:
  verification-run:
  skipped-checks:
  branch:
  pr:
  blocker:
  ```

## Multi-PR Mode

One PR = one branch = one worktree = one PR owner. One coordinator decides PR boundaries, creates worktrees, assigns ownership. Status reporting: `pr`, `branch`, `worktree`, `state` (planning/implementing/waiting-review/addressing-comments/ready-to-merge/merged/blocked), `blocker`.

## Pre-Merge Checklist

1. CI all SUCCESS (SKIPPED for instrumented-tests OK).
2. Read every PR review thread. Reply and resolve if actionable.
3. Latest commit matches CI run.
4. PR title is conventional commit format.

## Commit Convention (semantic-release)

Every commit on `main` triggers a release via semantic-release. PR title must be conventional commit. Use squash+merge.

| Prefix | Bump | Example |
|---|---|---|
| `feat:` | minor | `feat: add search bar` |
| `fix:` | patch | `fix: crash on empty list` |
| `perf:` | patch | `perf: optimize image loading` |
| `feat!:` / `fix!:` | major | `feat!: drop API v1` |

`docs:`, `chore:`, `style:`, `refactor:`, `test:`, `ci:`, `build:` — no release unless a `BREAKING CHANGE:` footer exists.

## Command & Data Hygiene

- `./gradlew` only (no host gradle). Run inside pinned Nix flake environment.
- Never commit: credentials, auth headers, signed media URLs, browser traces, HAR files, authenticated screenshots.
- `gh` CLI for GitHub. No browser.
- `@Suppress` only for false positives with no cleaner fix. Narrowest scope.

## KMP Safety

Code in `commonMain` compiles for ALL targets (JVM, iOS, Wasm, macOS desktop).
Before pushing, verify that any API used in common code exists across all targets:

- `String.format("...")` — JVM-only. Use string templates.
- `java.*`, `javax.*`, `android.*` — not on non-JVM/non-Android targets.
- `Math.*`, `StrictMath` — use `kotlin.math.*`.

When in doubt, run:
```bash
./gradlew :core:model:compileKotlinWasmJs :core:model:compileKotlinIosArm64 2>&1 | tail -5
```

## Autonomy

- Investigate first (`rg`, `find`). Act independently on implementation details.
- Stop when: ambiguity changes outcome, action is irreversible, request conflicts with repo docs.
- When multiple reasonable approaches exist: choose smallest, most reversible. Explain in final response.
- If blocked: do not edit, commit, or push. Report blocker with exact files, commands, and unclear rule.
