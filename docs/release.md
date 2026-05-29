# Release Process

## Overview

Releases are managed by [semantic-release](https://github.com/semantic-release/semantic-release). It runs on every push to `main`, analyzes conventional commits, determines the next version, creates a git tag (`vX.Y.Z`), and publishes a GitHub Release with auto-generated release notes.

## Version Management

- **Git tags**: The authoritative source of truth for released versions (`vX.Y.Z`).
- **Android versionName**: Derived from the latest git tag via `git describe --tags --abbrev=0 --match=v*` at build time. Falls back to `0.0.0` if no tag is found.
- **Android versionCode**: Derived deterministically from SemVer components: `$MAJOR * 1000000 + $MINOR * 1000 + $PATCH`.

## Release Workflow

 1. A maintainer merges changes to `main` using conventional commits (squash + merge).
 2. The PR title becomes the commit message on `main`, and `pr-title.yml` enforces the conventional commit format.
 3. `semantic-release.yml` runs on push to `main`, sets up JDK 25 + JDK 17, analyzes commits since the last tag, determines the next version, builds the debug APK via `:app:assembleDebug`, and creates a GitHub Release with the APK attached and tag `vX.Y.Z`.
 4. Release notes are auto-generated from conventional commit messages and available in the GitHub Release.

No commits are pushed back to `main` during the release process. The git tag and GitHub Release are the source of truth.

APK Artifact

A debug-signed sideload APK (`subsloth-{version}-debug.apk`) named with the release version is built during the release pipeline and uploaded as a release asset. Download it from the Assets section of the GitHub Release page.

### Manual Install / Update
1. Download the APK from the GitHub Release page.
2. Transfer it to your Android device (phone, tablet, or TV).
3. Open the APK file on the device and follow the system prompts to install.
4. If updating an existing installation, the system handles the upgrade automatically.

### Rollback
1. Uninstall the current version from the device.
2. Download and install the previous APK from an earlier GitHub Release.

## Release Scope

v1 releases produce **debug-signed APKs for internal sideloading only**. Dedicated release signing and public distribution (e.g., Google Play Store) are deferred.

## Important Notes

- **No in-app update check**: v1 does not include an automatic update mechanism. Discover new releases manually via the GitHub Releases page.
- **No Play Store distribution**: APKs are not published to any app store in v1.
- **Release discovery**: Manual via GitHub Releases — there is no in-app notification for new versions.
- **Release notes**: Available in the GitHub Release description. There is no `CHANGELOG.md` file in the repository.
