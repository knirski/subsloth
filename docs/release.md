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
 3. `semantic-release.yml` runs on push to `main` in three ordered jobs (all set up JDK 17 via the shared `kmp-setup` action — CI does not use JDK 25):
    - `prepare` computes the next version with a semantic-release dry run; no tag, release, or notes are created.
    - `build` assembles the Android APK, Linux desktop package, and web distribution with that version, verifies the Android signing certificate when the release keystore secrets are configured, and stages each artifact for publication.
    - `publish` runs semantic-release for real, checks that the published tag matches the prepared version, uploads the staged assets with a `SHA256SUMS` file, and deletes the release and tag again if any post-publication step fails.

    A failed build therefore never leaves a published release with missing assets.
 4. Release notes are auto-generated from conventional commit messages and available in the GitHub Release.

No commits are pushed back to `main` during the release process. The git tag and GitHub Release are the source of truth.

## APK Artifact

A sideload APK named `subsloth-{version}-release.apk` is built during the release pipeline and uploaded with the other release assets. When the `ANDROID_KEYSTORE_*` secrets are configured, the APK is release-signed and its certificate is verified against the digest pinned in `.github/release-signing-sha256.txt`; without them the pipeline falls back to the standard debug keystore, so the artifact is for internal sideloading only. Download it from the Assets section of the GitHub Release page.

### Manual Install / Update
1. Download the APK from the GitHub Release page.
2. Transfer it to your Android device (phone, tablet, or TV).
3. Open the APK file on the device and follow the system prompts to install.
4. If updating an existing installation, the system handles the upgrade automatically.

### Rollback
1. Uninstall the current version from the device.
2. Download and install the previous APK from an earlier GitHub Release.

## Release Scope

Release APKs are release-signed when the `ANDROID_KEYSTORE_*` secrets are configured and verified against the pinned certificate digest; otherwise they are debug-signed and internal-only. Public distribution (e.g., Google Play Store) is out of scope.

## Important Notes

- **No in-app update check**: v1 does not include an automatic update mechanism. Discover new releases manually via the GitHub Releases page.
- **No Play Store distribution**: APKs are not published to any app store in v1.
- **Release discovery**: Manual via GitHub Releases — there is no in-app notification for new versions.
- **Release notes**: Available in the GitHub Release description. There is no `CHANGELOG.md` file in the repository.
