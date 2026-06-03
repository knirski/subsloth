# Emulator Testing Workflow

Set up and run Android instrumented tests on the emulator.

## Quick Start

```bash
setup-emulator                                         # one-time: install system image + create AVD
run-subsloth-instrumented-tests :core:preferences:connectedDebugAndroidTest  # start → run → stop
```

## Step by Step

```bash
setup-emulator                                    # once per machine
start-subsloth-emulator                            # start + wait for boot
run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest
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

Instrumented tests run on every PR and push to main via GitHub Actions: `reactivecircus/android-emulator-runner@v2`, API 36, `google_apis`, `x86_64`, `swiftshader_indirect`. Currently tests `:core:preferences`.

## Troubleshooting

| Symptom | Fix |
|---|---|
| `EMULATOR_TIMEOUT` | Check `/dev/kvm`; `stop-subsloth-emulator` then retry |
| `TEST_FAILED` | Check `build/reports/androidTests/` for HTML report |
| Connection refused on adb | Run `wait-subsloth-emulator` first |
| Emulator crashes on launch | Ensure `/dev/kvm` exists and is readable |
| Gradle daemon timeout | Run `./gradlew --stop && ./gradlew :core:model:classes` once to warm up |

## Tips

- Gradle warmup: `./gradlew :core:preferences:assembleDebug` once after entering shell
- adb state: `adb devices` to check connectivity
- One emulator at a time — don't run multiple `start-subsloth-emulator` instances
- Emulator output: `/tmp/subsloth-emulator-$USER.log`
