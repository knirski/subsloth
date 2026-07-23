# Repository Gap Remediation Plan

- Status: proposed planning document
- Assessment baseline: `origin/main` at `c9610d889879`
- PR branch base: `origin/main` at `c9610d889879` (rebase verified 2026-07-23)
- Scope: all gaps identified in the July 2026 repository assessment

## Purpose

This plan moves SubSloth from a well-structured prototype with incomplete platform
wiring to an evidence-backed, reproducible release candidate. It covers runtime
composition, architecture, tests, performance, release engineering, Nix
reproducibility, supply-chain controls, OpenSpec governance, and documentation.

The plan is intentionally split into small OpenSpec changes. Each change must use
one branch, one worktree, and one pull request. Do not start implementation from
the stale planning checkout; create every implementation branch from the current
remote default branch after confirming that the working tree is clean.

## Constraints and working rules

- Preserve unrelated user changes, including the currently deleted Media fixture
  files.
- Create and validate an OpenSpec change before modifying implementation or
  canonical specifications.
- Do not edit archived task checkboxes to make historical work appear complete.
  Record their disposition in a new, auditable change.
- Keep semantic-release as the release mechanism because it is the current
  repository policy. Reconcile stale specifications to that decision.
- Keep Android artifacts as clearly labelled internal/debug sideloads unless a
  separate product decision adds signing and public distribution.
- Keep all default test tasks offline and deterministic. Live service checks must
  be opt-in and separately reported.
- Never persist raw service credentials in web local storage.
- Run Gradle only through `./gradlew` in the pinned Nix environment.
- Archive a completed OpenSpec change only after validation passes and the user
  confirms archival.

## Target state

The remediation is complete when all of the following are true:

- Android launches through a real authenticated composition root and its primary
  flows use production adapters rather than in-memory or no-op defaults.
- Desktop has a declared support tier, real composition, working navigation, and
  a documented secure-session policy.
- Web is either an explicitly labelled stateless demo or meets the security,
  CORS, isolation-header, persistence, and deployment requirements of a
  production tier.
- Feature modules depend on domain ports rather than concrete database, network,
  preference, or media adapters.
- Default CI cannot execute credential-driven live drift tests.
- Desktop, Web, TV focus, screenshot, benchmark, and baseline-profile claims are
  enforced by executable checks.
- A release is published only after its artifacts have built and passed
  verification, and all artifacts share one version source.
- A clean Nix build with an empty Gradle home does not redownload or overwrite
  patched JavaScript toolchains.
- GitHub Actions, downloaded tools, dependencies, and secrets have explicit
  integrity and least-privilege controls.
- Canonical OpenSpec requirements, checked-in implementation, CI, and public
  documentation describe the same system.

## Decisions to adopt up front

These decisions remove ambiguity that would otherwise cause rework.

1. **Release policy:** retain semantic-release and conventional squash-merge
   titles. Remove release-please, `version.txt`, and manually maintained
   changelog requirements from the canonical specification unless they are still
   used for another documented purpose.
2. **Version source:** derive application and package versions from the release
   tag or a single generated Gradle property. Android, Desktop, Web metadata,
   archives, and release notes must consume the same value.
3. **Architecture boundary:** introduce `:core:data` as the repository and
   orchestration adapter. `:core:network` owns HTTP transport only;
   `:core:database` owns persistence only; `:core:preferences` owns settings and
   session persistence only.
4. **Feature dependencies:** features may depend on model, domain ports, and
   shared UI contracts. They must not construct or depend directly on concrete
   network, database, preferences, or platform adapters.
5. **Web tier:** GitHub Pages remains a stateless demo until a production host or
   approved backend-for-frontend can provide the required headers and safe
   authentication boundary. Demo mode must be conspicuous in UI and docs.
6. **Desktop credentials:** prefer an OS keyring-backed adapter on supported
   systems. Until its support matrix is verified, use a clearly documented
   session-only fallback and never plaintext persistent storage.
