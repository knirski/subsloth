## Context

The repository is still in pre-implementation shape, but it already has a fixed Android baseline in OpenSpec and a checked-in Gradle wrapper. What it does not yet have is a reproducible local bootstrap path for the rest of the toolchain. Today that means contributors must assemble OpenSpec, Git, Java, Node, Bun, Android SDK tooling, and Android Studio support by hand before they can start development.

This change adds a first-class local environment entrypoint so `direnv` + `nix develop` become the standard way to enter the repository. The environment must remain aligned with the project baseline and should avoid introducing extra mutable state outside the repo.

## Goals / Non-Goals

Goals:

- Provide one reproducible development shell before Android implementation starts.
- Include OpenSpec, JDK 17, Gradle wrapper support, Node, Bun, Android SDK tooling, SDK manager access, and Android Studio in that shell.
- Pin the environment through `flake.lock`.
- Activate the shell through direnv after `direnv allow`.

Non-goals:

- Do not implement app features, Gradle modules, or Android build logic here.
- Do not change app behavior requirements.
- Do not add extra named Nix shells.
- Do not use host-managed package installs as the primary setup path.

## Decisions

- Use checked-in `flake.nix`, `flake.lock`, and `.envrc` as the canonical setup.
- Provide only `devShells.default`; it includes CLI tools, Android SDK tooling, SDK manager access, and Android Studio.
- Compose the Android SDK through `androidenv.composeAndroidPackages` so Gradle and Android Studio share the same SDK root.
- Export `JAVA_HOME`, `ANDROID_HOME`, and `ANDROID_SDK_ROOT` from the shell.
- Defer precomposed emulator system images until the app scaffold exists and device targets are verified.

## Risks / Trade-offs

- [Large SDK download size] -> Accept the larger single-shell environment and defer emulator system images.
- [Android SDK package lag] -> Update the flake lock or SDK composition intentionally when the project baseline changes.
- [direnv setup friction] -> Keep `.envrc` minimal and document `direnv allow`.

## Migration Plan

1. Add `flake.nix`, `flake.lock`, and `.envrc`.
2. Document `direnv allow`, `nix develop`, Android SDK license expectations, and the single-shell tool list.
3. Verify the default shell exposes the required tools.
4. If the environment proves unstable, remove the flake and direnv files and return to documented host setup.

## Open Questions

- None.
