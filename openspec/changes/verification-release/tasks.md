## 1. Architecture and Unit Tests

- [ ] 1.1 Add core architecture boundary tests verifying `:core:model` and `:core:domain` are Android-free.
- [ ] 1.2 Add cross-module domain, mapper, request identity, retry, no-comments, and unexpected-response tests.
- [ ] 1.3 Add cross-module Room, DataStore, credential, backup-exclusion, logout, and ViewModel tests.
- [ ] 1.4 Add cross-module media, playback, offline, download, storage, and process-restoration tests.

## 2. UI and Device Tests

- [ ] 2.1 Add Compose tests for login/logout, catalog, details without comments, player controls, offline library, Downloads, storage, settings, and diagnostics, including process-death state restoration for main navigation and remote media key handling where practical.
- [ ] 2.2 Add TV D-pad focus tests for browse, detail, player, library, Downloads, and dialogs.
- [ ] 2.3 Add accessibility tests for labels, contrast-critical states, large text, focus visibility, touch targets, and remote-only operation.
- [ ] 2.4 Add Roborazzi screenshots for movie and series detail on phone, tablet, and TV.

## 3. Performance and Acceptance

- [ ] 3.1 Add baseline profile generation for startup, catalog scroll, detail open, and playback start.
- [ ] 3.2 Add macrobenchmarks for startup, home load from cache, movie detail open, series detail open, and playback start. Android TV 8 is a required manual/device benchmark target.
- [ ] 3.3 Add `docs/testing/device-acceptance.md` for Android TV 8, Android tablet 13, and Android phone 16.

## 4. Final Verification

- [ ] 4.1 Run `vacuum lint "api/subsloth.openapi.yaml" && ./gradlew check lintDebug testDebugUnitTest assembleDebug`.
- [ ] 4.2 Run `./gradlew connectedDebugAndroidTest :core:preferences:connectedDebugAndroidTest` plus benchmark connected tests on configured emulators/devices.
- [ ] 4.3 Run local live drift tests only when local credentials are present.
- [ ] 4.4 Run `rg -n "comment|comments|spoiler" app core feature testing` and verify production support is absent.
- [ ] 4.5 Run `openspec validate verification-release --strict`.
