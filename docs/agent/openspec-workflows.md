# OpenSpec Workflows

This document is the shared implementation reference for OpenSpec-driven work in this repository.
Keep the per-agent skill files thin and point them here.

| Workflow | Use when | Required action |
|---|---|---|
| `apply-change` | implementing an active change | Read `AGENTS.md`, `openspec/README.md`, the change `proposal.md`, `design.md`, `specs/`, and `tasks.md`; run `openspec status --change "<change>" --json` and `openspec instructions apply --change "<change>" --json`; update tasks in order; stop if blocked |
| `archive-change` | all tasks are done and user wants archive | verify completion; run the relevant validation; run `openspec archive <change-id>`; confirm promotion into `openspec/specs/` |
| `bulk-archive-change` | several completed changes are ready | confirm each change is complete and validated; archive the narrowest set that matches the request |
| `continue-change` | a change exists but needs the next artifact or step | inspect current status first; create only the next missing artifact; stop for user review if required |
| `explore` | requirements need clarification before implementation | read repo context; compare options; do not write implementation code |
| `ff-change` | the user wants artifact creation fast-forwarded | generate the required artifacts in one pass; validate the result |
| `new-change` | starting a brand-new change | capture the problem and create the initial artifacts |
| `onboard` | explaining OpenSpec workflow | explain the artifact lifecycle and verify/archive loop |
| `propose` | the user wants a full proposal with design and tasks | produce proposal artifacts before implementation |
| `sync-specs` | delta specs need promotion into canonical specs | update only the shared spec baseline |
| `verify-change` | checking implementation against active artifacts | run the verification commands from the change tasks and report mismatches |
