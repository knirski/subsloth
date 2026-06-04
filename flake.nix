{
  description = "subsloth development shell";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs =
    { nixpkgs, ... }:
    let
      # Android Studio is only available on x86_64-linux in this nixpkgs
      # revision, so the bootstrap shell stays pinned to the supported host.
      system = "x86_64-linux";
      pkgs = import nixpkgs {
        inherit system;
        config = {
          allowUnfree = true;
          android_sdk.accept_license = true;
        };
      };

      # ── Android SDK ──────────────────────────────────────────────────────
      androidPackages = pkgs.androidenv.composeAndroidPackages {
        cmdLineToolsVersion = "17.0";
        platformVersions = [ "37" ];
        # 37 is the default for compileSdk 37. 36 is required by AGP 9's
        # com.android.kotlin.multiplatform.library plugin — without it the
        # plugin tries to install build-tools 36 via sdkmanager into the
        # read-only Nix store and fails. 36 is inert (only satisfies the
        # bootstrap check); actual compilation uses 37.
        buildToolsVersions = [ "37.0.0" "36.0.0" ];
        platformToolsVersion = "37.0.0";
      };

      androidSdk = androidPackages.androidsdk;
      androidEmulator = pkgs.androidenv.androidPkgs.emulator;
      androidStudio = pkgs.android-studio.withSdk androidSdk;

      # Writable SDK root for sdkmanager (Nix store is read-only, so
      # system images, emulator add-ons, and AVDs go here).
      writableSdkRoot = "/tmp/android-sdk";

      # System image for x86_64 emulation. google_apis includes Play
      # Services and is the standard choice for app testing.
      systemImage = "system-images;android-37;google_apis;x86_64";
      systemImageDir = "${writableSdkRoot}/${builtins.replaceStrings [ ";" ] [ "/" ] systemImage}";
      avdName = "subsloth-device";

      # ── setup-emulator script ─────────────────────────────────────────────
      # Installs the x86_64 system image, creates an AVD with an absolute
      # system-image path so the emulator works regardless of ANDROID_HOME
      # or ANDROID_SDK_ROOT. Run once per machine.
      setupEmulator = pkgs.writeShellScriptBin "setup-emulator" ''
        set -euo pipefail

        if [ $# -gt 0 ] && ( [ "$1" = "-h" ] || [ "$1" = "--help" ] ); then
          echo "Usage: setup-emulator"
          echo ""
          echo "Install system image and create the '${avdName}' AVD."
          echo "Run once per machine (or after clearing ${writableSdkRoot})."
          echo ""
          echo "Signals: SETUP_COMPLETE"
          exit 0
        fi

        SDK="${writableSdkRoot}"
        AVD_DIR="$HOME/.android/avd"
        SYS_IMG_DIR="${systemImageDir}"

        echo "→ Installing x86_64 system image..."
        sdkmanager --sdk_root="$SDK" --install "${systemImage}"

        # Remove corrupted/conflicting AVDs for the same name
        if [ -d "$AVD_DIR/${avdName}.avd" ]; then
          echo "→ Removing existing AVD '${avdName}'..."
          rm -rf "$AVD_DIR/${avdName}.avd" "$AVD_DIR/${avdName}.ini"
        fi

        echo "→ Creating AVD '${avdName}' (x86_64)..."
        mkdir -p "$AVD_DIR/${avdName}.avd"

        # Write the AVD .ini file
        cat > "$AVD_DIR/${avdName}.ini" << INI
        target=android-37
        path=$AVD_DIR/${avdName}.avd
        INI

        # Write the AVD config with an ABSOLUTE system-image path and
        # resolution skin.  The absolute path means the emulator does not
        # need ANDROID_HOME / ANDROID_SDK_ROOT to locate the image — it
        # works inside or outside the Nix shell, with or without env overrides.
        cat > "$AVD_DIR/${avdName}.avd/config.ini" << INI
        AvdId=${avdName}
        PlayStore.enabled=false
        abi.type=x86_64
        hw.cpu.arch=x86_64
        hw.cpu.ncore=4
        hw.ramSize=2048
        hw.gpu.enabled=yes
        hw.gpu.mode=host
        hw.lcd.density=420
        hw.lcd.height=2400
        hw.lcd.width=1080
        hw.mainKeys=no
        hw.device.name=pixel_6
        image.sysdir.1=${systemImageDir}/
        tag.id=google_apis
        tag.display=Google APIs
        disk.dataPartition.size=4G
        skin.name=1080x2400
        INI

        # Create userdata disk (empty data partition)
        if [ -f "$SYS_IMG_DIR/data/empty_data_disk" ]; then
          cp "$SYS_IMG_DIR/data/empty_data_disk" "$AVD_DIR/${avdName}.avd/userdata.img"
        elif [ -f "$SYS_IMG_DIR/data/userdata.img" ]; then
          cp "$SYS_IMG_DIR/data/userdata.img" "$AVD_DIR/${avdName}.avd/userdata.img"
        else
          # Fallback: create a minimal 4G sparse image
          dd if=/dev/zero of="$AVD_DIR/${avdName}.avd/userdata.img" bs=1M count=1 seek=4095 2>/dev/null
        fi

        echo ""
        echo "=== SETUP_COMPLETE ==="
        echo "AVD: ${avdName} (x86_64)"
        echo "Image: ${systemImage}"
        echo "Sysdir: $SYS_IMG_DIR"
        echo ""
        echo "Next steps:"
        echo "  1. start-subsloth-emulator"
        echo "  2. run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest"
        echo "  3. stop-subsloth-emulator"
        echo "======================"
      '';

      # ── start-subsloth-emulator script ──────────────────────────────────────
      # Launches the emulator detached from the parent shell (survives
      # "nix develop --command" exit), waits for boot, and prints
      # machine-parseable signals for AI agents.
      startSubSlothEmulator = pkgs.writeShellScriptBin "start-subsloth-emulator" ''
        set -euo pipefail

        if [ $# -gt 0 ] && ( [ "$1" = "-h" ] || [ "$1" = "--help" ] ); then
          echo "Usage: start-subsloth-emulator"
          echo ""
          echo "Start the '${avdName}' emulator and wait for boot."
          echo ""
          echo "Signals: EMULATOR_STARTING, EMULATOR_PID, BOOT_COMPLETED, EMULATOR_READY, EMULATOR_TIMEOUT"
          exit 0
        fi

        echo "EMULATOR_STARTING"
        echo "AVD: ${avdName}"
        echo "Log: /tmp/subsloth-emulator-$(whoami).log"

        # The AVD config uses an absolute system-image path, so the emulator
        # works with the Nix shell's ANDROID_HOME (read-only Nix store) or
        # with any other environment.  No env overrides needed.

        # Launch fully detached so the process survives "nix develop --command"
        # exit.  nohup + disown prevents SIGHUP propagation.
        EMU_LOG="/tmp/subsloth-emulator-$(whoami).log"
        nohup emulator \
          -avd "${avdName}" \
          -no-window \
          -no-audio \
          -gpu swiftshader_indirect \
          -no-snapshot \
          -skin 1080x2400 \
          -no-metrics \
          > "$EMU_LOG" 2>&1 &
        EMU_PID=$!
        disown "$EMU_PID"
        echo "EMULATOR_PID=$EMU_PID"

        # Wait up to 120 seconds for boot
        BOOT_TIMEOUT=120
        echo "Waiting for boot (timeout: ''${BOOT_TIMEOUT}s)..."
        for i in $(seq 1 $BOOT_TIMEOUT); do
          # Fail fast if emulator process died
          if ! kill -0 "$EMU_PID" 2>/dev/null; then
            echo "EMULATOR_CRASHED" >&2
            exit 1
          fi
          STATE=$(adb -e get-state 2>/dev/null || echo "unknown")
          if [ "$STATE" = "device" ]; then
            BOOT=$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || echo "")
            if [ "$BOOT" = "1" ]; then
              echo "BOOT_COMPLETED in ''${i}s"
              # Disable animations for reliable test execution
              adb -e shell settings put global window_animation_scale 0.0 >/dev/null 2>&1 || true
              adb -e shell settings put global transition_animation_scale 0.0 >/dev/null 2>&1 || true
              adb -e shell settings put global animator_duration_scale 0.0 >/dev/null 2>&1 || true
              echo "EMULATOR_READY"
              exit 0
            fi
          fi
          sleep 1
        done

        echo "EMULATOR_TIMEOUT" >&2
        exit 1
      '';

      # ── wait-subsloth-emulator script ───────────────────────────────────────
      # Waits for the emulator to finish booting. Use when the emulator is
      # already started and you just need to wait for boot. Prints
      # machine-parseable signals for AI agents.
      waitSubSlothEmulator = pkgs.writeShellScriptBin "wait-subsloth-emulator" ''
        set -euo pipefail

        if [ $# -gt 0 ] && ( [ "$1" = "-h" ] || [ "$1" = "--help" ] ); then
          echo "Usage: wait-subsloth-emulator"
          echo ""
          echo "Wait for an already-starting emulator to finish booting."
          echo "Same signals as the wait phase of start-subsloth-emulator."
          echo ""
          echo "Signals: WAITING_FOR_EMULATOR, BOOT_COMPLETED, EMULATOR_READY, EMULATOR_TIMEOUT"
          exit 0
        fi

        BOOT_TIMEOUT=120
        echo "WAITING_FOR_EMULATOR"
        for i in $(seq 1 $BOOT_TIMEOUT); do
          STATE=$(adb -e get-state 2>/dev/null || echo "unknown")
          if [ "$STATE" = "device" ]; then
            BOOT=$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || echo "")
            if [ "$BOOT" = "1" ]; then
              echo "BOOT_COMPLETED in ''${i}s"
              echo "EMULATOR_READY"
              exit 0
            fi
          fi
          sleep 1
        done

        echo "EMULATOR_TIMEOUT" >&2
        exit 1
      '';

      # ── run-subsloth-instrumented-test script ───────────────────────────────
      # Runs a single Gradle connectedAndroidTest task on the running emulator.
      # Usage: run-subsloth-instrumented-test <gradle-task-path> [additional-gradle-args]
      # Example: run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest
      runSubSlothInstrumentedTest = pkgs.writeShellScriptBin "run-subsloth-instrumented-test" ''
        set -euo pipefail

        if [ $# -gt 0 ] && ( [ "$1" = "-h" ] || [ "$1" = "--help" ] ); then
          echo "Usage: run-subsloth-instrumented-test <gradle-task-path> [gradle-args...]"
          echo ""
          echo "Run a single Gradle connectedAndroidTest task on the running emulator."
          echo ""
          echo "Example:"
          echo "  run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest"
          echo ""
          echo "Signals: TEST_RUNNER_STARTING, TEST_PASSED, TEST_FAILED"
          exit 0
        fi

        if [ $# -lt 1 ]; then
          echo "USAGE: run-subsloth-instrumented-test <gradle-task-path>" >&2
          echo "Example: run-subsloth-instrumented-test :core:preferences:connectedDebugAndroidTest" >&2
          echo "See --help for details." >&2
          exit 1
        fi

        TASK="$1"
        shift
        echo "TEST_RUNNER_STARTING"
        echo "Task: $TASK"

        if ./gradlew "$TASK" "$@"; then
          echo "TEST_PASSED"
          exit 0
        else
          echo "TEST_FAILED" >&2
          exit 1
        fi
      '';

      # ── stop-subsloth-emulator script ───────────────────────────────────────
      # Stops the running emulator cleanly.  Prints machine-parseable signals.
      stopSubSlothEmulator = pkgs.writeShellScriptBin "stop-subsloth-emulator" ''
        set -euo pipefail

        if [ $# -gt 0 ] && ( [ "$1" = "-h" ] || [ "$1" = "--help" ] ); then
          echo "Usage: stop-subsloth-emulator"
          echo ""
          echo "Stop the running '${avdName}' emulator cleanly."
          echo "Tries graceful adb shutdown first, then SIGTERM/SIGKILL."
          echo ""
          echo "Signals: EMULATOR_STOPPING, EMULATOR_STOPPED"
          exit 0
        fi

        echo "EMULATOR_STOPPING"

        # Try graceful adb shutdown first
        adb -e emu kill >/dev/null 2>&1 && true

        # Fallback: kill by process name.  Multiple emulator PIDs can exist
        # (the emulator process itself plus the qemu-system child).
        EMU_PIDS=$(pgrep -f "emulator.*-avd ${avdName}" 2>/dev/null || true)
        if [ -n "$EMU_PIDS" ]; then
          echo "Killing emulator PIDs: $EMU_PIDS"
          kill $EMU_PIDS 2>/dev/null || true
          sleep 2
          # Force kill survivors
          EMU_PIDS=$(pgrep -f "emulator.*-avd ${avdName}" 2>/dev/null || true)
          if [ -n "$EMU_PIDS" ]; then
            kill -9 $EMU_PIDS 2>/dev/null || true
          fi
        fi

        echo "EMULATOR_STOPPED"
      '';

      # ── Desktop app runtime libraries (Skiko/Compose) ──────────────────────
      # These Nix packages provide the native libraries that Skiko (Compose
      # Desktop's rendering engine) dlopen's at runtime: libGL, libX11,
      # libfontconfig, libstdc++, etc.  Each package's lib/ directory is added
      # to LD_LIBRARY_PATH so the dynamic linker resolves them without relying
      # on the system path (which can cause glibc version mismatch with Nix).
      desktopLibs = with pkgs; [
        # Skiko/Compose Desktop direct dependencies (libskiko.so has no RUNPATH)
        libglvnd     # libGL.so.1 — GL dispatch layer
        mesa         # libGLX_mesa.so.0 / libEGL_mesa.so.0 — GL impl (dlopen'd by libglvnd)
        libx11       # libX11.so.6 — X11 client library
        libxext      # libXext.so.6 — X11 extensions
        libxcb       # libxcb.so.1 — X11 protocol (needed by libGL but not in its RUNPATH)
        fontconfig   # libfontconfig.so.1 — font configuration
        # Wayland native rendering (optional — only loaded when $WAYLAND_DISPLAY is set)
        wayland      # libwayland-client.so.0, libwayland-egl.so.1
        libxkbcommon # libxkbcommon.so.0 — keyboard handling for Wayland
        # libstdc++.so.6 — C++ stdlib (already loaded by JDK at runtime)
      ];
      desktopLibPath = pkgs.lib.makeLibraryPath desktopLibs;

      # ── run-subsloth-instrumented-tests script ──────────────────────────────
      # Full one-shot pipeline: start emulator → wait for boot → run tests →
      # stop emulator.  All-in-one convenience for AI agents and humans.
      # Usage: run-subsloth-instrumented-tests <gradle-task-path>
      runSubSlothInstrumentedTests = pkgs.writeShellScriptBin "run-subsloth-instrumented-tests" ''
        set -euo pipefail

        if [ $# -gt 0 ] && ( [ "$1" = "-h" ] || [ "$1" = "--help" ] ); then
          echo "Usage: run-subsloth-instrumented-tests <gradle-task-path> [gradle-args...]"
          echo ""
          echo "One-shot pipeline: start emulator -> wait for boot -> run tests -> stop emulator."
          echo "If the emulator is already running, it is reused and NOT stopped afterward."
          echo ""
          echo "Example:"
          echo "  run-subsloth-instrumented-tests :core:preferences:connectedDebugAndroidTest"
          echo ""
          echo "Signals: PIPELINE_START, EMULATOR_ALREADY_RUNNING, PIPELINE_RUNNING_TESTS,"
          echo "         TEST_PASSED, TEST_FAILED, PIPELINE_PASSED, PIPELINE_FAILED"
          exit 0
        fi

        if [ $# -lt 1 ]; then
          echo "USAGE: run-subsloth-instrumented-tests <gradle-task-path>" >&2
          echo "Example: run-subsloth-instrumented-tests :core:preferences:connectedDebugAndroidTest" >&2
          echo "See --help for details." >&2
          exit 1
        fi

        TASK="$1"
        shift

        echo "=== PIPELINE_START ==="

        # Check if emulator is already running and booted
        WE_STARTED_EMU=false
        ADB_STATE=$(adb -e get-state 2>/dev/null || echo "unknown")
        if [ "$ADB_STATE" = "device" ]; then
          BOOT=$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n' || echo "")
          if [ "$BOOT" = "1" ]; then
            echo "EMULATOR_ALREADY_RUNNING"
          else
            echo "EMULATOR_BOOTING" >&2
            wait-subsloth-emulator
          fi
        else
          start-subsloth-emulator
          WE_STARTED_EMU=true
        fi

        # Ensure cleanup on any exit (success, failure, SIGINT, SIGTERM)
        if [ "$WE_STARTED_EMU" = true ]; then
          trap 'stop-subsloth-emulator' EXIT
        fi

        echo "PIPELINE_RUNNING_TESTS"
        if ./gradlew "$TASK" "$@"; then
          echo "TEST_PASSED"
          echo "=== PIPELINE_PASSED ==="
          exit 0
        else
          echo "TEST_FAILED" >&2
          echo "=== PIPELINE_FAILED ===" >&2
          exit 1
        fi
      '';
    in
    {
      devShells.${system}.default = pkgs.mkShell {
        packages = with pkgs; [
          # Development tooling
          openspec
          git
          vacuum-go

          # Android
          android-tools
          androidStudio
          androidSdk
          androidEmulator
          setupEmulator
          startSubSlothEmulator
          waitSubSlothEmulator
          runSubSlothInstrumentedTest
          stopSubSlothEmulator
          runSubSlothInstrumentedTests

          # Java toolchain
          openjdk25
          openjdk17

          # Node.js + Yarn + Binaryen (for Kotlin/Wasm webpack bundling)
          nodejs
          yarn
          binaryen

          # Utilities (not provided by stdenv)
          curl
          ripgrep
          unzip
          which
          zip

          # Desktop app runtime (Skiko/Compose native libraries)
        ] ++ desktopLibs;

        # JDK 25 runs the Gradle daemon because Metro requires at least 21.
        # JDK 17 powers the Kotlin/Java compile toolchain via JAVA17_HOME +
        # `org.gradle.java.installations.fromEnv` in gradle.properties.
        # See docs/jdk.md.
        JAVA_HOME = "${pkgs.openjdk25}";
        JAVA17_HOME = "${pkgs.openjdk17}/lib/openjdk";
        ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
        ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
        # Kotlin/Wasm toolchain — when set, the Kotlin/Wasm plugin uses an
        # existing Node.js/Yarn/Binaryen installation instead of downloading
        # one, which avoids the FAIL_ON_PROJECT_REPOS conflict.
        KOTLIN_NODEJS_HOME = "${pkgs.nodejs}";
        KOTLIN_YARN_HOME = "${pkgs.yarn}";
        KOTLIN_BINARYEN_HOME = "${pkgs.binaryen}";

        shellHook = ''
          # Desktop GL runtime (Skiko/Compose)
          # Nix packages provide all native deps: libglvnd+mesa (GL dispatch +
          # vendor implementation), X11, fontconfig, etc.
          old_path="''${LD_LIBRARY_PATH:-}"
          export LD_LIBRARY_PATH="${desktopLibPath}''${old_path:+:}''${old_path}"

          # Gradle daemon project property forwarding: ORG_GRADLE_PROJECT_*
          # env vars are passed from the client shell to the daemon by gradlew.
          # The desktopApp build reads this and forwards it to the forked JVM.
          export ORG_GRADLE_PROJECT_desktopLibPath="$LD_LIBRARY_PATH"

          # Add cmdline-tools to PATH (sdkmanager, avdmanager)
          CMDLINE_TOOLS_BIN="$ANDROID_HOME/cmdline-tools/17.0/bin"
          if [ -d "$CMDLINE_TOOLS_BIN" ]; then
            export PATH="$CMDLINE_TOOLS_BIN:$PATH"
          fi

          # Ensure a writable SDK root for sdkmanager operations
          export ANDROID_WRITABLE_SDK="${writableSdkRoot}"
          if [ ! -d "$ANDROID_WRITABLE_SDK/cmdline-tools" ]; then
            mkdir -p "$ANDROID_WRITABLE_SDK"
            cp -rs "$ANDROID_HOME/cmdline-tools" "$ANDROID_WRITABLE_SDK/" 2>/dev/null || \
            cp -r "$ANDROID_HOME/cmdline-tools" "$ANDROID_WRITABLE_SDK/" 2>/dev/null || true
          fi

          # Add writable SDK's cmdline-tools to PATH (comes after Nix's
          # so sdkmanager writes to the writable root by default).
          WRITABLE_CMDLINE_TOOLS_BIN="$ANDROID_WRITABLE_SDK/cmdline-tools/17.0/bin"
          if [ -d "$WRITABLE_CMDLINE_TOOLS_BIN" ]; then
            export PATH="$WRITABLE_CMDLINE_TOOLS_BIN:$PATH"
          fi

          # Detect missing setup
          if [ ! -d "${systemImageDir}" ]; then
            echo "⚠  System image not found. Run: setup-emulator"
          elif [ ! -f "$HOME/.android/avd/${avdName}.avd/config.ini" ]; then
            echo "⚠  AVD '${avdName}' not created. Run: setup-emulator"
          elif ! grep -q "${systemImageDir}" "$HOME/.android/avd/${avdName}.avd/config.ini" 2>/dev/null; then
            echo "⚠  AVD '${avdName}' has old relative system-image path. Run: setup-emulator"
          fi

          # Auto-generate local.properties from Nix SDK path
          # Updates only the sdk.dir line to preserve other properties (signing configs, etc.)
          if [ -n "$ANDROID_HOME" ]; then
            LOCAL_PROPERTIES="$(git rev-parse --show-toplevel 2>/dev/null || pwd)/local.properties"
            if grep -q '^sdk\.dir=' "$LOCAL_PROPERTIES" 2>/dev/null; then
              if ! grep -q "^sdk\.dir=$ANDROID_HOME$" "$LOCAL_PROPERTIES" 2>/dev/null; then
                echo "→ Updating local.properties SDK path"
                TEMP_PROPERTIES="$(mktemp)"
                grep -v '^sdk\.dir=' "$LOCAL_PROPERTIES" > "$TEMP_PROPERTIES" || true
                echo "sdk.dir=$ANDROID_HOME" >> "$TEMP_PROPERTIES"
                mv "$TEMP_PROPERTIES" "$LOCAL_PROPERTIES"
              fi
            else
              echo "→ Adding sdk.dir to local.properties"
              echo "sdk.dir=$ANDROID_HOME" >> "$LOCAL_PROPERTIES"
            fi
          fi

          echo "subsloth — emulator: ✓, sdk: $ANDROID_HOME"
          echo "  Emulator scripts: setup-emulator, start-subsloth-emulator, wait-subsloth-emulator, run-subsloth-instrumented-test, stop-subsloth-emulator"
          echo "  One-shot: run-subsloth-instrumented-tests <gradle-task>"
        '';
      };
    };
}
