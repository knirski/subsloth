# Publishing Checklist

Use this before creating or updating a pull request.

Type: normative policy.

1. Re-read [`AGENTS.md`](/AGENTS.md) and follow repo rules over any agent defaults.
2. Confirm the branch name, commit message, and PR title use the repo's conventional-commit naming.
3. **Before every commit, run `./gradlew check`.** Do not commit if checks fail.
4. **After every push** that changes the PR's content, update the PR title and description so they always reflect the current diff. Stale titles mislead reviewers and break `squash+merge` commit messages.
5. Open PRs ready for review unless the user explicitly asks for a draft. **After creating a PR, enter monitoring mode immediately.**
6. **Keep PRs small.** Limit each PR to one logical change. Do not bundle multiple unrelated tasks into a single PR. If a task grows beyond a manageable scope, split it across multiple stacked PRs. A PR should be easy to review in a single sitting.
7. If multiple PRs or branches need work at the same time, use separate git worktrees and one PR owner per PR.
8. Use one coordinator agent to decide PR boundaries and assign ownership. Let the PR owner handle that PR end to end.
9. Follow [`docs/agent/review-loop.md`](/docs/agent/review-loop.md) for monitoring, replies, and merge timing.
10. Report each active PR with `pr`, `branch`, `worktree`, `state`, and `blocker`.
11. Use only these `state` values: `planning`, `implementing`, `waiting-review`, `addressing-comments`, `ready-to-merge`, `merged`, `blocked`.
12. Merge when no actionable comments remain and the PR is ready.
13. Always use **squash + merge** — the PR title becomes the commit on `main`, and `semantic-release` uses it to determine the next version. No other merge method is allowed.
14. Every change to `main` MUST go through a pull request. Direct pushes to `main` are forbidden.

The PR title rule is enforced in CI, so treat it as a required check.

For commit convention rules (which prefixes trigger a release, breaking change syntax, merge strategy), see the **Commit Convention** section in [`AGENTS.md`](/AGENTS.md). The rules there are authoritative.

Verification selection:
- One active OpenSpec change: task-specific checks plus `openspec validate <change-id> --strict`.
- Shared OpenSpec content: `openspec validate --all --strict`.
- Docs-only PR work: narrowest meaningful verification; say explicitly if none exists.

Minimum safe fallback:
- Do not commit if verification is unclear.
- Do not push if PR title, branch, or base branch is unclear.
- Report the blocker with the exact missing detail.
