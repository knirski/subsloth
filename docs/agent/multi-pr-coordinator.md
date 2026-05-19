# Multi-PR Coordinator

Use this when one change or task set needs multiple active PRs at the same time.
Do not use this when one PR is enough or when one PR owner can carry the work end to end without coordination.

Coordinator responsibilities:
1. Decide whether the work should stay as one PR or split into multiple PRs. Split only for explicit dependencies or clear review boundaries.
2. Create one branch, one worktree, and one PR owner per PR.
3. Assign each PR owner a narrow scope with disjoint write ownership.
4. Keep a compact status board for all active PRs using `pr`, `branch`, `base`, `worktree`, `state`, `blocker`, and `next-action`.
5. Use only these `state` values: `planning`, `implementing`, `waiting-review`, `addressing-comments`, `ready-to-merge`, `merged`, `blocked`.
6. Monitor overall progress across PRs, but do not push to, reply on, or merge a PR owned by another subagent.
7. Reassign or restack PRs only when scope changes or dependency order demands it.

Status board template:

```text
pr: <number-or-pending>
branch: <branch-name>
base: <main|base-branch|base-pr>
worktree: <path>
state: planning|implementing|waiting-review|addressing-comments|ready-to-merge|merged|blocked
blocker: <none-or-short-blocker>
next-action: <single next step>
```

Example:

```text
pr: 16
branch: docs/review-loop-polling-guidance
base: main
worktree: /tmp/subsloth-pr16
state: addressing-comments
blocker: none
next-action: reply to Gemini and re-run PR checks
```

PR owner required startup checklist:
1. Read `AGENTS.md` sections "Multi-PR Mode", "Required Workflow", and "Git And PR Hygiene".
2. Read `docs/agent/publishing.md`.
3. Read `docs/agent/review-loop.md`.
4. Run `git status --short`.
5. Confirm `branch` matches the assigned branch.
6. Confirm the current directory is the assigned worktree.
7. Stop and report `blocked` if any of the checks above fail.

PR owner responsibilities:
1. MUST work only in the assigned worktree and branch.
2. MUST implement, verify, publish, reply, monitor, and merge only the assigned PR.
3. MUST follow `docs/agent/publishing.md` before and after opening or updating the PR.
4. MUST follow `docs/agent/review-loop.md` once the PR exists.
5. MUST NOT push to, reply on, or merge another PR.
6. MUST stop and mark `blocked` if scope crosses into another PR.

State transitions:
- `planning`: before the first code edit
- `implementing`: code or tests in progress
- `waiting-review`: PR open, waiting on CI or reviews
- `addressing-comments`: acting on review feedback
- `ready-to-merge`: checks and reviews satisfied
- `merged`: PR merged
- `blocked`: waiting on dependency, permission, dirty worktree, or design decision

Role boundaries:

| Action | Coordinator | PR Owner |
|---|---|---|
| Split PRs | yes | no |
| Assign ownership | yes | no |
| Restack dependencies | yes | no |
| Edit assigned PR | no | yes |
| Reply on assigned PR | no | yes |
| Merge assigned PR | no | yes |

Handoff template:

```text
pr: <number-or-pending>
branch: <branch-name>
base: <main|base-branch|base-pr>
worktree: <path>
state: planning|implementing|waiting-review|addressing-comments|ready-to-merge|merged|blocked
blocker: <none-or-short-blocker>
owner: <subagent-name-or-id>
next-action: <single next step>
```

Exit conditions:
- Exit when all tracked PRs are `merged` or `blocked`.
- Exit early only if the user changes scope or collapses the PR split.

Escalation rules:
- Cross-PR write scope: stop and re-split ownership.
- Dependency between PRs: record it in `base`; mark blocked PRs as `blocked`.
- Stacked PR with open `base`: do not mark `ready-to-merge` unless the coordinator restacks it.
- Dirty assigned worktree or wrong PR base: PR owner stops and reports `blocked`.
- If the split no longer makes sense, collapse or restack it.