7. **Toolchain policy:** use the version catalog and wrapper files as the
   executable source of truth. Specifications should state supported constraints
   and automated consistency checks rather than duplicating versions that drift.
8. **Performance evidence:** use an emulator for required smoke checks and a
   documented physical-device run for release-candidate performance acceptance.

## Delivery sequence

| Order | OpenSpec change | Priority | Depends on | Primary outcome |
|---|---|---:|---|---|
| 0 | `reconcile-readiness-baseline` | P0 | None | One authoritative baseline and honest support tiers |
| 1 | `enforce-architecture-boundaries` | P0 | 0 | Adapter boundaries and composition contracts |
| 2 | `wire-android-production-runtime` | P0 | 1 | Real Android authentication, navigation, and data |
| 3A | `wire-desktop-production-runtime` | P1 | 1 | Real Desktop runtime and credential policy |
| 3B | `define-web-runtime-tier` | P0 | 0, 1 | Safe demo now; measurable production gate |
| 4 | `isolate-live-drift-tests` | P0 | 0 | Deterministic default test suite |
| 5 | `enforce-platform-test-gates` | P0 | 2, 3A, 3B, 4 | Meaningful Android/Desktop/Web/TV checks |
| 6 | `automate-visual-performance-gates` | P1 | 2, 5 | Screenshot, benchmark, and profile evidence |
| 7 | `fix-nix-clean-room-reproducibility` | P0 | 0 | Reproducible JS and Gradle toolchains |
| 8 | `harden-release-pipeline` | P0 | 5, 7 | Build-before-publish, shared version, provenance |
| 9 | `harden-supply-chain` | P1 | 0 | Pinned automation, verified inputs, least privilege |
| 10 | `reduce-hotspots-and-sync-docs` | P1 | 2-9 | Maintainable files and accurate user-facing status |

Changes 3A, 3B, 4, 7, and 9 can proceed in parallel after their listed
dependencies merge. Changes 2 and 3A should reuse the composition contracts from
change 1 rather than independently inventing platform graphs.

## Change 0: Reconcile readiness baseline

### Baseline scope

- Inventory every unchecked task in archived changes and classify it as:
  verified complete, superseded, still required, or intentionally deferred.
- Link every still-required item to a new change in this plan. Preserve archived
  files as historical evidence.
- Reconcile canonical specifications with the actual release workflow and
  supported toolchain:
  semantic-release instead of release-please; Gradle, AGP, Kotlin, SDK, and JDK
  values from executable configuration rather than stale prose.
- Create a platform support matrix with explicit tiers:
  Android internal beta, Desktop preview, Web demo, and the gates for promotion.
- Replace broad "production-ready" claims with evidence-backed language.
- Define a single readiness checklist that references executable CI checks.

### Baseline verification

- `openspec validate --all --strict`
- A repository search finds no normative release-please or stale toolchain
  requirement outside explicitly labelled historical archives.
- Every unchecked archived task has a disposition and, when still required, an
  owning planned change.
- The support matrix links every promotion criterion to a check or a documented
  manual acceptance record.

### Baseline exit criteria

- There is one unambiguous policy for releases, versions, toolchains, and platform
  support.
- No implementation change in this plan depends on an unresolved canonical-spec
  conflict.

## Change 1: Enforce architecture boundaries

### Architecture scope

- Add `:core:data` for repository implementations and orchestration that combine
  transport, persistence, preferences, and domain ports.
- Remove database and preferences dependencies from `:core:network`.
- Move feature-facing concrete adapter use behind domain ports.
- Remove unused feature dependencies while migrating each feature.
- Keep service DTO-to-domain mapping at the adapter boundary; keep UI error
  mapping in UI-facing modules.
- Remove Compose runtime from `:core:model`, or move stability annotations into a
  UI-owned wrapper or Compose stability configuration. If removal is not
  technically sound, update the canonical rule with a narrow, justified
  exception.
