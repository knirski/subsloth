## 1. Offline CI

- [x] 1.1 Add offline-only required CI workflow at `.github/workflows/ci.yml` using JDK 25 for the Gradle runtime, JDK 17 for the Kotlin/Java compile toolchain, and the Gradle wrapper, running `gradle/wrapper-validation-action`, `:core:network:openApiValidate` before `check`/`testDebugUnitTest` so mapper and domain tests run against generated models, `lintDebug`, and `assembleDebug`.
- [x] 1.2 Add secret/artifact scanning covering Media credentials, Basic auth headers, signed media URLs, `.playwright-cli/`, HAR files, browser traces, screenshots, and signed APK material; plus invariant checks for no-comments and Kodi-compatible request behavior.
- [x] 1.3 Document local-only live drift execution with the exact command `SUBSLOTH_LOGIN=… SUBSLOTH_PASSWORD=… ./gradlew :core:network:test`, clarify that missing env vars cause skips not failures, and explicitly avoid creating a GitHub live-drift workflow in v1.

## 2. Release Please and Versioning

- [x] 2.1 Add `version.txt` and `CHANGELOG.md` at the repository root. Wire the `:app` Gradle build to read `version.txt` for `versionName` and compute `versionCode` deterministically from SemVer components (e.g. `MAJOR*10000 + MINOR*100 + PATCH`).
- [x] 2.2 Add release-please workflow at `.github/workflows/release-please.yml` using `googleapis/release-please-action@v4`, `release-type: simple`, and a dedicated `RELEASE_PLEASE_TOKEN` PAT or GitHub App token (the default `GITHUB_TOKEN` is not used).
- [x] 2.3 Gate the `assembleDebug` and APK upload job on `needs.release-please.outputs.release_created == 'true'` and on the existence of `app/`. Name the artifact `subsloth-vX.Y.Z-debug-<shortsha>.apk`.
- [x] 2.4 Document that dedicated release signing and public distribution are deferred.

## 3. Documentation

- [x] 3.1 Add `docs/development.md` covering JDK 25 runtime, JDK 17 toolchain, Android Studio latest stable, Gradle wrapper, local live-drift env vars, offline test execution, and an explicit note that JDK 25 is the project baseline unless a later OpenSpec change says otherwise and full verification passes.
- [x] 3.2 Add `docs/release.md` covering release-please flow, APK naming, manual sideload install/update, rollback, changelog expectations, and that release discovery happens manually via GitHub Releases (no in-app update check).

## 4. Verification

- [x] 4.1 Confirm CI workflow validates with a local act-style dry-run or syntax check.
- [x] 4.2 Run `./gradlew check lintDebug testDebugUnitTest assembleDebug` to confirm CI commands work locally.
- [x] 4.3 Run `openspec validate release-and-ci-foundation --strict`.
