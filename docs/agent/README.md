# Shared Agent Guidance

This directory is the shared source of truth for cross-agent instructions in this repo.

Always read:

1. `AGENTS.md` for repo-wide bootstrap rules.

Then route by task and stop once you have the matching docs:

1. `docs/codestyle.md` - normative policy for code changes.
2. `docs/agent/openspec-workflows.md` - execution workflow for OpenSpec tasks.
3. `docs/agent/publishing.md` - normative policy for commits and PRs.
4. `docs/agent/review-loop.md` - execution workflow for existing PRs.
5. `docs/agent/gh-cli.md` - reference only for `gh` syntax.
6. `docs/agent/multi-pr-coordinator.md` - execution workflow for multiple PRs.
7. `docs/agent/capture-workflow.md` - execution workflow for capture/export tasks.
8. `docs/agent/emulator-testing.md` - execution workflow for Android emulator and instrumented tests.
9. `docs/agent/lessons-learned.md` - hard-won API, toolchain, and CI patterns from past PRs.

If multiple docs match, read the normative policy docs first, then the execution workflow docs, then the reference docs.

Agent-specific skill files (`.codex/skills/`, `.opencode/skills/`, `.agents/skills/`) should stay thin and point back here instead of duplicating policy text.
