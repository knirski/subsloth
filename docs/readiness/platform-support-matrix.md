# Platform Support Matrix

This is the single authoritative statement of each platform's release-readiness tier, required by the `readiness` OpenSpec capability. `README.md`, `docs/project-assessment.md`, and the canonical `platform-parity` spec link here rather than restating platform status independently. No document may claim a platform is production-ready, fully supported without qualification, or gate-free based only on the fact that it compiles or renders a shell.

Tiers are adopted from `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`'s promotion-gates table and are not re-derived here.

Last reconciled: 2026-08-02, against `feat/define-web-runtime-tier` after the Web demo runtime change.

## Tiers

| Platform | Current tier | Promotion requirement | Owning change | Evidence today |
|---|---|---|---|---|
| Android | **Internal beta** | Change 2 (`wire-android-production-runtime`) complete: real session/credential adapter, authenticated clients, wired ViewModels, single authenticated start destination | Change 2 | Done. `AndroidSessionState` (Keystore-backed, HMAC profile-key derivation, cold-start recovery, typed auth errors) replaces `InMemorySessionState` on Android's production path; `SubSlothNavHost` starts at `CatalogKey`; library/downloads/settings/player/detail/auth-repair all wired to real adapters. One documented, deliberately out-of-scope gap: `PlayerViewModel`'s stream-source resolution (`fetchVideoSource`/`refreshStreamUrl`) has no `PlaybackPort` implementation anywhere in the tree yet — left on its safe no-op default pending a future change scoped to the `playback` capability. |
| Android | Internal beta → next tier | Required platform tests green (Change 5) | Change 5 | CI job "📱 Instrumented" runs (93 tests as of Change 2, including new session/library/account-switching coverage); TV focus traversal tests do not exist yet (harness unused — see disposition ledger) |
| Android | Internal beta → next tier | Baseline profile consumed by release build (Change 6) | Change 6 | No `baseline-prof.txt` is committed anywhere in the tree |
| Android | Internal beta → next tier | Release pipeline hardened (Change 8) | Change 8 | Release currently publishes before verifying artifact build order across platforms |
| Desktop | **Preview** | Real composition root and routes replacing placeholders and in-memory session (Change 3A) | Change 3A | Desktop has placeholder navigation and an in-memory session per the remediation plan's known gaps |
| Desktop | Preview → next tier | Desktop tests required in CI (Change 5) | Change 5 | CI job "☕ JVM / 🖥️ Desktop" only runs `:desktopApp:compileKotlin` — no `:desktopApp:test` step exists |
| Desktop | Preview → next tier | Keyring/session policy verified on every supported OS (Change 3A) | Change 3A | Only Linux (`ubuntu-latest`) is built in the release workflow; macOS/Windows rows are commented out pending a macOS runner |
| Web on GitHub Pages | **Stateless demo** | Explicit mock/demo labelling in UI and docs; no credential persistence; meaningful browser smoke tests (Change 3B, 5) | Change 3B, Change 5 | The Pages deploy step is named "Build stateless Web demo with mock data only"; `WebDemoBanner` is always rendered; `WebDemoRuntime` uses the WASM mock transport; Nix-provided Chromium 150.0.0.0 and Firefox 153.0 browser tests cover mock startup and seeded credential-key preservation. |
| Web production | **Not yet granted** | Approved auth/CORS architecture; COOP/COEP-capable host; persistence reload test; real adapters; production browser suite (Change 3B) | Change 3B | GitHub Pages does not set `Cross-Origin-Opener-Policy`/`Cross-Origin-Embedder-Policy`; per `docs/production-deployment.md`, without these headers OPFS-backed SQLite silently falls back to in-memory storage |

## Readiness checklist

Every promotion requirement above is backed by either a named, currently-runnable CI check or an explicit "no automated check yet" note. This is the checklist required by the `readiness` specification.

