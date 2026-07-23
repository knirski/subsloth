# testing-release Specification (delta)

## RENAMED Requirements

- FROM: `### Requirement: Release Please`
- TO: `### Requirement: Release Mechanism`

## MODIFIED Requirements

### Requirement: Release Mechanism
The repository SHALL use semantic-release, triggered on push to `main`, to determine the next SemVer version from conventional commits, create a `vX.Y.Z` git tag when a release-worthy commit exists, and publish a GitHub Release with auto-generated notes. The repository SHALL NOT require `release-please`, a committed `version.txt`, or a maintained `CHANGELOG.md` file as part of this mechanism.

#### Scenario: Release is created after a push to main
- **WHEN** a conventionally-titled pull request is squash-merged to `main`
- **THEN** `semantic-release.yml` runs semantic-release, which creates a `vX.Y.Z` tag when a release-worthy commit exists and publishes a GitHub Release with generated notes, without pushing any commit back to `main`

#### Scenario: Android version derives from the release tag
- **WHEN** the `:androidApp` Gradle build resolves version metadata
- **THEN** `versionName` is derived from `git describe --tags --abbrev=0 --match=v*` (falling back to `0.0.0` when no tag exists) and `versionCode` is computed deterministically from the SemVer components

#### Scenario: Release notes have no separate changelog file
- **WHEN** a developer looks for release notes
- **THEN** they are read from the GitHub Release description; the repository does not maintain a `CHANGELOG.md` file