- Replace source-import-only architecture tests with Gradle dependency graph
  invariants covering every feature and core module.
- Add a Web convention plugin or extend the applicable KMP convention so
  `:webApp` receives the same Spotless and Detekt policy as other modules.
- Document ownership of composition roots and platform adapter construction.

### Architecture verification

- `openspec validate enforce-architecture-boundaries --strict`
- `./gradlew spotlessApply spotlessCheck detekt`
- `./gradlew :core:model:compileKotlinJvm :core:domain:compileKotlinJvm`
- `./gradlew :core:model:compileKotlinWasmJs :core:domain:compileKotlinWasmJs`
- Task discovery confirms that verification covers the configured JVM and Wasm
  targets without introducing an iOS target that the platform-parity
  specification explicitly exempts.
- A dependency-invariant test fails on a fixture module or test mutation that
  adds a forbidden feature-to-adapter edge.
- `./gradlew test`

### Architecture exit criteria

- Features compile without concrete database, network, preference, or platform
  adapter dependencies.
- The allowed dependency graph is executable policy, not only documentation.
- All platform composition roots can build the same domain-facing contracts.

## Change 2: Wire Android production runtime

### Android scope

- Replace the default `InMemorySessionState` with an Android session adapter and
  encrypted credential storage.
- Build authenticated HTTP clients only after credential recovery or login.
  Rebuild or invalidate authenticated clients when credentials change.
- Make failed authentication return a typed error; never accept credentials
  merely because fields are non-empty.
- Clear encrypted credentials, in-memory authentication state, and authenticated
  clients on ordinary logout while retaining non-transient active-profile and
  shared offline data.
- Apply reset-preferences, clear-watch/library, and delete-downloads cleanup only
  when the user selects each independent option. Keep other account profiles
  untouched except for intentionally shared offline-media deletion.
- Replace the nested navigation stack that starts at `LoginKey` after successful
  authentication with one authoritative authenticated start destination.
- Wire library, downloads, settings, details, and player ViewModels through the
  Android composition root; remove production use of no-op/default ViewModels.
- Implement or explicitly defer movie detail, show detail, and auth-repair routes
  through OpenSpec scenarios rather than placeholder screens.
- Add account-switch and cold-start recovery scenarios.

### Android verification

- `openspec validate wire-android-production-runtime --strict`
- Repository pre-commit Gradle check suite.
- Instrumented tests for valid login, invalid login, cold start with a session,
  expired credentials, logout, and account switching.
- Logout partition tests prove that ordinary logout retains active-profile
  preferences, watch/library records, cached catalog/detail metadata, shared
  downloads, and shared offline progress; each explicit cleanup option clears
  only its canonical scope.
- A test proves that production startup contains no in-memory session or mock
  service binding.
- Secrets and authenticated payloads do not appear in logs or captured fixtures.

### Android exit criteria

- A fresh Android install can authenticate against the configured service, enter
  the library, navigate to supported details and playback, then log out.
- Relaunch behavior matches the encrypted-session policy.
- No screen reachable in the promoted Android tier silently uses a no-op adapter.

## Change 3A: Wire Desktop production runtime

### Desktop scope

- Build a Desktop composition root using the shared domain ports and `:core:data`
  adapters.
- Replace in-memory sessions and placeholder routes with real library, details,
  downloads, settings, and player bindings.
- Implement the OS keyring adapter and enumerate supported operating systems.
- Provide session-only behavior, with visible messaging, where keyring support is
  not verified.
- Add logout, account switching, offline startup, and expired-session behavior.
- Ensure packaging metadata consumes the shared release version.

### Desktop verification

- `openspec validate wire-desktop-production-runtime --strict`
- `./gradlew :desktopApp:test`
- `./gradlew :desktopApp:packageDistributionForCurrentOS`
- Navigation tests cover every supported route.
- Credential tests prove no plaintext secret is persisted by either storage mode.

