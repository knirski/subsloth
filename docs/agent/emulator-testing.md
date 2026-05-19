# Emulator Testing Workflow

Set up and run Android instrumented tests on the emulator — for AI agents and humans.

## Quick Start

```bash
# One-time setup (install system image, create AVD)
setup-emulator

# Full pipeline: start emulator → run tests → stop emulator
run-subsloth-instrumented-tests :core:preferences:connectedDebugAndroidTest
```

That's it. The one-shot script handles boot wait, test execution, and cleanup.

If you need step-by-step control instead:

```bash
setup-emulator                                    # once per machine
start-subsloth-emulator                            # start + wait for boot
run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest
stop-subsloth-emulator
```

## Prerequisites

- Run inside `nix develop` or `direnv allow` (the flake provides all tooling)
- The emulator requires KVM support (`/dev/kvm` must exist and be readable)
- First Gradle invocation may need to warm up the daemon (~10-30s)

## Available Scripts

All scripts are available inside `nix develop` or with `direnv allow`. Each supports `--help`.

| Script | Purpose |
|---|---|
| `setup-emulator` | Install system image + create AVD (once per machine) |
| `start-subsloth-emulator` | Start emulator and wait for boot |
| `wait-subsloth-emulator` | Wait for an already-starting emulator to boot |
| `run-subsloth-instrumented-test` | Run a single connected Android test task |
| `stop-subsloth-emulator` | Stop the running emulator |
| `run-subsloth-instrumented-tests` | **One-shot:** start → wait → run → stop (reuses running emulator) |

## Detailed Workflow

### setup-emulator

Run once per machine (or after clearing `/tmp/android-sdk`):

```bash
setup-emulator
```

Installs the system image and creates the `subsloth-device` AVD with an **absolute system-image path**. The absolute path means the emulator works regardless of `ANDROID_HOME` or `ANDROID_SDK_ROOT` — no environment overrides needed even outside the Nix shell.

Prints `SETUP_COMPLETE` on success. The nix shell's `shellHook` also checks whether setup is needed on entry.

### start-subsloth-emulator

```bash
start-subsloth-emulator
```

1. Launches the emulator with `-no-window -no-audio -gpu swiftshader_indirect -no-snapshot`
2. Uses `nohup` + `disown` to fully detach from the parent shell (survives `nix develop --command` exit)
3. Waits up to 120 seconds for boot completion
4. Disables animations for reliable test execution
5. Prints machine-parseable signals

### wait-subsloth-emulator

If the emulator was started separately (e.g., by another process or terminal):

```bash
wait-subsloth-emulator
```

Same signals as the wait phase of `start-subsloth-emulator`.

### run-subsloth-instrumented-test

```bash
run-subsloth-instrumented-test <gradle-task-path> [gradle-args...]
```

Example:

```bash
run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest
```

Requires the emulator to be running and fully booted. Use `start-subsloth-emulator` or `wait-subsloth-emulator` first.

### stop-subsloth-emulator

```bash
stop-subsloth-emulator
```

Tries graceful `adb emu kill` first, then falls back to `SIGTERM` / `SIGKILL`.

### run-subsloth-instrumented-tests (One-Shot Pipeline)

```bash
run-subsloth-instrumented-tests <gradle-task-path> [gradle-args...]
```

All-in-one convenience: start emulator → wait for boot → run tests → stop emulator.

- If the emulator is **already running**, it is reused and **not stopped** after tests
- If the emulator is **not running**, it is started and **stopped** after tests

## Agent Signals Reference

All scripts emit machine-parseable `UPPER_CASE` signals on stdout (or stderr for errors). Agents should run the script, wait for exit code, and parse stdout for the terminal signal.

| Signal | Source | Meaning |
|---|---|---|
| `SETUP_COMPLETE` | `setup-emulator` | System image installed, AVD ready |
| `EMULATOR_STARTING` | `start-subsloth-emulator` | Launch initiated |
| `EMULATOR_PID=<pid>` | `start-subsloth-emulator` | Emulator process ID |
| `BOOT_COMPLETED in <N>s` | `start/wait-subsloth-emulator` | Boot finished |
| `EMULATOR_READY` | `start/wait-subsloth-emulator` | Ready for tests |
| `EMULATOR_TIMEOUT` | `start/wait-subsloth-emulator` (stderr) | Boot timed out |
| `WAITING_FOR_EMULATOR` | `wait-subsloth-emulator` | Polling started |
| `TEST_RUNNER_STARTING` | `run-*-test` | Test execution started |
| `TEST_PASSED` | `run-*-test` or pipeline | All tests passed |
| `TEST_FAILED` | `run-*-test` or pipeline (stderr) | Test failure |
| `EMULATOR_STOPPING` | `stop-subsloth-emulator` | Shutdown initiated |
| `EMULATOR_STOPPED` | `stop-subsloth-emulator` | Shutdown complete |
| `PIPELINE_START` | `run-*-tests` | One-shot pipeline began |
| `EMULATOR_ALREADY_RUNNING` | `run-*-tests` | Emulator was already up |
| `EMULATOR_BOOTING` | `run-*-tests` (stderr) | Emulator existed but booting |
| `PIPELINE_PASSED` | `run-*-tests` | Full pipeline succeeded |
| `PIPELINE_FAILED` | `run-*-tests` (stderr) | Pipeline failed |

## CI Integration

Instrumented tests run on every PR and push to `main` via GitHub Actions:

- **Action**: [`reactivecircus/android-emulator-runner@v2`](https://github.com/reactivecircus/android-emulator-runner)
- **Runner**: `ubuntu-latest` with KVM acceleration
- **API**: 36, `google_apis`, `x86_64`, `swiftshader_indirect` GPU

The CI job (`instrumented-test` in `.github/workflows/ci.yml`) uses the same approach as the local scripts. Currently tests `:core:preferences` — extend the `script` line in CI to add more modules.

The local scripts are for **development and agent workflows**. CI uses the `android-emulator-runner` action directly.

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `EMULATOR_TIMEOUT` | Emulator not booting | Check `/dev/kvm`: `ls -l /dev/kvm`; run `stop-subsloth-emulator` then retry |
| `TEST_FAILED` | Test assertion failed | Check `build/reports/androidTests/` for HTML report |
| `Connection refused` on adb | Emulator not started or still booting | Run `wait-subsloth-emulator` first |
| Emulator crashes on launch | KVM not available | Ensure `/dev/kvm` exists and is readable (`nix develop` may need `--impure` for `/dev` access) |
| `SETUP_COMPLETE` never prints | System image download failed | Check network; retry `setup-emulator` |
| AVD system path error | AVD config has old relative path | Re-run `setup-emulator` to regenerate with absolute path |
| Gradle daemon timeout | First run cold start | Run `./gradlew --stop && ./gradlew :core:model:classes` once to warm up |
| Emulator log says "Unknown AVD" | AVD not found | Run `setup-emulator` to recreate; check `~/.android/avd/` |

## Tips

- **Gradle warmup**: First Gradle invocation is slow (daemon cold start + dependency resolution). Run `./gradlew :core:preferences:assembleDebug` once after entering the shell to warm up.
- **adb state**: Run `adb devices` to check emulator connectivity. If the emulator shows as `offline`, wait a few seconds and retry.
- **Parallel tests**: Currently only one emulator is supported. Don't run multiple `start-subsloth-emulator` instances.
- **Logs**: The emulator output goes to `/tmp/subsloth-emulator-$USER.log` for debugging startup issues (user-specific to avoid multi-user conflicts).
