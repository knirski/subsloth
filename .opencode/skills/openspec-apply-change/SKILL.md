---
name: openspec-apply-change
description: Implement tasks from an OpenSpec change using the shared repo workflow.
license: MIT
compatibility: Requires openspec CLI.
metadata:
  source: docs/agent/openspec-workflows.md
---

Use `docs/agent/openspec-workflows.md` as the canonical workflow reference for this skill.
Follow the repo's `AGENTS.md` and `openspec/README.md` first, then execute the active change with `openspec status --change "<change>" --json` and `openspec instructions apply --change "<change>" --json`.
Keep the active change's `tasks.md` as the source of task progress, and update checkboxes as work is completed.
