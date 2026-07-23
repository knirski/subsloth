# readiness Specification (delta)

## ADDED Requirements

### Requirement: Platform Support Tiers
The project SHALL maintain a single platform support matrix at `docs/readiness/platform-support-matrix.md` that assigns each platform target an explicit release-readiness tier and states the promotion requirements for the next tier. `docs/readiness/platform-support-matrix.md` is authoritative: other documents MAY show a brief tier label as a linked summary (e.g. a table cell reading "See the readiness matrix — Internal beta"), but MUST NOT state a tier, promotion criterion, or readiness judgment that the matrix does not also state, and MUST link to the matrix wherever a tier label appears. No documentation SHALL claim a platform is production-ready, fully supported without qualification, or gate-free based solely on the fact that it compiles or renders a shell.

#### Scenario: A reader wants a platform's current status
- **WHEN** a developer or reviewer wants to know a platform's current release readiness
- **THEN** `docs/readiness/platform-support-matrix.md` is the single authoritative source, and `README.md`, `docs/project-assessment.md`, and the canonical OpenSpec specs either link to it without restating a tier or show only a brief, linked tier label that matches it exactly

#### Scenario: A linked summary drifts from the matrix
- **WHEN** a document's linked tier label no longer matches the tier recorded in `docs/readiness/platform-support-matrix.md`
- **THEN** the document is corrected to match the matrix; the matrix is never corrected to match a stale summary

#### Scenario: A promotion criterion is marked complete
- **WHEN** a promotion requirement in the matrix is marked complete
- **THEN** it links to a named, passing CI check or a dated manual acceptance record; a criterion without either remains open

### Requirement: Readiness Checklist
The platform support matrix SHALL include a readiness checklist mapping each promotion gate to a concrete, currently-runnable command or CI required-check name, or SHALL mark it "no automated check yet" together with the remediation-plan change that is expected to add one.

#### Scenario: A checklist item is verified
- **WHEN** a reader wants to verify a readiness checklist item
- **THEN** the item names an exact command (e.g. `./gradlew spotlessCheck`) or CI job/required-check name, not a vague description of intended coverage

### Requirement: Archived Task Disposition Ledger
Every unchecked task checkbox found in an archived OpenSpec change SHALL have a recorded disposition in `docs/readiness/archived-task-disposition.md`: verified complete, superseded, still required, or intentionally deferred. A "still required" disposition SHALL name its owning planned or active OpenSpec change. Archived `tasks.md` files SHALL NOT be edited to mark historical work complete; dispositions are recorded only in the ledger.

#### Scenario: A reviewer finds an unchecked archived task
- **WHEN** a reviewer reads an archived change's `tasks.md` and finds an unchecked item
- **THEN** `docs/readiness/archived-task-disposition.md` lists that exact item with its disposition and supporting reasoning

#### Scenario: A recorded disposition is later found wrong
- **WHEN** new evidence contradicts a disposition already recorded in the ledger
- **THEN** the ledger entry is corrected in place; the archived `tasks.md` file is not retroactively checked off
