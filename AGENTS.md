# Agent Instructions for `subsloth`

Mission: deliver work that matches the active OpenSpec change, passes that change's verification, and does not overwrite unrelated user state.

## Defaults

- Default: one active OpenSpec change -> one branch -> one PR.
- Default: use the pinned Nix flake toolchain with `direnv allow` or `nix develop --command <cmd>`.
- Default: verify before claiming completion.
- Default: act without asking when the repo, specs, and code already answer the question.

## Precedence

- User instructions win unless they conflict with active OpenSpec requirements.
- If a conflict exists, surface it and stop for direction.
- Otherwise, active OpenSpec requirements win.
- Then `openspec/specs/` wins over archived planning material.

## Source Of Truth

- Treat `OpenSpec` as authoritative for product and engineering behavior.
- Active requirements live in `openspec/changes/*/specs/`; archived changes live in `openspec/changes/archive/`; accepted baseline specs live in `openspec/specs/`.
- If docs conflict, prefer active change specs and `openspec/specs/`.
- If a user request conflicts with active OpenSpec requirements, surface the conflict and stop for direction.

## Bootstrap Checklist

Before editing or running write actions:
1. Read `AGENTS.md`, `best_practices.md`, and `docs/agent/README.md`.
2. Run `git status --short`.
3. Confirm branch and worktree.
4. Identify the active OpenSpec change.
5. Read that change's `proposal.md`, `design.md`, `specs/`, and `tasks.md`.
6. Choose verification from **Verification Selection**.
7. If any step is unclear, stop and report a blocker.

## Shared Agent Guidance

- Shared cross-agent instructions live in `docs/agent/`.
- Use `docs/agent/README.md` to route to the matching workflow doc.
- Keep `.codex/skills/`, `.opencode/skills/`, and `.agents/skills/` as thin adapters that point back to those docs.

## Multi-PR Mode

- Default to one active OpenSpec change -> one branch -> one PR. Split one change into multiple PRs only for explicit dependencies or clear review boundaries.
- When multiple PRs are active at once, use one worktree per PR and one PR owner per PR.
- Use one coordinator agent to decide PR boundaries, create worktrees, assign ownership, and monitor overall status.
- The PR owner is responsible for implementation, verification, publishing, review replies, monitoring, and merge decisions for that PR.
- Do not let one subagent push to, reply on, or merge another subagent's PR.
- Keep status reporting compact and explicit: `pr`, `branch`, `worktree`, `state`, and `blocker`.
- Use only these `state` values: `planning`, `implementing`, `waiting-review`, `addressing-comments`, `ready-to-merge`, `merged`, `blocked`.

## Required Workflow

- Follow the **Bootstrap Checklist**, then read `openspec/README.md` and the active change `tasks.md`.
- Use the task list and repo docs to choose verification. Prefer `openspec validate <change-id> --strict` for one active change and `openspec validate --all --strict` when shared OpenSpec content changes.
- Do not claim completion until the relevant checks succeed. If full verification is blocked, run the narrowest meaningful checks and report exactly what was skipped and why.
- In the final response, include changed files, exact verification runs, and any skipped checks with the reason.
- Archive with `openspec archive <change-id>` only when all tasks are done, verification passed, and the user confirmed the archive.
- Before creating a PR, ensure lint, detekt, and tests are all green. Do not create a PR with failing checks.
- When resolving review comments in a PR, reply to each comment explaining the fix or reasoning, then resolve the thread.

## Pre-Commit Checks

**MUST run ALL of these before every commit that touches Kotlin or build files. Do not commit until they pass. This is not optional.**

| # | Check | Command | Catches |
|---|---|---|---|
| 1 | Formatting | `./gradlew spotlessApply` then `./gradlew spotlessCheck` | unused imports, wrong indentation, trailing whitespace, import ordering |
| 2 | Detekt | `./gradlew detekt` | code smells, complexity violations, style issues |
| 3 | Compile (core KMP) | `./gradlew :core:model:compileKotlinJvm :core:domain:compileKotlinJvm` | compilation errors in domain types, missing deps |
| 4 | Compile (full) | `./gradlew :app:assembleDebug` | cross-module compilation errors, Android resource issues |
| 5 | Tests | `./gradlew test` | behavioral regressions |

