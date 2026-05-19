## Why

The repository currently depends on a mix of host-installed tools and ad hoc setup, which makes the first development step fragile and inconsistent across machines. A checked-in Nix flake with direnv removes that setup drift and gives every contributor the same baseline before any Android implementation work begins.

## What Changes

- Add a reproducible repository dev environment built around Nix flakes and direnv.
- Add one comprehensive development shell for OpenSpec, Git, JDK 17, Gradle wrapper usage, Node, Bun, Android SDK tooling, and Android Studio.
- Add `.envrc` so the environment activates automatically after `direnv allow`.
- Keep the toolchain pinned to the project baseline instead of relying on whatever is installed on the host.

## Capabilities

### New Capabilities
- `dev-environment`: Reproducible local development shell, Android SDK tooling, Android Studio, direnv activation, and baseline toolchain pinning for this repository.

### Modified Capabilities
- None.

## Impact

- Affects root-level Nix and direnv files such as `flake.nix`, `flake.lock`, and `.envrc`.
- Affects local developer setup for Android, OpenSpec, Gradle wrapper, Java, Node, Bun, and Android Studio workflows.
- May add documentation for entering the shell and accepting Android SDK licenses.
