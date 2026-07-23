## 1. Delta specs

- [x] 1.1 Write `specs/testing-release/spec.md` renaming and rewriting "Release Please" to "Release Mechanism" describing semantic-release; drop `release-please`/`version.txt`/`CHANGELOG.md` normativity.
- [x] 1.2 Write `specs/project/spec.md` rewriting "Toolchain Baseline" to reference `gradle/wrapper/gradle-wrapper.properties`, `gradle/libs.versions.toml`, and `flake.nix` as the executable source of truth instead of literal version numbers.
- [x] 1.3 Write `specs/platform-parity/spec.md` updating the Platform Support Table's status column to point at the new readiness matrix instead of an unqualified "Supported" claim.
- [x] 1.4 Write `specs/readiness/spec.md` (new capability) with Platform Support Tiers, Readiness Checklist, and Archived Task Disposition Ledger requirements.

## 2. Archived task disposition inventory

- [x] 2.1 Enumerate every unchecked `- [ ]` item across all 27 `openspec/changes/archive/*/tasks.md` files.
- [x] 2.2 For each item, verify against current code/tests whether it is done, and record a disposition (verified complete / superseded / still required / intentionally deferred) with evidence in `docs/readiness/archived-task-disposition.md`.
- [x] 2.3 For every "still required" item, name its owning change from `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`'s delivery sequence.
- [x] 2.4 Do not edit any archived `tasks.md` checkbox.

## 3. Platform support matrix

- [x] 3.1 Write `docs/readiness/platform-support-matrix.md` with the four tiers (Android: Internal beta, Desktop: Preview, Web on GitHub Pages: Stateless demo, Web production: Not yet granted) and their promotion requirements, matching `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`'s promotion-gates table.
- [x] 3.2 For each promotion requirement, link a named CI required-check (e.g. `Android assemble and unit tests`) or a manual acceptance doc (e.g. `docs/testing/device-acceptance.md`); leave none unlinked.

## 4. Doc reconciliation

- [x] 4.1 `README.md`: replace the unqualified "✅ Production" platform support table with tier labels linking to the new matrix; fix the "Android SDK 36" prose (disambiguate compileSdk 37 vs. emulator API 36) and the "NixOS 25.05" badge (flake tracks `nixos-unstable`, not a pinned release channel).
- [x] 4.2 `docs/project-assessment.md`: correct the "v1 Released ✅ ... production-ready ... No architectural or implementation gaps block release" verdict and the Release Pipeline row's `release-please` claim; mark the document as superseded by the 2026-07-23 assessment and this remediation plan without deleting its still-accurate technical detail.
- [x] 4.3 `docs/testing/benchmarks.md`: correct the "Benchmarks run on every PR and push to main via GitHub Actions" claim — no CI workflow invokes `:benchmark`; state the actual 3/7 manual result and that no baseline profile is committed, linking to remediation-plan Change 6.
- [x] 4.4 `docs/jdk.md`: fix the stale "Kotlin 2.3.x" / "compileSdk 36" mentions to match the version catalog (or point at it instead of repeating numbers).
- [x] 4.5 `docs/release.md`: correct "sets up JDK 25 + JDK 17" — CI (`kmp-setup` composite action) only installs JDK 17.
- [x] 4.6 `openspec/README.md`: reconcile the `release-and-ci-foundation` change description, which still lists `release-please`, `version.txt`, `CHANGELOG` as what that change delivered, given the actual implementation kept semantic-release.

## 5. Verification

- [x] 5.1 `openspec validate reconcile-readiness-baseline --strict`
- [x] 5.2 `openspec validate --all --strict`
- [x] 5.3 Grep the non-archived tree for `release-please`, `version.txt`, and `production-ready` and confirm no remaining normative reference outside archived/historical paths.
- [x] 5.4 Confirm every unchecked archived task has a disposition in `docs/readiness/archived-task-disposition.md` (count matches the 56 found in step 2.1).
