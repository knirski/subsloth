# OpenSpec Workflows

Shared implementation reference for OpenSpec-driven work. Keep per-agent skills thin and point here.

| Workflow | Use when | Required action |
|---|---|---|
| `apply-change` | implementing active change | Read AGENTS.md, openspec/README.md, change proposal/design/specs/tasks; run `openspec status --change "<change>" --json` and `openspec instructions apply --change "<change>" --json`; update tasks in order; stop if blocked |
| `archive-change` | all tasks done, user wants archive | Verify completion, run validation, run `openspec archive <change-id>`, confirm promotion to openspec/specs/ |
| `bulk-archive-change` | several completed changes ready | Confirm each complete and validated; archive narrowest matching set |
| `continue-change` | change exists but needs next artifact | Inspect current status, create only the next missing artifact; stop for review if needed |
| `explore` | requirements need clarification | Read repo context, compare options; no implementation code |
| `ff-change` | user wants fast-forward artifact creation | Generate all required artifacts in one pass, validate |
| `new-change` | starting a brand-new change | Capture problem, create initial artifacts |
| `onboard` | explaining OpenSpec workflow | Explain artifact lifecycle and verify/archive loop |
| `propose` | user wants full proposal with design and tasks | Produce proposal artifacts before implementation |
| `sync-specs` | delta specs need promotion to canonical | Update only the shared spec baseline |
| `verify-change` | checking implementation against artifacts | Run verification commands from change tasks, report mismatches |
