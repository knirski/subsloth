# Platform Support Matrix

This is the single authoritative statement of each platform's release-readiness tier, required by the `readiness` OpenSpec capability. `README.md`, `docs/project-assessment.md`, and the canonical `platform-parity` spec link here rather than restating platform status independently. No document may claim a platform is production-ready, fully supported without qualification, or gate-free based only on the fact that it compiles or renders a shell.

Tiers are adopted from `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`'s promotion-gates table and are not re-derived here.

Last reconciled: 2026-07-23, against `origin/main` at `d51dbec`.

## Tiers

| Platform | Current tier | Promotion requirement | Owning change | Evidence today |
|---|---|---|---|---|
| Android | **Internal beta** | Change 2 (`wire-android-production-runtime`) complete: real session/credential adapter, authenticated clients, wired ViewModels, single authenticated start destination | Change 2 | `InMemorySessionState` is still the wired default; navigation restarts a `LoginKey` stack after authentication (see remediation plan gap table) |
| Android | Internal beta → next tier | Required platform tests green (Change 5) | Change 5 | CI job "📱 Instrumented" runs; TV focus traversal tests do not exist yet (harness unused — see disposition ledger) |
| Android | Internal beta → next tier | Baseline profile consumed by release build (Change 6) | Change 6 | No `baseline-prof.txt` is committed anywhere in the tree |
| Android | Internal beta → next tier | Release pipeline hardened (Change 8) | Change 8 | Release currently publishes before verifying artifact build order across platforms |
| Desktop | **Preview** | Real composition root and routes replacing placeholders and in-memory session (Change 3A) | Change 3A | Desktop has placeholder navigation and an in-memory session per the remediation plan's known gaps |
| Desktop | Preview → next tier | Desktop tests required in CI (Change 5) | Change 5 | CI job "☕ JVM / 🖥️ Desktop" only runs `:desktopApp:compileKotlin` — no `:desktopApp:test` step exists |
| Desktop | Preview → next tier | Keyring/session policy verified on every supported OS (Change 3A) | Change 3A | Only Linux (`ubuntu-latest`) is built in the release workflow; macOS/Windows rows are commented out pending a macOS runner |
| Web on GitHub Pages | **Stateless demo** | Explicit mock/demo labelling in UI and docs; no credential persistence; meaningful browser smoke tests (Change 3B, 5) | Change 3B, Change 5 | The Pages deploy step is literally named "Build web distribution with mock data"; `webApp/src` has zero test files though CI job "🌐 Web / wasmJs" still runs `wasmJsBrowserTest` (a vacuous pass) |
| Web production | **Not yet granted** | Approved auth/CORS architecture; COOP/COEP-capable host; persistence reload test; real adapters; production browser suite (Change 3B) | Change 3B | GitHub Pages does not set `Cross-Origin-Opener-Policy`/`Cross-Origin-Embedder-Policy`; per `docs/production-deployment.md`, without these headers OPFS-backed SQLite silently falls back to in-memory storage |

## Readiness checklist

Every promotion requirement above is backed by either a named, currently-runnable CI check or an explicit "no automated check yet" note. This is the checklist required by the `readiness` specification.

| Check | How to run | Status |
|---|---|---|
| Format and static analysis | CI job `pre-checks` (`./gradlew spotlessCheck`, `./gradlew detekt`) | Passing, required |
| Core/shared JVM tests | CI job `☕ JVM / 🖥️ Desktop` (JVM tests step) | Passing, required |
| Android assemble + unit tests | CI job `🟢 Android` | Passing, required |
| Android instrumented smoke | CI job `📱 Instrumented` | Passing, required |
| Desktop compiles | CI job `☕ JVM / 🖥️ Desktop` (desktop compilation step) | Passing, required |
| Desktop unit/UI tests run in CI | No automated check yet — `:desktopApp:test` is not invoked anywhere in `.github/workflows/ci.yml` | Owned by Change 5 |
| Web compiles + browser test task runs | CI job `🌐 Web / wasmJs` | Passing, but the test task has no test files to exercise |
| Web has meaningful browser tests | No automated check yet — `webApp/src` contains no test sources | Owned by Change 5 |
| TV focus traversal/restoration tests | No automated check yet — `testing/tv-focus-harness` exists but is not consumed by any production screen test | Owned by Change 5 |
| Screenshot regression detection on every PR | No automated check yet — `.github/workflows/screenshots.yml` is `workflow_dispatch`-only | Owned by Change 6 |
| Benchmark smoke in CI | No automated check yet — no workflow invokes `:benchmark`; last recorded manual run passed 3 of 7 scenarios | Owned by Change 6 |
| Baseline profile generated and consumed | No automated check yet — no `baseline-prof.txt` is committed | Owned by Change 6 |
| Release publishes only after build/verify | No automated check yet — see `docs/release.md` for the current (build-after-tag) order | Owned by Change 8 |
| Desktop credential support matrix verified per OS | Manual acceptance record — none exists yet; only Linux is built | Owned by Change 3A |
| Android TV/tablet/phone manual device acceptance | Manual acceptance record: `docs/testing/device-acceptance.md` | Documented, run manually |
| Web COOP/COEP-capable production host selected | No automated check yet — decision record not yet written | Owned by Change 3B |

## Non-goals of this matrix

This matrix states current tiers and what's missing for promotion; it does not implement any of the listed gaps. Each "owning change" above is a change in `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`'s delivery sequence, not work done by this change.
