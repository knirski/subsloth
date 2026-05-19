# SubSloth

<img src="docs/subsloth-mascot.svg" width="180" height="180" align="right" alt="SubSloth mascot" />

> 🚧 **Work in progress.** This project is under active development. Features are incomplete and APIs may change.

Learn languages by watching. Native Android streaming client with dual subtitles, built with modern Android tooling.

## Architecture

Functional Core / Imperative Shell, with typed domain models, sealed errors, and strict mapper boundaries between the API layer and app decisions.

- **UI:** Jetpack Compose with adaptive layouts, TV focus, and accessibility
- **Networking:** Metro HTTP client with single-flight, bounded retry, and rate-limit handling
- **Storage:** Room (SQLite), DataStore (preferences), Android Keystore (credentials)
- **Build:** Gradle with Kotlin DSL and version catalogs
- **Dev environment:** Nix flake with pinned JDK 25/17, Android SDK 36, and bundled Android Studio

## Features

- Learn languages through video content with dual subtitles
- Catalog browsing with search, filters, and sort
- Movie and series detail views with episode structure
- Video playback with subtitles, quality selection, and resume
- Offline downloads with queue management and storage safety
- Library, central Downloads, settings, and diagnostics

## Getting Started

```bash
# Activate the development environment (requires Nix with flakes enabled)
direnv allow            # or run non-interactively: nix develop --command <cmd>

# Build and test
./gradlew build

# Run on a connected device or emulator
./gradlew installDebug
```

See [`docs/development.md`](docs/development.md) for detailed setup instructions, [`docs/jdk.md`](docs/jdk.md) for JDK toolchain notes, and [`docs/agent/emulator-testing.md`](docs/agent/emulator-testing.md) for instrumented test workflow.

## Planning & Specs

This project uses [OpenSpec](https://github.com/anthropics/openspec) as the source of truth for product and engineering requirements.

- Start with [`openspec/README.md`](openspec/README.md) for the planning workflow.
- Active requirements live in `openspec/changes/*/specs/`.
- Archived (completed) changes move to `openspec/changes/archive/` and promote their specs into `openspec/specs/`.
- Step-level implementation detail is preserved in `docs/archive/superpowers/plans/`.

## License

Apache 2.0 — see [LICENSE](LICENSE).
