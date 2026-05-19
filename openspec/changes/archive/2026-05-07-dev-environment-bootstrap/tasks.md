## 1. Environment Files

- [x] 1.1 Add `flake.nix` with a pinned nixpkgs input and one default development shell.
- [x] 1.2 Add `.envrc` so `direnv` activates the flake automatically after `direnv allow`.
- [x] 1.3 Add or update `flake.lock` so the toolchain is pinned to a specific nixpkgs revision.

## 2. Toolchain Coverage

- [x] 2.1 Include all baseline tools in the default shell: OpenSpec, Git, JDK 17, Gradle wrapper usage, Node, Bun, Android SDK command-line tools, Android platform tools, SDK manager access, and Android Studio.
- [x] 2.2 Export the standard Java and Android environment variables from the shell so Gradle and Android tooling work without extra manual setup.

## 3. Onboarding Documentation

- [x] 3.1 Add `docs/development.md` describing `direnv allow`, `nix develop`, and the single all-tools development shell.
- [x] 3.2 Document Android SDK license acceptance and the expected first-run bootstrap flow.

## 4. Verification

- [x] 4.1 Run `nix flake check` or an equivalent flake evaluation command to confirm the environment evaluates cleanly.
- [x] 4.2 Run `nix develop -c openspec --version`, `nix develop -c java -version`, `nix develop -c node -v`, `nix develop -c bun -v`, `nix develop -c sdkmanager --version`, `nix develop -c adb version`, `nix develop -c android-studio --version`, `nix develop -c bash -lc 'test -d "$ANDROID_HOME/platforms" && test -d "$ANDROID_HOME/build-tools" && test -d "$ANDROID_HOME/platform-tools"'`, and `nix develop -c ./gradlew -v` to verify the default shell and confirm `flake.nix` exports a valid `ANDROID_HOME`.
- [x] 4.3 Run `openspec validate dev-environment-bootstrap --strict`.
