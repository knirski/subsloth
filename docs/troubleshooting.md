# Troubleshooting Guide

Common build, test, emulator, IDE, and development issues and their fixes.

---

## Build Failures

| Symptom | Likely Cause | Fix |
|---|---|---|
| `Project with path ':app' not found` | Stale reference to old module name | Use `:androidApp` instead of `:app` |
| `Could not find com.android.library` / AGP errors | Not inside Nix dev shell | Run `direnv allow` or `nix develop` |
| `Unresolved reference: compose` | Missing Compose plugin | Add `id("org.jetbrains.kotlin.plugin.compose")` and `alias(libs.plugins.compose.gradle)` |
| `Kotlin compiler warning treated as error` | `allWarningsAsErrors = true` | Fix the warning, or suppress with `@OptIn` / `@Suppress` (narrowest scope) |
| `Detekt: MagicNumber` | Bare numeric literal | Extract to named constant |
| `Detekt: ExpressionBodySyntax` | Single-expression function with block body | Use `= expr` syntax |
| `Detekt: ClassOrdering` | Methods before properties | Reorder: properties first, methods after |
| `Detekt: ComposableParamOrder` | Wrong Compose parameter order | Required → `modifier` → optional → trailing lambda |
| `FAILED: Configuration cache` | Stale configuration cache | `./gradlew --stop` then retry without cache |
| Build hangs with no output | Stale configuration cache masking a StackOverflow | `./gradlew --no-configuration-cache` to see the real error |
| Gradle daemon OutOfMemoryError | Insufficient heap | Add `org.gradle.jvmargs=-Xmx2048m` in `gradle.properties` |
| `Kotlin 2.3: DateTimeFormatException not accessible` | API removed | Catch `IllegalArgumentException` instead |
| `Kotlin 2.3: Instant deprecated` | Use `kotlin.time.Instant` | Replace `kotlinx.datetime.Instant` with stdlib `kotlin.time.Instant` |

### Spotless / ktlint

| Symptom | Fix |
|---|---|
| `const val` naming violations | Use `SCREAMING_SNAKE_CASE`: `PHONE_WIDTH` not `PhoneWidth` |
| File name ≠ declaration | Rename file to match the primary declaration |
| Composable lambda position | Must be last parameter, after `modifier` |
| Single-expression function style | `fun foo() = expr` not `fun foo() { return expr }` |

---

## KMP / Cross-Platform Issues

| Symptom | Likely Cause | Fix |
|---|---|---|
| `String.format()` not found on WasmJS | JVM-only API in common code | Use Kotlin string templates: `"Value: $value"` |
| `java.*` / `javax.*` not found | Platform API in common code | Move to `jvmMain` source set or use `expect`/`actual` |
| `Math.*` / `StrictMath` not found | JVM-only math API | Use `kotlin.math.*` instead |
| WasmJS build fails | Missing npm dependency | Declare in `wasmJsMain` with `implementation(npm(...))` |
| WasmJS webpack bundling fails | Missing `KOTLIN_NODEJS_HOME` / `KOTLIN_YARN_HOME` | Ensure inside Nix dev shell (`direnv allow`) |
| iOS targets don't compile | iOS targets disabled | See `docs/known-gaps.md` §6 — deferred |
| `androidTarget()` plugin conflict | AGP + KMP conflict in convention | Use separate AGP module or add per-module `androidTarget()` |
| SQLite on WasmJS: `isNull` bug | Upstream bug in `sqlite-web:2.7.0-alpha05` | See `docs/known-gaps.md` §3 — blocked on upstream |

---

## Emulator & Instrumented Tests

| Symptom | Likely Cause | Fix |
|---|---|---|
| `EMULATOR_TIMEOUT` | Emulator not booting | Check `/dev/kvm`; `stop-subsloth-emulator` then retry |
| `TEST_FAILED` | Test assertion failed | Check `build/reports/androidTests/` for HTML report |
| Connection refused on adb | Emulator not started | Run `wait-subsloth-emulator` first |
| Emulator crashes on launch | KVM not available | Ensure `/dev/kvm` exists and is readable (`ls -la /dev/kvm`) |
| Gradle daemon timeout (cold start) | First run after entering shell | `./gradlew :core:model:classes` to warm up |
| `No tests found for given includes` | Wrong task name | Use `:module:connectedDebugAndroidTest` not `:module:connectedAndroidTest` |
| `Could not install APK` | App already installed with different signature | Uninstall first: `adb uninstall net.subsloth` |

---

## Screenshot Tests

