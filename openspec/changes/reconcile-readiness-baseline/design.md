## Context

The 2026-07-23 repository assessment found that canonical specs, README, and project-assessment docs each tell a different story about release mechanism, toolchain versions, and platform readiness than the executable repository state. `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md` schedules 11 changes to close the gaps; this one (Change 0) is listed first because every later change's acceptance criteria depend on there being one non-contradictory baseline to implement against.

## Goals / Non-Goals

Goals:

- One unambiguous policy for releases, versions, toolchains, and platform support, stated once and referenced everywhere else.
- Every one of the 56 unchecked archived-task checkboxes has a recorded disposition; each "still required" item names its owning change.
- The new platform support matrix links every promotion criterion to a named, currently-runnable check or a documented manual acceptance record — never a vague "should have tests."
- No two current, non-archived documents make contradictory claims about the same fact (release mechanism, toolchain version, or platform status).

Non-Goals:

- Do not implement any of the flagged runtime gaps (Android session/ViewModels, Desktop composition, Web isolation headers, live-drift isolation, platform test gates, screenshot/benchmark automation, Nix reproducibility, release pipeline hardening, supply-chain hardening) — each has its own owning change.
- Do not convert `docs/known-gaps.md` into a full risk register with owner/severity/mitigation/closure columns — that restructuring, and consolidating status docs to link to one generated matrix, is Change 10's scope.
- Do not build new CI tooling that automatically checks documentation for drift — Change 10 adds documentation checks; this change only fixes the drift that exists today.
- Do not edit archived `tasks.md` checkboxes to mark historical work complete — dispositions are recorded in a new ledger, not by rewriting history.
- Do not run `openspec archive` on this change — archival (promoting these deltas into `openspec/specs/`) happens only after PR merge and explicit user confirmation, per the remediation plan's pull request protocol.

## Decisions

- **Keep semantic-release.** Per the remediation plan's adopted decision, remove `release-please`/`version.txt`/`CHANGELOG.md` normativity from the canonical spec rather than switching the actual mechanism. `docs/release.md` already accurately describes the wired-up mechanism and is the source the spec is reconciled toward.
- **Toolchain baseline references the executable source, not literal numbers.** `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`, and `flake.nix`'s JDK roles become the stated source of truth. Prose docs may describe current values for readability but are corrected toward the catalog/wrapper whenever they drift, rather than the spec duplicating digits that go stale again (plan decision 7).
- **New `readiness` capability, not an extension of `platform-parity` or `project`.** `platform-parity` governs functional equivalence across platforms (a different axis — a platform can have full UI parity while still being an unauthenticated demo). `project` governs the build/toolchain baseline. Release-tier gating (Internal beta / Preview / Stateless demo / Not yet granted) is a distinct governance concern and gets its own spec so it can evolve (e.g. under Change 10's automation) without repeatedly editing the other two.
- **Tiers adopted verbatim from the remediation plan's promotion-gates table:** Android = Internal beta, Desktop = Preview, Web on GitHub Pages = Stateless demo, Web production = Not yet granted. This change does not re-derive tiers; it formalizes the ones the plan already assigned based on the assessment.
- **Disposition ledger lives outside the OpenSpec change folder**, at `docs/readiness/archived-task-disposition.md`, so it can be corrected in place if a disposition later proves wrong, without requiring a new OpenSpec change each time.
- **Disposition outcome for the 56 unchecked archived tasks** (see the ledger for full detail and evidence):
  - 50 items across `feature-session-port` (6), `refactor-error-shape` (16), `feature-login-gate-navigation` (9), `refactor-fc-is-harden` (17), and `catalog-details` (2) are **verified complete** — the described code, tests, and verification commands exist in the current tree; the checkboxes were simply never ticked before archive.
  - 2 items in `library-settings-diagnostics` (TV Downloads focus/layout, and the TV focus test that depends on it) are **still required** — no TV-specific focus/layout code exists in `DownloadsScreen`, and the TV focus harness is unused in production. Owned by remediation-plan Change 5 (`enforce-platform-test-gates`).
  - 1 item in `verification-release` (run local live-drift tests only with local credentials) is **still required** in substance — the mechanism exists (`ApiLiveDriftTest` gates on env vars via `assumeTrue`) but uses the same ambient-credential pattern the assessment flags as a live-test isolation gap. Owned by Change 4 (`isolate-live-drift-tests`). Its sibling item (re-running `openspec validate` at archive time) is bookkeeping, recorded as superseded.
  - 2 items in `android-ui-foundation` (TV emulator smoke test, process-death restoration smoke test) are **intentionally deferred** — no CI TV emulator or process-death harness exists today, and none of the remediation plan's changes add one; they remain manual per `docs/testing/device-acceptance.md` until a future change proposes that infrastructure.

## Risks / Trade-offs

- Marking 2 archived tasks "intentionally deferred" with no owning change risks them being forgotten. Mitigation: the disposition ledger states the re-review trigger explicitly (revisit if Change 5 or Change 6 adds Android TV CI infrastructure) so a future reader knows when to reopen them.
- Rewriting `docs/project-assessment.md`'s verdict risks looking like it erases a historical record. Mitigation: the document's status header is corrected in place to mark it superseded by the 2026-07-23 assessment and this remediation plan, not deleted — its architectural/technical detail sections that are still accurate are left intact.

## Migration Plan

1. Write the `testing-release`, `project`, and `platform-parity` delta specs and the new `readiness` delta spec under `openspec/changes/reconcile-readiness-baseline/specs/`.
2. Write `docs/readiness/platform-support-matrix.md` and `docs/readiness/archived-task-disposition.md`.
3. Fix doc drift in `README.md`, `docs/project-assessment.md`, `docs/testing/benchmarks.md`, `docs/jdk.md`, `docs/release.md`, and `openspec/README.md`.
4. Run `openspec validate reconcile-readiness-baseline --strict` and `openspec validate --all --strict`.
5. Grep the non-archived tree for `release-please`, `version.txt`, `production-ready`, and stale toolchain numbers to confirm no normative reference remains outside archived paths.

## Open Questions

- None blocking. Whether the platform support matrix should later be generated (rather than hand-authored) is left to Change 10, which already scopes "one generated or centrally maintained platform-readiness matrix."
