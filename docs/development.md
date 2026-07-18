# Development Environment

The repository uses one pinned Nix flake shell for local development.

## Bootstrap

1. Install `nix` and `direnv` on your machine if they are not already present.
2. From the repository root, run `direnv allow` once.
3. After that, entering the repository automatically loads the default shell.
4. You can also enter the same shell explicitly with `nix develop`.

## What The Shell Provides

The default shell includes:

- `openspec`
- `git`
- `java`
- `./gradlew` wrapper execution support
- Android SDK command-line tools
- Android platform tools
- `sdkmanager`
- `adb`
- `android-studio`

The shell also exports `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` so Gradle and Android tooling share the same SDK root.

## First Run

The first shell entry may download the Android SDK components pinned by the flake lock. If Android tooling prompts for extra license acceptance, run:

```bash
sdkmanager --licenses
```

Then launch Android Studio from the same shell so it reuses the repository SDK root:

```bash
android-studio
```

If you want a quick smoke test after bootstrapping, run:

```bash
./gradlew -v
```

## Java toolchain

The flake ships JDK 25 (`JAVA_HOME`, runs the Gradle daemon) and JDK 17 (`JAVA17_HOME`, used by the Kotlin/Java compile toolchain). No system Java needed. See [`docs/jdk.md`](jdk.md).

### Running Tests

#### Offline Tests (Required CI)
```bash
./gradlew check testDebugUnitTest lintDebug assembleDebug
```

This is the same command set that runs in CI. It does not require Media credentials and works fully offline.

#### OpenAPI Validation
```bash
vacuum lint api/subsloth.openapi.yaml
```

Validates `api/subsloth.openapi.yaml` against OpenAPI 3.1 rules. Vacuum is included in the Nix flake shell and runs in ~100ms (no Gradle overhead).

#### Live Drift Tests (Local Only)
```bash
SUBSLOTH_LOGIN="your@email.com" SUBSLOTH_PASSWORD="your-password" ./gradlew :core:network:test
```

Live drift tests validate that the committed OpenAPI contract and typed DTOs still match the live Media Kodi-compatible API surface.

- **Credentials**: Set `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD` environment variables.
- **Skip behavior**: If either variable is missing, the tests are skipped (not failed).
- **No CI workflow**: There is no GitHub Actions live-drift workflow in v1. Live drift verification is a manual developer responsibility.
- **Complementary safeguards**: `FixtureTest` (Ktor MockEngine) replays captured fixtures offline. The `testing:api-contract` module validates fixture generation and WireMock mapping replay.

#### Invariant Checks
The repository includes automated invariant checks that run in CI:
- **Sensitive artifact scan**: Scans for committed credentials, auth headers, signed URLs, HAR files, browser traces, and screenshots.

Run manually:
```bash
./.github/scripts/check-invariants.sh
```
See [`docs/agent/emulator-testing.md`](agent/emulator-testing.md) for instrumented test workflow, [`docs/testing/benchmarks.md`](testing/benchmarks.md) for macrobenchmarks and baseline profiles, [`docs/testing/screenshot-tests.md`](testing/screenshot-tests.md) for screenshot tests, and [`docs/troubleshooting.md`](troubleshooting.md) for common build, test, and IDE issues.

### Pre-Commit Checks

Before committing, run the full pre-commit suite defined in `AGENTS.md` to catch formatting, lint, and compilation issues early:

```bash
./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :androidApp:assembleDebug test
```

This is stricter than the CI-only command set (`check testDebugUnitTest lintDebug assembleDebug`) — it includes spotless formatting, detekt, and targeted KMP compilation checks that CI also runs. Running it locally avoids commit-then-fix cycles.

### Building

```bash
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
