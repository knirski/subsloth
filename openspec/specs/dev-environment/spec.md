# dev-environment Specification

## Purpose
Define the repository's single reproducible local development environment so contributors can enter the same pinned Nix shell with direnv or `nix develop` and use the documented Android, Java, Node, Bun, Git, and OpenSpec toolchain without ad hoc host setup.
## Requirements
### Requirement: Single Flake Environment
The repository SHALL provide one direnv-driven Nix flake shell for local development.

#### Scenario: Repo activation is automatic
- **WHEN** a developer allows the repository's `.envrc` and enters the repo directory
- **THEN** direnv loads the default flake shell without requiring manual tool installation steps for this project

#### Scenario: Activation is reproducible
- **WHEN** the repository environment is re-entered on another machine with the same flake lock
- **THEN** the same pinned shell definition is used

#### Scenario: Core tools are available
- **WHEN** a developer enters the default shell
- **THEN** `openspec`, `git`, `java`, `./gradlew`, `node`, `bun`, Android SDK command-line tools, Android platform tools, SDK manager access, and Android Studio are available through the shell environment

#### Scenario: Android environment variables are set
- **WHEN** a developer enters the default shell
- **THEN** the shell exports the Android and Java environment variables required for local Gradle and Android SDK usage

#### Scenario: No alternate shell is required
- **WHEN** a developer follows the documented bootstrap path
- **THEN** they use the same default shell for CLI and IDE work

### Requirement: Pinned Toolchain Baseline
The repository SHALL pin the dev-environment toolchain through the flake lock so contributors share the same baseline packages.

#### Scenario: Toolchain is stable across machines
- **WHEN** the flake lock is updated and committed
- **THEN** the shell resolves against the same pinned Nixpkgs revision until the lock is intentionally changed

#### Scenario: Baseline follows project tooling
- **WHEN** the dev environment is entered
- **THEN** the shell uses the repository's baseline Java, Android, Node, Bun, and OpenSpec tool versions rather than arbitrary host-installed versions
