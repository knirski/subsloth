# Emulator Testing Workflow

Set up and run Android instrumented tests on the emulator.

## Quick Start

```bash
setup-emulator                                         # one-time: install system image + create AVD
run-subsloth-instrumented-tests :core:database:connectedAndroidDeviceTest  # start → run → stop
```

## Step by Step

```bash
setup-emulator                                    # once per machine
start-subsloth-emulator                            # start + wait for boot
run-subsloth-instrumented-test :core:database:connectedAndroidDeviceTest
stop-subsloth-emulator
```

## Prerequisites

- Inside `nix develop` or `direnv allow` (flake provides all tooling)
- KVM support (`/dev/kvm` must exist and be readable)
- First Gradle invocation may need daemon warmup (~10-30s)

## Available Scripts

| Script | Purpose |
|---|---|
| `setup-emulator` | Install system image + create AVD (once per machine) |
| `start-subsloth-emulator` | Start emulator and wait for boot |
| `wait-subsloth-emulator` | Wait for already-starting emulator to boot |
| `run-subsloth-instrumented-test` | Run a single connected Android test task |
| `stop-subsloth-emulator` | Stop running emulator |
| `run-subsloth-instrumented-tests` | **One-shot:** start → wait → run → stop |

## Agent Signals (machine-parseable)

All scripts emit `UPPER_CASE` signals on stdout (errors on stderr). Parse stdout for terminal signal.

| Signal | Source | Meaning |
|---|---|---|
| `SETUP_COMPLETE` | `setup-emulator` | System image installed, AVD ready |
| `EMULATOR_PID=<pid>` | `start-subsloth-emulator` | Emulator process ID |
| `BOOT_COMPLETED in <N>s` | `start/wait-*` | Boot finished |
| `EMULATOR_READY` | `start/wait-*` | Ready for tests |
| `EMULATOR_TIMEOUT` | `start/wait-*` (stderr) | Boot timed out |
| `TEST_PASSED` / `TEST_FAILED` | `run-*-test` | Test result |
| `PIPELINE_PASSED` / `PIPELINE_FAILED` | `run-*-tests` | Pipeline result |

## CI Integration

Instrumented tests run on every PR and push to main via GitHub Actions:
[`reactivecircus/android-emulator-runner@v2`](https://github.com/reactivecircus/android-emulator-runner),
API 36, `google_apis`, `x86_64`, `swiftshader_indirect`.

### Performance Optimisations

| Technique | What it saves | Implementation | Notes |
|---|---|---|---|
| **AVD + system image cache** | ~1–2 min (no re-download) | `actions/cache@v6` on `~/.android/avd/` and `~/.android/system-images/` keyed by `runner.os` + `runner.arch` + `api36-google-apis` | Works on all branches; first run after cache eviction downloads fresh |
| **Emulator RAM boost** | Faster test execution | `ram-size: 6144`, `heap-size: 1024`, Gradle capped at `-Xmx512m` + Kotlin daemon at `-Xmx256m` | 6 GB emulator on 7 GB runner — QEMU's `ram-size` is virtual address space, physical usage tracks guest demand; Gradle/Kotlin trimmed because this job only runs tests, not compilation |
| **Extended boot timeout** | Prevents timeout with larger RAM | `emulator-boot-timeout: 600` (10 min) | Safety net — default 300 s can be tight with 2048 MB |

| Workflow | What runs | Trigger |
|---|---|---|
| [`ci.yml`](/.github/workflows/ci.yml) — `instrumented-android-tests` | `:core:database:connectedAndroidDeviceTest` (Room DB creation) + `:androidApp:connectedDebugAndroidTest` (UI instrumented tests) | Every PR and push to `main` when `shared` or `android` paths change (runs in parallel with `build-android`) |
| [`screenshots.yml`](/.github/workflows/screenshots.yml) (`verify` mode) | `:androidApp:connectedDebugAndroidTest` — compares against stored golden images | `workflow_dispatch` (manual) |
| [`screenshots.yml`](/.github/workflows/screenshots.yml) (`update` mode) | Regenerate goldens + export to `docs/screenshots/` + commit | `workflow_dispatch` (manual) |

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `EMULATOR_TIMEOUT` | Emulator not booting | Check `/dev/kvm`; increase `emulator-boot-timeout` if RAM was just raised; `stop-subsloth-emulator` then retry |
| `TEST_FAILED` | Test assertion failed | Check `build/reports/androidTests/` for HTML report |
| Connection refused on adb | Emulator not started | Run `wait-subsloth-emulator` first |
| Emulator crashes on launch | KVM not available | Ensure `/dev/kvm` exists and is readable |
| Gradle daemon timeout | Cold start | Run `./gradlew --stop && ./gradlew :core:model:classes` once to warm up |
| AVD cache miss on new runner arch | Cache key includes `runner.arch` | Expected — first run on a new arch will re-download, subsequent runs hit cache |
| System image download slow | First run or cache eviction | Expected — AVD cache saves ~1–2 min on subsequent runs |

## Tips

- Gradle warmup: `./gradlew :core:model:classes` once after entering shell
- adb state: `adb devices` to check connectivity
- One emulator at a time — don't run multiple `start-subsloth-emulator` instances
- Emulator output: `/tmp/subsloth-emulator-$USER.log`
