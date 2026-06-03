# Multi-PR Coordinator

Use when one change or task set needs multiple active PRs. Not for single-PR work.

## Coordinator Responsibilities

1. Decide whether to split. Split only for explicit dependencies or clear review boundaries.
2. Create one branch + worktree + PR owner per PR.
3. Assign each owner narrow scope with disjoint write ownership.
4. Maintain compact status board for all active PRs.
5. Use state values: `planning`, `implementing`, `waiting-review`, `addressing-comments`, `ready-to-merge`, `merged`, `blocked`.
6. Monitor overall progress. Do NOT push to, reply on, or merge another owner's PR.
7. Reassign/restack only when scope changes or dependency order demands it.

## Status Board Template

```text
pr: <number-or-pending>
branch: <branch-name>
base: <main|base-branch|base-pr>
worktree: <path>
state: planning|implementing|waiting-review|addressing-comments|ready-to-merge|merged|blocked
blocker: <none-or-short-blocker>
next-action: <single next step>
```

## PR Owner Startup

1. Read AGENTS.md sections "Multi-PR Mode", "Workflow"
2. Read `docs/agent/publishing.md` and `docs/agent/review-loop.md`
3. `git status --short`. Confirm branch and worktree.
4. Stop and report `blocked` if any check fails.

## PR Owner Responsibilities

- Work only in assigned worktree and branch
- Implement, verify, publish, reply, monitor, merge only the assigned PR
- Follow `docs/agent/publishing.md` and `docs/agent/review-loop.md`
- MUST NOT touch another PR
- Stop and mark `blocked` if scope crosses into another PR

## State Transitions

`planning` → `implementing` → `waiting-review` → `addressing-comments` → `ready-to-merge` → `merged` (or `blocked` at any point)

## Role Boundaries

| Action | Coordinator | PR Owner |
|---|---|---|
| Split PRs | yes | no |
| Assign ownership | yes | no |
| Restack deps | yes | no |
| Edit/reply/merge assigned PR | no | yes |

## Handoff Template

```text
pr: <number>
branch: <branch-name>
base: <main|base-pr>
worktree: <path>
state: <state>
blocker: <none|blocker>
owner: <subagent-name>
next-action: <single next step>
```

## Exit Conditions

- Exit when all PRs are `merged` or `blocked`.
- Exit early only if user changes scope or collapses the split.

## Escalation

- Cross-PR write scope → stop, re-split ownership
- Dependency between PRs → record in `base`; mark blocked PRs as `blocked`
- Stacked PR with open `base` → don't mark ready-to-merge unless coordinator restacks
- Dirty worktree or wrong base → PR owner stops, reports `blocked`
- Split no longer makes sense → collapse or restack
