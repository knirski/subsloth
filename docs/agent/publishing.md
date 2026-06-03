# Publishing Checklist

Type: normative policy. Use before creating or updating a PR.

1. Re-read `AGENTS.md` — repo rules override agent defaults.
2. Confirm branch name, commit message, and PR title use conventional-commit naming.
3. Before every commit: `./gradlew check`.
4. After every push that changes PR content, update PR title and description to reflect current diff. Stale titles mislead reviewers and break squash+merge.
5. Open PRs ready for review (not draft) unless user requests it. Enter monitoring mode immediately.
6. Keep PRs to one logical change. Split large tasks across stacked PRs.
7. Multiple PRs → separate worktrees, one owner per PR.
8. One coordinator decides PR boundaries and ownership. PR owner handles their PR end to end.
9. Follow `docs/agent/review-loop.md` for monitoring, replies, and merge timing.
10. Report each active PR with `pr`, `branch`, `worktree`, `state`, `blocker`.
11. State values: `planning`, `implementing`, `waiting-review`, `addressing-comments`, `ready-to-merge`, `merged`, `blocked`.
12. Merge when no actionable comments remain and PR is ready.
13. Squash+merge only — PR title becomes commit on main, semantic-release uses it. No other merge method.
14. Every change to main MUST go through a PR. Direct pushes to main are forbidden.

## Multi-PR Merge Order

When PRs touch overlapping files, merge from smallest scope to largest:

1. Isolated changes first (single-file cleanups, no cross-module impact)
2. Foundation PRs (new types, deps, convention plugin changes)
3. Consumer PRs (code using foundation changes)

After each merge, rebase remaining PRs onto updated main:

```bash
git checkout <branch> && git pull --rebase origin main
```

Conflict resolution: when two PRs add different entries to `libs.versions.toml`, keep both entries — never drop one.

Only merge next PR when its CI is green after rebase. Do not merge PRs with unresolved conflicts against main.

PR title rule is enforced in CI — treat as required check.

## Verification Selection

- One active OpenSpec change: task-specific checks + `openspec validate <change-id> --strict`
- Shared OpenSpec content: `openspec validate --all --strict`
- Docs-only PR: narrowest meaningful verification; say explicitly if none exists
- Minimum safe fallback: do not commit if verification unclear. Do not push if PR title, branch, or base is unclear. Report blocker with exact detail.