### Desktop exit criteria

- Desktop's declared preview flow is complete without placeholder navigation.
- The credential support matrix and fallback behavior match the implementation.
- Desktop tests run in CI, not only compile.

## Change 3B: Define Web runtime tier

### Web scope

- Make demo and production configurations explicit build variants or runtime
  modes. Never silently force mocks in a build labelled production.
- Keep GitHub Pages deployment in stateless demo mode and show that state in the
  UI.
- Remove any raw credential persistence from local storage. Demo mode should not
  request production service credentials.
- Write a decision record for production connectivity:
  direct service access only with verified CORS and security behavior, otherwise
  an approved backend-for-frontend.
- Select a production host capable of COOP and COEP response headers before
  claiming OPFS-backed persistent SQLite support.
- Add a deployed-header probe, IndexedDB/OPFS capability probe, and persistence
  reload scenario.
- Wire real Web ViewModels only in a production-capable mode after its
  authentication boundary is approved.

### Web verification

- `openspec validate define-web-runtime-tier --strict`
- `./gradlew :webApp:wasmJsBrowserTest`
- A deployed smoke test verifies mode labelling and, for a production candidate,
  COOP/COEP headers and reload persistence.
- Static analysis or a browser test proves credentials are absent from local
  storage.
- Docs and deployment workflow call GitHub Pages a demo unless every production
  gate passes.

### Web exit criteria

- Web cannot be mistaken for a production client while it uses mocks or lacks
  isolation headers.
- A production promotion has a concrete host, authentication design, and
  executable acceptance tests.

## Change 4: Isolate live drift tests

### Live-test scope

- Tag live service tests and move them behind a dedicated `liveDriftTest` task.
- Exclude live tags from `test`, `jvmTest`, and all default aggregate test tasks
  regardless of ambient credential variables.
- Require an explicit opt-in flag in addition to credentials.
- Report skipped live tests distinctly from passed offline tests.
- Sanitize captured fixtures and record provenance without secrets, signed URLs,
  headers, or user-specific data.
- Add a regression test that injects credential-shaped environment variables and
  proves default tests remain offline.

### Live-test verification

- `openspec validate isolate-live-drift-tests --strict`
- Run the default JVM suite with no credentials.
- Run it again with dummy credential variables and confirm the same offline test
  selection.
- Run `liveDriftTest` without opt-in and verify a safe skip or clear refusal.
- Run the live task only in an approved secret-bearing environment.

### Live-test exit criteria

- Default CI is deterministic and cannot contact a live Media service.
- Live drift failures are visible without making ordinary developer tests
  environment-dependent.

## Change 5: Enforce platform test gates

### Platform-test scope

- Run `:desktopApp:test` in CI rather than compiling Desktop only.
- Add meaningful `webApp` browser tests before treating
  `:webApp:wasmJsBrowserTest` as a gate.
- Add TV focus traversal, restoration, and remote-control interaction tests that
  consume the existing focus rule.
- Add composition-root tests that reject mock, in-memory, or no-op bindings in
  promoted variants.
- Add navigation coverage for supported destinations on each platform.
- Define required checks by affected path so platform-specific changes run the
  relevant suite without skipping cross-cutting core checks.
- Keep live drift tests outside these gates.

### Required CI matrix

| Check | Pull requests | Main/release | Failure policy |
|---|---|---|---|
| Format and Detekt | All | All | Required |
| Core KMP compilation | All | All | Required |
| JVM unit tests | All | All | Required, offline |
| Android assemble and unit tests | Relevant or shared code | All | Required |
| Android instrumented smoke | Relevant or shared code | All | Required; documented infrastructure skip only |
| Desktop tests | Relevant or shared code | All | Required |
| Web browser tests | Relevant or shared code | All | Required |
| TV focus tests | TV/UI/navigation changes | All | Required |
| Live drift tests | Never by default | Scheduled/manual | Non-blocking signal with alerts |