| Check | How to run | Status |
|---|---|---|
| Format and static analysis | CI job `pre-checks` (`./gradlew spotlessCheck`, `./gradlew detekt`) | Passing, required |
| Core/shared JVM tests | CI job `☕ JVM / 🖥️ Desktop` (JVM tests step) | Passing, required |
| Android assemble + unit tests | CI job `🟢 Android` | Passing, required |
| Android instrumented smoke | CI job `📱 Instrumented` | Passing, required |
| Android session/data runtime has no in-memory/no-op binding | `AndroidSessionStateInstrumentedTest`/`LogoutCleanupInstrumentedTest` assert `AppContainer.sessionPort is AndroidSessionState` and `!is InMemorySessionState`; production startup wires real session, library, downloads, and settings adapters. Player stream-source resolution remains on its no-op default (no `PlaybackPort` implementation exists) — not covered by this check, tracked separately below | Change 2, done for session/data adapters |
| Android player stream-source resolution is production-wired | No automated check yet — `PlayerViewModel`'s `fetchVideoSource`/`refreshStreamUrl` remain on their safe no-op defaults; `core/domain/.../PlaybackPort` has no implementation anywhere in the tree | Owned by a future `playback`-scoped change |
| Desktop compiles | CI job `☕ JVM / 🖥️ Desktop` (desktop compilation step) | Passing, required |
| Desktop real composition root and routes (no placeholders) | No automated check yet — no test asserts absence of placeholder navigation or the in-memory session | Owned by Change 3A |
| Desktop unit/UI tests run in CI | No automated check yet — `:desktopApp:test` is not invoked anywhere in `.github/workflows/ci.yml` | Owned by Change 5 |
| Desktop credential support matrix verified per OS | Manual acceptance record — none exists yet; only Linux is built | Owned by Change 3A |
| Web compiles + browser test task runs | CI job `🌐 Web / wasmJs` | Passing, with the Pages build explicitly using mock data only |
| Web has meaningful browser tests | `./gradlew :webApp:wasmJsBrowserTest` | Passing; Nix-provided Chromium 150.0.0.0 and Firefox 153.0 each execute five Web runtime tests |
| Web demo mode is explicitly labelled and requests no production credentials | `WebDemoBanner` plus `WebRuntimeModeTest.demoBannerExplainsDataAndCredentialBoundary` | Done for the stateless demo |
| Web credentials absent from local storage | `WebRuntimeModeTest.demoStartupDoesNotUseCredentialStorage` | Done for the stateless demo; authenticated promotion remains open |
| Web production auth/CORS architecture decision recorded | No automated check yet — decision record not yet written | Owned by Change 3B |
| Web COOP/COEP-capable production host selected | No automated check yet — decision record not yet written | Owned by Change 3B |
| Web OPFS persistence survives a page reload | No automated check yet — no reload-persistence test exists; GitHub Pages lacks COOP/COEP so OPFS falls back to in-memory storage today | Owned by Change 3B |
| Web production browser test suite (beyond the demo smoke tests above) | No automated check yet — not yet defined | Owned by Change 3B |
| TV focus traversal/restoration tests | No automated check yet — `testing/tv-focus-harness` exists but is not consumed by any production screen test | Owned by Change 5 |
| Screenshot regression detection on every PR | No automated check yet — `.github/workflows/screenshots.yml` is `workflow_dispatch`-only | Owned by Change 6 |
| Benchmark smoke in CI | No automated check yet — no workflow invokes `:benchmark`; last recorded manual run passed 3 of 7 scenarios | Owned by Change 6 |
| Baseline profile generated and consumed | No automated check yet — no `baseline-prof.txt` is committed | Owned by Change 6 |
| Release publishes only after build/verify | No automated check yet — see `docs/release.md` for the current (build-after-tag) order | Owned by Change 8 |
| Android TV/tablet/phone manual device acceptance | Manual acceptance record: `docs/testing/device-acceptance.md` | Documented, run manually |

## Non-goals of this matrix

This matrix states current tiers and what's missing for promotion; it does not implement any of the listed gaps. Each "owning change" above is a change in `docs/superpowers/plans/2026-07-23-repository-assessment-remediation.md`'s delivery sequence, not work done by this change.
