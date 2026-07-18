# Benchmarks & Baseline Profiles

The `:benchmark` module provides Android macrobenchmarks and baseline profile generation using `androidx.benchmark`. These run on a physical device or emulator via `connectedDebugAndroidTest`.

---

## Module Overview

| Aspect | Detail |
|---|---|
| Module | `:benchmark` |
| Plugin | `com.android.test` (self-instrumenting — targets `:androidApp`) |
| Test runner | `androidx.test.runner.AndroidJUnitRunner` |
| Suppressed errors | `EMULATOR,DEBUGGABLE` — allows smoke-testing on emulator/debug builds |
| Dependencies | `benchmark-macro-junit4`, `androidx.test.ext.junit`, `uiAutomator` |

The module is self-instrumenting: `targetProjectPath = ":androidApp"` in `build.gradle.kts` means it runs against the installed debug APK.

---

## Available Benchmarks

| Benchmark | Measures | Baseline Profile |
|---|---|---|
| `StartupBenchmark` | Cold start and warm re-launch time | ✅ Generates startup profile |
| `HomeLoadBenchmark` | Home screen load from local cache | — |
| `DetailOpenBenchmark` | Movie/series detail screen open time | — |
| `PlaybackStartBenchmark` | Playback start latency | — |
| `BaselineProfileGenerator` | Baseline profile rules | ✅ Generates profile for all measured scenarios |

---

## Running Benchmarks

### On a Physical Device (Recommended for Accurate Results)

```bash
# Build release APK + benchmark APK
./gradlew :androidApp:assembleRelease :benchmark:assemble

# Install and run
./gradlew :benchmark:connectedDebugAndroidTest
```

Results are written to `benchmark/build/outputs/connected_android_test_additional_output/`.

### On an Emulator (Smoke-Testing Only)

```bash
# Start emulator first (see emulator-testing.md)
start-subsloth-emulator

# Run benchmarks (suppressed errors allow debug builds)
./gradlew :benchmark:connectedDebugAndroidTest

# Stop emulator
stop-subsloth-emulator
```

> ⚠ Emulator and debug-build results are **not representative** of real performance. Use only for testing that benchmarks execute without crashes. Always validate on a release-build physical device.

---

## Baseline Profile Generation

The `BaselineProfileGenerator` benchmark produces a `baseline-prof` artifact that the Android app uses at install time to pre-compile critical code paths.

### Workflow

1. Run the benchmark on a **physical device** with a release build:
   ```bash
   ./gradlew :androidApp:assembleRelease :benchmark:connectedDebugAndroidTest
   ```

2. The generated profile is written to:
   ```
   benchmark/build/outputs/connected_android_test_additional_output/connectedDebugAndroidTest/
   ```

3. Copy the generated `baseline-prof` output into the app module:
   ```
   androidApp/src/main/baseline-prof.txt
   ```

4. Rebuild and verify:
   ```bash
   ./gradlew :androidApp:assembleRelease
   ```

### What Gets Profiled

The `BaselineProfileGenerator` iterates through the following user journeys:
- App startup → home screen
- Catalog scrolling
- Movie/series detail open
- Playback start

Each journey is driven by `UiAutomator` interactions against the installed app.

---

## CI Integration

Benchmarks run on every PR and push to main via GitHub Actions. The CI job uses `reactivecircus/android-emulator-runner@v2` with a physical-device-hosted runner where available, or falls back to an emulator for smoke-testing.

CI runs:
```bash
./gradlew :benchmark:connectedDebugAndroidTest
```

---

## Troubleshooting

| Symptom | Likely Cause | Fix |
|---|---|---|
| `BenchmarkRule` errors about EMULATOR/DEBUGGABLE | Running on emulator or debug build | Expected on non-physical-device runs. The module suppresses these errors for CI smoke-testing. |
| `BaselineProfileGenerator` finds no movie cards | No real catalog data on device | Login with valid credentials before running, or seed the database with test fixtures. |
| `DetailOpenBenchmark` / `PlaybackStartBenchmark` fail | Login gate blocks navigation | Login first or use a mock-auth build variant. |
| Benchmark results vary wildly | Background processes / thermal throttling | Close other apps, charge the device, run multiple iterations. |
| `FAILED: couldn't find test` | Benchmark APK not aligned with app APK | Run `:androidApp:assembleRelease :benchmark:assemble` first. |

---

## Adding a New Benchmark

1. Create a test class in `benchmark/src/main/java/net/subsloth/benchmark/` extending `MacrobenchmarkBase` (or write a standalone benchmark using `@RunWith(AndroidJUnit4::class)`).
2. Annotate with `@LargeTest` and `@Requirements(...)` if applicable.
3. Use `UiAutomator` to navigate to the measured screen.
4. Wrap measurement in `benchmarkRule.measureRepeated { ... }`.
5. If the scenario should be included in baseline profile generation, add it to `BaselineProfileGenerator`.