### Platform-test verification

- `openspec validate enforce-platform-test-gates --strict`
- Each new CI job is demonstrated failing on a controlled test mutation before
  the change merges.
- Required-check names are documented and protected on the default branch.
- CI logs show that no required offline task selects live-tagged tests.

### Platform-test exit criteria

- Every advertised platform has meaningful executable coverage.
- A compile-only or empty test task cannot satisfy a readiness claim.

## Change 6: Automate visual and performance gates

### Visual and performance scope

- Run screenshot comparison for UI-affecting pull requests and make intentional
  golden updates explicit review artifacts.
- Add benchmark smoke checks to CI, with stable emulator configuration and
  machine-readable thresholds appropriate for regression detection.
- Generate and commit the Android baseline profile.
- Add a profile freshness check tied to benchmark or startup-critical source
  changes.
- Separate emulator smoke evidence from physical-device release acceptance.
- Record device model, OS version, build, scenario, and result for
  release-candidate measurements.
- Reconcile benchmark documentation with the actual seven-test status; do not
  claim completion while only three tests pass.

### Visual and performance verification

- `openspec validate automate-visual-performance-gates --strict`
- Screenshot job detects a controlled visual change.
- Benchmark smoke job runs on the same build type used by its documented claim.
- Baseline-profile generation produces a non-empty committed profile consumed by
  the Android release configuration.
- A release-candidate checklist links to physical-device evidence.

### Visual and performance exit criteria

- Screenshot, benchmark, and baseline-profile statements are backed by current
  CI or release-candidate evidence.
- Visual and performance regressions cannot be hidden behind manual-only docs.

## Change 7: Fix Nix clean-room reproducibility

### Nix scope

- Configure the Kotlin Gradle plugin to use Nix-provided Node, Yarn, and Binaryen
  before JavaScript tasks are registered.
- Remove the shell-hook strategy that patches a downloaded Gradle toolchain which
  Gradle can later re-extract and overwrite.
- Add a clean-room check with an empty temporary `GRADLE_USER_HOME`.
- Align local Nix and CI JDK policy, distinguishing the Gradle runtime JDK from
  Java bytecode/toolchain targets.
- Declare supported flake systems explicitly. Either add tested systems or narrow
  documentation to `x86_64-linux`.
- Make `nix flake check` build at least one representative derivation in CI;
  evaluation-only `--no-build` is not sufficient evidence.

### Nix verification

- `openspec validate fix-nix-clean-room-reproducibility --strict`
- `nix flake check`
- A clean environment runs the Web browser test with an empty Gradle home and no
  generic Node extraction.
- CI and the development shell report compatible Gradle runtime/toolchain values.
- `nix flake show --json` matches the documented system support.

### Nix exit criteria

- Web and Gradle tasks succeed from a clean Nix environment without mutating or
  patching downloaded toolchains.
- Supported systems and JDK roles are explicit and tested.

## Change 8: Harden release pipeline

### Release scope

- Build, test, package, and checksum artifacts before creating or publishing a
  GitHub release.
- Publish atomically from immutable staged artifacts. A failed build must not
  leave a successful tag/release with missing assets.
- Feed Android, Desktop package metadata, Web metadata, archive names, and release
  notes from the shared version source.
- Label debug sideload artifacts as internal and non-production.
- Add checksums and an SBOM or equivalent dependency inventory to the release.
- Verify that every uploaded artifact was built from the tagged commit.
- Document retry, failed-draft cleanup, and rollback behavior.

### Release verification

- `openspec validate harden-release-pipeline --strict`
- Action workflow linting.
- A dry run verifies version equality across all packages and filenames.
- A controlled packaging failure proves no published release is created.
- A successful release rehearsal verifies checksums and commit provenance.

### Release exit criteria

- Publication is the final step after all required evidence exists.
- No package has a hard-coded version such as Desktop `1.0.0`.
- Release scope and distribution limitations are explicit.