| Symptom | Likely Cause | Fix |
|---|---|---|
| Test fails with image diff | UI intentionally changed | Update goldens: `-Pandroid.test.screenshot.update.golden=true` |
| Test fails with image diff | UI changed unintentionally | Fix the UI regression |
| `@PreviewTest` annotation not found | Missing plugin | Verify `alias(libs.plugins.compose.screenshot)` in `androidApp/build.gradle.kts` |
| Test failed to render | Composable throws during preview | Check for platform APIs (Context, Intent) without guards |

---

## Benchmarks

| Symptom | Likely Cause | Fix |
|---|---|---|
| `BenchmarkRule` errors about EMULATOR/DEBUGGABLE | Running on emulator or debug build | Expected — suppressed in CI; use physical device for real measurements |
| `BaselineProfileGenerator` finds no movie cards | No real catalog data | Login with valid credentials, or seed database with test fixtures |
| `DetailOpenBenchmark` / `PlaybackStartBenchmark` fail | Login gate blocks navigation | Login first or use mock-auth build variant |

---

## Fixture Capture

| Symptom | Likely Cause | Fix |
|---|---|---|
| `captureApi` 401 | Wrong credentials | Verify email/password |
| `exportFixtures` 0 fixtures | `Endpoint.parse` doesn't recognize URL | Update `Endpoint.parse` in `Endpoint.kt` |
| `WebDiscoveryFixtureTest` fails | Real hostname leaked | Add to sanitization rules, re-export |
| `FixtureTest` fails | Fixture doesn't decode against DTO | Update fixture shape or DTO |
| `MockMappingVerificationTest` fails | Missing fixture or bad replay metadata | Verify fixture exists and metadata matches real response |
| HAR file not found | Git-ignored `*.har` | HAR files are ignored by `.gitignore`; process and delete originals |

---

## Git & Version Control

| Symptom | Likely Cause | Fix |
|---|---|---|
| `screenshots/` matches wrong directory | Gitignore glob is too broad | Use `git add -f` for intentional commits |
| Force-push orphans review threads | `git push --force` rewrites history | Use incremental commits + normal pushes during review |
| `Cannot merge: conflicting files` | Two PRs modified same file | Rebase, keep both changes (e.g. both entries in `libs.versions.toml`) |
| `Stale CI checks` after rebase | CI triggered on old SHA | Force-push to trigger new CI run |
| `PR title doesn't match conventional commit` | Title rejected by `pr-title.yml` | Edit PR title to match `feat:`, `fix:`, `chore:`, etc. |

---

## IDE (Android Studio)

| Symptom | Likely Cause | Fix |
|---|---|---|
| Android Studio can't find SDK | Not launched from Nix shell | Launch via `android-studio` from the Nix dev shell |
| `local.properties` missing | Auto-generated by `flake.nix` shellHook | `direnv allow` to regenerate |
| IDE shows red squiggles on KMP sources | IDE indexing incomplete | `File → Invalidate Caches...` or `./gradlew clean` |
| Navigation3 symbols not resolved | IDE doesn't recognise Navigation3 | Ensure `androidx.navigation3` dependencies are in `gradle/libs.versions.toml` |
| Compose preview fails | Missing `@Preview` annotation or platform dependency | Add `@Preview(showBackground = true)`; verify Compose BOM |

---

## Nix / Development Environment

| Symptom | Likely Cause | Fix |
|---|---|---|
| `direnv: command not found` | direnv not installed | Install via `nix profile install nixpkgs#direnv` or system package manager |
| `direnv: blocked` | First use of `.envrc` | Run `direnv allow` |
| `sdkmanager --licenses` prompts | Android SDK licenses not accepted | Run `sdkmanager --licenses` inside Nix shell |
| `./gradlew` uses system JDK instead of Nix JDK | Not inside Nix dev shell | Enter shell with `nix develop` or `direnv allow` |
| Missing `JAVA17_HOME` / `ANDROID_HOME` | Nix shell not loaded | Verify `echo $JAVA17_HOME` has a value |
| Emulator scripts not found (`setup-emulator`, etc.) | Not in Nix dev shell | These are provided by `flake.nix` — enter the shell first |
| `vacuum: command not found` | vacuum not installed | Install via Nix (included in flake), npm, or `curl` — see `docs/openapi.md` |

---

## See Also

- `docs/known-gaps.md` — documented deferred items and upstream blockers
- `docs/agent/emulator-testing.md` — emulator setup and instrumented test workflow
- `docs/testing/benchmarks.md` — benchmark troubleshooting
- `docs/testing/screenshot-tests.md` — screenshot test troubleshooting
- `docs/agent/capture-workflow.md` — fixture capture troubleshooting
- `docs/agent/lessons-learned.md` — hard-won patterns from past PRs