**Shortcut for all checks in one line:**
```bash
./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :app:assembleDebug test
```

**If spotlessApply made changes, re-stage them before committing.** Check with `git diff --name-only` to verify only intended files were modified.

**Why this exists:** CI runs `spotlessCheck` and `detekt` on every push. A failure here means a red CI, wasted review cycles, and an extra commit to fix formatting. Run checks locally first.

## Verification Selection

| Situation | Required verification |
|---|---|
| One active OpenSpec change, code or behavior changed | `openspec validate <change-id> --strict` plus the task-specific checks from that change's `tasks.md` |
| Shared OpenSpec content changed, multiple change docs changed, or canonical `openspec/specs/` changed | `openspec validate --all --strict` |
| Agent docs or repo docs only | run the narrowest meaningful docs verification; if none exists, say so explicitly |
| PR monitoring, review replies, or merge timing only | no Gradle build by default; use `gh` checks and PR state reads |
| Unsure which change owns the work | stop and report a blocker before editing |

## Stop Conditions

- The user request conflicts with active OpenSpec requirements.
- The work crosses OpenSpec change boundaries and the split is unclear.
- The current worktree is dirty in a way that affects the assigned scope.
- Another PR or another PR owner's files must be edited.
- The correct branch, base branch, or worktree is unclear.
- The verification command is unclear.
- The requested action is destructive or irreversible and the user did not explicitly approve it.
- You cannot tell which doc or spec is authoritative.

## Git And PR Hygiene

- Every change to `main` MUST go through a pull request. Never push directly to `main`.
- Start from a clean worktree when feasible. If it is already dirty, inspect first and keep unrelated changes untouched.
- Do not delete, reset, or overwrite user changes without explicit permission.
- Choose the branch and base branch before the first edit.
- Use branch names like `feat/auth-shell`, `fix/core-network-timeout`, and `docs/agent-instructions`.
- Use commit prefixes like `feat:`, `fix:`, `docs:`, `chore:`, and `refactor:`.
- Use PR titles like `feat: add auth shell`, `fix: tighten core network timeout`, and `docs: update agent instructions`.
- Use stacked PRs only for explicit dependencies.
- Keep edits within the active OpenSpec change unless the user expands scope. If work crosses change boundaries, stop and split it before continuing.
- Commit early once one logical unit is in place. Keep commits and PRs small, single-purpose, and easy to review.
- Limit each PR to **one logical change**. Do not bundle multiple unrelated tasks into a single PR. If a task grows beyond a manageable scope, split it across multiple stacked PRs. A PR should be easy to review in a single sitting.
- Use separate git worktrees when working on multiple PRs or branches at the same time.
- Keep local caches and machine-specific state out of git.

## Pre-Merge Checklist

**MUST verify ALL of these before merging any PR. Do not merge until every item passes. This is not optional.**

| # | Check | How to verify |
|---|---|---|
| 1 | CI checks green | `gh pr view <number> --json statusCheckRollup` — all `conclusion` must be `SUCCESS` (SKIPPED for instrumented-test is OK). |
| 2 | PR Agent / bot comments | Check `gh pr view <number> --json comments` and `gh api .../pulls/<number>/reviews` — read every review thread. If there are unresolved comments with actionable feedback, reply and address them before merging. |
| 3 | Review threads resolved | Run `get_review_comments` on the PR. Every thread with a comment requesting a change must have a reply explaining the fix and be marked resolved. |
| 4 | No stale CI | If the last commit is older than the latest CI run, check that the CI run corresponds to the latest commit SHA (`gh run list --branch <branch> --limit 1`). |
| 5 | Conventional commit title | The PR title must match `type(scope): description` format (e.g. `fix: ...`, `feat(core): ...`). The `conventional-title` CI check enforces this. |

**Why this exists:** In the past, PRs were merged with unresolved bot comments, raw error messages in the UI, and skipped pre-merge review cycles. The checklist prevents these by forcing explicit verification before every merge.

### Never Do This

- Never share one active worktree across multiple active PRs.
- Never merge with anything other than squash + merge.
- Never restack or rebase another PR owner's branch without coordinator ownership in multi-PR mode.
- Never archive an OpenSpec change without user confirmation.
- Never guess when a blocker should have stopped the task.

## Commit Convention (semantic-release)