## Change 9: Harden supply chain

### Supply-chain scope

- Pin third-party GitHub Actions to commit SHAs, retaining readable version
  comments and automated update support.
- Obtain Vacuum through Nix or verify its downloaded archive with a pinned
  checksum.
- Add Gradle dependency verification metadata and a documented locking policy for
  configurations where locks are practical.
- Commit the Kotlin/JS `kotlin-js-store/yarn.lock`, stop ignoring it, and configure
  CI to fail on lock mismatch or automatic replacement. The lock must record the
  local worker resolution and include integrity hashes for
  `@sqlite.org/sqlite-wasm` and every transitive registry artifact used by the
  Web build.
- Align duplicate `@sqlite.org/sqlite-wasm` declarations to one version and make
  dependency updates regenerate and review the committed lockfile explicitly.
- Add gitleaks to the default development shell or expose it as a flake app from
  the repository's locked `nixpkgs` input. CI must invoke that pinned flake
  package rather than an indirect registry reference.
- Run gitleaks in CI with a narrow, reviewed allowlist; retain targeted custom
  checks only when they cover repository-specific formats.
- Reduce workflow permissions to the minimum per job.
- Remove unnecessary write permission and broad token exposure from the PR-agent
  workflow; isolate any third-party action that receives a token.
- Inventory prerelease dependencies. Upgrade stable replacements or create a
  time-bounded, owner-labelled exception for each remaining alpha, beta, or RC.
- Ensure logs and artifacts cannot contain credentials, authorization headers,
  signed media URLs, browser traces, HAR files, or authenticated screenshots.

### Supply-chain verification

- `openspec validate harden-supply-chain --strict`
- Action workflow linting passes.
- `nix develop .# -c gitleaks detect --source . --no-git --verbose`
- Gradle dependency verification rejects a controlled checksum mismatch.
- A clean Web dependency resolution succeeds with the committed frozen Yarn lock,
  while a controlled npm version or integrity mismatch fails.
- The gitleaks binary resolves through `flake.lock`; CI contains no
  `nixpkgs#gitleaks` registry invocation.
- A policy check rejects an unpinned action and an unapproved prerelease
  dependency.
- Workflow permission review shows no job has unused write access.

### Supply-chain exit criteria

- Every executable CI input has a verified identity or integrity check.
- Dependency and prerelease exceptions are visible, owned, and enforceable.
- Secrets receive prevention controls, not only documentation.

## Change 10: Reduce hotspots and synchronize documentation

### Maintainability scope

- Refactor the largest UI and mapping files by responsibility while preserving
  behavior:
  `SeriesDetailScreen`, `DownloadsScreen`, `PlayerScreen`,
  `MovieDetailScreen`, `PlayerViewModel`, `WebNavHost`, `SettingsScreen`, and the
  large mapper.
- Extract state reducers, pure mapping functions, reusable sections, and
  navigation adapters before splitting files mechanically.
- Add focused tests around extracted pure behavior and state transitions.
- Create one generated or centrally maintained platform-readiness matrix.
- Make README, project assessment, known gaps, benchmark docs, release docs, and
  OpenSpec link to that matrix rather than duplicating status claims.
- Convert `docs/known-gaps.md` into an active risk register with owner, severity,
  mitigation, verification, and closure evidence. Move resolved history to a
  clearly labelled archive.
- Add documentation checks for broken internal links, stale module names,
  conflicting support tiers, and forbidden production-ready wording when gates
  are unmet.

### Maintainability verification

- `openspec validate reduce-hotspots-and-sync-docs --strict`
- Markdown lint and internal-link checks pass.
- Extracted reducers and mappers have focused unit tests.
- A repository search finds no conflicting platform or test-readiness claims.
- The risk register contains only active risks; closed entries link to evidence.

### Maintainability exit criteria

- High-change files have smaller, testable responsibilities.
- A status change is made once and reflected everywhere.
- Public documentation describes the released behavior and its limitations.

