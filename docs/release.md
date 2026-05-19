# Release Process

## Overview

Releases are managed by [release-please](https://github.com/googleapis/release-please) using `release-type: simple`. A single version for the entire repository is tracked in `version.txt` at the repository root.

## Version Management

- **version.txt**: Contains the current version in SemVer format (e.g., `0.1.0`).
- **Android versionCode**: Derived deterministically from SemVer components: `$MAJOR * 1000000 + $MINOR * 1000 + $PATCH`.
- **Android versionName**: Read directly from `version.txt`.

## Release Workflow

1. A maintainer merges changes to `main` using conventional commits.
2. The `release-please.yml` GitHub Actions workflow detects release-worthy commits and opens or updates a Release PR.
3. When the Release PR is merged, release-please creates a GitHub Release with tag `vX.Y.Z` and generated changelog.
4. The workflow builds `assembleDebug` and uploads the APK as a release artifact.

## APK Artifact

The debug-signed sideload APK is named:
```
subsloth-vX.Y.Z-debug-<shortsha>.apk
```

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

## Changelog

The `CHANGELOG.md` at the repository root is maintained automatically by release-please. Every merge to `main` that uses a conventional commit prefix (`feat:`, `fix:`, `docs:`, `chore:`, `refactor:`) may appear in the next release notes.

## Important Notes

- **No in-app update check**: v1 does not include an automatic update mechanism. Discover new releases manually via the GitHub Releases page.
- **No Play Store distribution**: APKs are not published to any app store in v1.
- **Release discovery**: Manual via GitHub Releases — there is no in-app notification for new versions.