- Every commit that lands on `main` MUST follow [Conventional Commits](https://www.conventionalcommits.org/).
- This repo uses `semantic-release` which runs on every push to `main` and releases immediately — there is no release PR.
- The PR title is checked in CI (`pr-title.yml`), but the commits themselves matter too.

### Prefixes that trigger a release

| Prefix | Version bump | Example |
|--------|-------------|---------|
| `feat:` | minor (1.0.0 → 1.1.0) | `feat: add search bar` |
| `fix:` | patch (1.0.0 → 1.0.1) | `fix: crash on empty list` |
| `perf:` | patch (1.0.0 → 1.0.1) | `perf: optimize image loading` |
| `feat!:` or `fix!:` | major (1.0.0 → 2.0.0) | `feat!: drop API v1 support` |

Any prefix with `!` before `:` or a `BREAKING CHANGE:` footer triggers a major bump.

### Prefixes that do NOT trigger a release

`docs:`, `chore:`, `style:`, `refactor:`, `test:`, `ci:`, `build:`

These are safe for any push to `main` — no version will be published. However, if any of these include `BREAKING CHANGE:` in the footer, a major release WILL fire.

### Merge strategy

Use **squash + merge** for every PR. The PR title becomes the single commit on `main`, and since CI already enforces conventional-commit PR titles, this guarantees clean history that `semantic-release` can parse. Other merge methods are not permitted.

### Breaking change syntax

Append `!` before `:` for a concise breaking change:

```
feat!: drop API v1 support
```

Or use a `BREAKING CHANGE:` footer for more detail:

```
feat: migrate auth to OAuth 2.0

BREAKING CHANGE: password-based login removed
```

Both forms trigger a major version bump.

## Command And Data Hygiene

- Use `./gradlew` from the repo root, never a host-installed `gradle`.
- Run repo commands inside the pinned environment so Gradle, Git, OpenSpec, Java, Node, Bun, and Android tooling come from the flake.
- Do not commit credentials, raw auth headers, signed media URLs, browser traces, HAR files, authenticated screenshots, or other sensitive artifacts.
- Treat OpenSpec requirement statements and scenarios as authoritative. Do not infer behavior from placeholder metadata such as `Purpose: TBD`.
- **Never use the browser** for GitHub access. Always use the `gh` CLI — see `docs/agent/gh-cli.md` for a comprehensive reference.
- **Avoid overusing `@Suppress`.** Prefer fixing the root cause. Only use when the warning is a false positive with no cleaner way to silence it, and scope it to the narrowest element.

## Autonomy

- Investigate before asking: use `rg`, `rg --files`, `find`, inspect the worktree, and read the relevant `openspec/` files first.
- Act independently on implementation details, but stop when ambiguity changes the outcome, the action is irreversible, or the request conflicts with OpenSpec.
- When multiple reasonable approaches exist and the spec does not decide, choose the smallest, most reversible option and explain it in the final response.
- Drive work to a verifiable end state in one turn whenever feasible, without trading away branch, commit, or PR hygiene.

## Scope Split Examples

- `auth-persistence-shell` plus `android-ui-foundation` in one PR: stop and verify split.
- Active change spec plus canonical `openspec/specs/`: stop and verify shared OpenSpec work.
- Multi-PR task touching another PR owner's files: stop and escalate.

## Minimum Safe Behavior

If confused or blocked: do not edit more files, do not commit, do not push, and report the blocker with exact files, commands, and unclear rule.

## Completion Format

Use this exact shape in the final response:

```text
changed-files:
verification-run:
skipped-checks:
branch:
pr:
blocker:
```

## Delegation And Review

- Use `superpowers` skills as execution helpers, not as a second requirements system.
- Good fits: `writing-plans`, `test-driven-development`, `systematic-debugging`, `verification-before-completion`, `receiving-code-review`, and `subagent-driven-development`.
- Use subagents or parallel workers only when explicitly allowed and only for well-scoped tasks with disjoint write ownership.
- In multi-PR mode, prefer one subagent = one PR = one worktree.
- Open PRs ready for review unless the user explicitly asks for a draft.
- The PR title rule is enforced in CI; do not rely on memory for that check.
- If CI is green and no "changes requested" review is active, you may merge immediately without asking again. Approval is not required.