## Gap-to-change coverage

| Assessed gap | Owning change(s) |
|---|---|
| Android in-memory session, unauthenticated client, no-op ViewModels | 1, 2 |
| Authenticated flow starts another login navigation stack | 2, 5 |
| Desktop placeholders and in-memory session | 1, 3A, 5 |
| Web forced mocks, unsafe production claim, missing headers | 3B, 5, 7 |
| Live tests selected by ambient credentials | 4 |
| Desktop tests not run; Web suite empty; TV focus rule unused | 5 |
| Screenshot testing is manual | 6 |
| Benchmark claims exceed passing tests; no baseline profile | 6 |
| Fifty-six unchecked tasks in archived changes | 0 |
| Canonical release and toolchain specs conflict with implementation | 0, 8 |
| Release published before artifact build; Desktop version hard-coded | 8 |
| Nix JavaScript toolchain patch is overwritten; evaluation-only check | 7 |
| Flake system and JDK support mismatch | 7 |
| Unverified downloads, npm artifacts, mutable actions, missing dependency verification | 9 |
| Limited secret scanning and excessive workflow permissions | 9 |
| Prerelease dependency policy conflicts with six catalog entries | 9 |
| Feature-to-adapter dependency leakage and network orchestration | 1 |
| Import-only architecture tests and model Compose dependency | 1 |
| Web bypasses shared quality convention | 1 |
| Large UI/ViewModel/mapper files | 10 |
| README, assessment, gaps, benchmark, and release docs overclaim | 0, 10 |
| Web and Desktop credential-storage policy is incomplete | 3A, 3B, 9 |

## Platform promotion gates

| Platform | Current planned tier | Promotion requirements |
|---|---|---|
| Android | Internal beta | Change 2 complete; required platform tests green; baseline profile consumed; release pipeline hardened |
| Desktop | Preview | Real composition and routes; Desktop tests required; keyring/session policy verified on every supported OS |
| Web on GitHub Pages | Stateless demo | Explicit mock/demo labelling; no credential persistence; meaningful browser smoke tests |
| Web production | Not yet granted | Approved auth/CORS architecture; COOP/COEP host; persistence reload test; real adapters; production browser suite |

No documentation may promote a platform merely because it compiles or renders a
shell. Promotion requires all corresponding gates and linked evidence.

## Pull request protocol

For every change:

1. Synchronize the remote default branch and create a dedicated worktree and
   conventional branch.
2. Create the OpenSpec proposal, design, delta specs, and ordered tasks.
3. Validate the change strictly before implementation.
4. Implement only that change's scope; record newly discovered cross-cutting work
   as a follow-up instead of expanding the pull request.
5. Run the task-specific checks plus repository-required checks.
6. Update task checkboxes only when evidence exists.
7. Open a conventional-title pull request and wait for required CI.
8. Read, answer, and resolve every actionable review thread.
9. Squash-merge only when CI is current and green and no changes-requested review
   remains.
10. Archive only after completion, validation, and explicit user confirmation.

## Definition of done for the program

- All twelve changes are merged or explicitly superseded by an approved design.
- Every active or historical gap in the coverage table has closure evidence.
- `openspec validate --all --strict` passes.
- The default offline CI matrix passes from a clean checkout.
- Android, Desktop, Web demo, and any promoted Web production tier pass their
  acceptance gates.
- The release rehearsal produces version-consistent artifacts, checksums, and
  provenance without prematurely publishing.
- The Nix clean-room test succeeds with an empty Gradle home.
- Supply-chain policy checks pass without broad exceptions.
- Documentation status, canonical specs, CI, and runtime behavior agree.
- Remaining risks are explicit, owned, and do not contradict the advertised
  support tier.

## First action

Start with `reconcile-readiness-baseline`. It is the only safe first
implementation change because it resolves the specification conflicts and support
tier definitions that determine the acceptance criteria for every later pull
request.
