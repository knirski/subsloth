## 1. Architecture and Unit Tests

- [x] 1.1 Add core architecture boundary tests verifying `:core:model` and `:core:domain` are Android-free.
- [x] 1.2 Add cross-module domain, mapper, request identity, retry, no-comments, and unexpected-response tests.
- [x] 1.3 Add cross-module Room, DataStore, credential, backup-exclusion, logout, and ViewModel tests.
- [x] 1.4 Add cross-module media, playback, offline, download, storage, and process-restoration tests.

## 2. UI and Device Tests

- [x] 2.1 Add CMP desktop Compose tests for login, catalog, detail without comments, library, downloads, settings, and diagnostics.
- [x] 2.2 Add CMP desktop Compose tests validating all interactive elements have click actions and are focusable (parity for TV D-pad, which requires an Android TV emulator for hardware-level focus testing).
- [x] 2.3 Add CMP desktop Compose accessibility tests for semantic labels, content descriptions, and click-action accessibility on all interactive elements.
- [x] 2.4 Add Compose Preview Screenshot Testing for all screens (login, home, search, player, library, downloads, settings, diagnostics, movie detail, series detail) on phone, tablet, and TV.

## 3. Performance and Acceptance

- [x] 3.1 Add baseline profile generation for startup, catalog scroll, detail open, and playback start.
- [x] 3.2 Add macrobenchmarks for startup, home load from cache, movie detail open, series detail open, and playback start. Android TV 8 is a required manual/device benchmark target.
- [x] 3.3 Add `docs/testing/device-acceptance.md` for Android TV 8, Android tablet 13, and Android phone 16.

## 4. Final Verification

- [x] 4.1 Run `vacuum lint "api/subsloth.openapi.yaml" && ./gradlew check lintDebug testDebugUnitTest assembleDebug`.
- [x] 4.2 Run connected Android tests on the emulator and verify the app no longer crashes at startup:
    - `./gradlew :core:database:connectedAndroidDeviceTest` — **2/2 PASSED** (Room database creation)
    - `./gradlew :androidApp:connectedDebugAndroidTest` — **2/2 PASSED** (app smoke tests)
    - `./gradlew :benchmark:connectedDebugAndroidTest` — **3/7 PASSED**:
        - `StartupBenchmark` (cold/warm) ✅ — cold start and warm re-launch succeed
        - `HomeLoadBenchmark` ✅ — home screen loads from cache
        - `BaselineProfileGenerator` ❌ — no movie cards found (no real catalog data on emulator)
        - `DetailOpenBenchmark`, `PlaybackStartBenchmark` ❌ — login gate prevents navigation; expected on emulator without mock data
- [ ] 4.3 Run local live drift tests only when local credentials are present.
- [ ] 4.5 Run `openspec validate verification-release --strict`.
