## Why

The July 2026 repository assessment (`docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`) found that the canonical OpenSpec baseline, README, and project-assessment docs describe a different system than what is actually built, tested, and running in CI:

- `openspec/specs/testing-release/spec.md`'s "Release Please" requirement still mandates `release-please`, `version.txt`, and a maintained `CHANGELOG.md`, but the repository has run `semantic-release` since commit `adf583d` (PR #5) and `docs/release.md` correctly describes that mechanism. No `version.txt` or `CHANGELOG.md` exists anywhere in the non-archived tree.
- `openspec/specs/project/spec.md`'s "Toolchain Baseline" requirement hard-codes Gradle 9.5 / AGP 9.2 / Kotlin 2.3 / `compileSdk 36` / `targetSdk 36`, all of which have drifted from the real version catalog (Gradle 9.6.1, AGP 9.3.0, Kotlin 2.4.10, `compileSdk`/`targetSdk` 37 — see `gradle/wrapper/gradle-wrapper.properties` and `gradle/libs.versions.toml`). The same stale numbers are duplicated in `docs/jdk.md`. `docs/release.md` additionally states CI sets up "JDK 25 + JDK 17", but `.github/actions/kmp-setup/action.yml` only installs JDK 17.
- `openspec/specs/platform-parity/spec.md` and `README.md` both claim unqualified "Supported, parity required" / "✅ Production" status for Android, Desktop, and Web, and `docs/project-assessment.md` declares "v1 Released ✅ ... production-ready ... No architectural or implementation gaps block release." This contradicts known, already-diagnosed gaps: Android's in-memory session and no-op ViewModels, Desktop's placeholders, Web's forced mocks and missing COOP/COEP isolation headers (confirmed absent from the GitHub Pages deploy), Desktop tests never running in CI (`ci.yml` only compiles `:desktopApp`), and an empty `:webApp` test suite passing vacuously.
- `docs/testing/benchmarks.md` claims benchmarks "run on every PR and push to main via GitHub Actions," but no CI workflow invokes the `:benchmark` module; the only recorded run is manual/local and passed 3 of 7 scenarios, with no baseline profile committed anywhere.
- 56 task checkboxes across 8 archived OpenSpec changes were never ticked, and nothing records whether each is done, stale, or a real gap — so a reader cannot tell resolved bookkeeping from live work.

Every later change in the remediation plan depends on one unambiguous, evidence-backed statement of what is actually true today. This change (`reconcile-readiness-baseline`, Change 0 of that plan) resolves those conflicts before any other implementation change starts.

## What Changes

- Rename and rewrite `testing-release`'s release requirement to describe the real `semantic-release` mechanism (tag-derived version, no `CHANGELOG.md`); drop `release-please`/`version.txt` as normative requirements.
- Rewrite `project`'s "Toolchain Baseline" requirement so `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`, and `flake.nix` are the stated executable source of truth, instead of duplicated literal version numbers that drift.
- Update `platform-parity`'s Platform Support Table to point at a new readiness matrix for release-tier status, instead of restating an unqualified "Supported"/"Production" claim.
- Add a new `readiness` capability spec that defines platform support tiers with promotion gates, a readiness checklist tied to concrete CI checks, and a requirement that every unchecked archived task carry a recorded disposition.
- Add `docs/readiness/platform-support-matrix.md` (tiers, promotion gates, checklist mapped to named CI jobs or manual acceptance docs) and `docs/readiness/archived-task-disposition.md` (disposition of all 56 unchecked archived tasks: 50 verified complete, 1 superseded, 3 still required and linked to their owning change in the remediation plan, and 2 intentionally deferred pending infrastructure that no planned change currently adds).
- Correct overclaiming and stale-toolchain language in `README.md`, `docs/project-assessment.md`, `docs/testing/benchmarks.md`, `docs/jdk.md`, `docs/release.md`, and `openspec/README.md`.

## Capabilities

### Modified Capabilities

- `testing-release`: replaces the release-please/version.txt/CHANGELOG.md requirement with the actual semantic-release mechanism.
- `project`: replaces hard-coded toolchain version numbers with a reference to the executable source of truth.
- `platform-parity`: points platform release-tier status at the new readiness matrix instead of an unqualified "Supported" claim; the feature-parity and platform-existence requirements are unchanged.

### Added Capabilities

- `readiness`: platform support tiers, promotion gates, the readiness checklist, and the archived-task disposition ledger.

## Impact

- No production code changes. Affects `openspec/specs/` (via this change's delta specs, promoted on archive), `openspec/README.md`, `README.md`, `docs/project-assessment.md`, `docs/testing/benchmarks.md`, `docs/jdk.md`, `docs/release.md`, and two new files under `docs/readiness/`.
- Does not implement any of the flagged gaps themselves (Android/Desktop/Web runtime work is owned by Changes 1-3B; live-drift isolation by Change 4; platform test gates by Change 5; screenshot/benchmark automation by Change 6; Nix reproducibility by Change 7; release pipeline hardening by Change 8; supply-chain hardening by Change 9).
- Does not convert `docs/known-gaps.md` into a full risk register or refactor large files — both are explicitly Change 10's scope.
- Risk: low. This is a documentation/specification reconciliation change with no runtime behavior change; the main risk is scope creep into fixing the gaps themselves, which this change deliberately avoids.
