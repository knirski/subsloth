## 1. Persistence Tests

- [x] 1.1 Add tests for account profile key derivation, raw login exclusion, account-scoped isolation, and shared offline visibility.
- [x] 1.2 Add tests for logout default retention and each optional cleanup scope.
- [x] 1.3 Add tests proving logout cleanup never calls Media server mutation endpoints.

## 2. Database and Preferences

- [x] 2.1 Add Room entities and DAOs for account-scoped cache/library/progress data.
- [x] 2.2 Add shared offline entities and DAOs for downloads, offline display metadata, and shared offline progress.
- [x] 2.3 Add DataStore preferences for subtitle, quality, speed, download, and cache timestamp settings.
- [x] 2.4 Add account profile salt storage and HMAC-SHA256 profile key derivation with same-account profile reuse tests.

## 3. Credentials

- [x] 3.1 Add Android Keystore-backed credential save/read/clear behavior.
- [x] 3.2 Add backup exclusion rules for credential files and key material.
- [x] 3.3 Add tests that credentials are separate from profile data and shared offline data.

## 4. App Shell and Auth UI

- [x] 4.1 Add app manifest, application class, main activity, navigation graph, and sensitive-screen policy.
- [x] 4.2 Add login UI with standard Autofill/password-manager support and no custom clipboard behavior.
- [x] 4.3 Add auth repair and recoverable unexpected-service-state UI.
- [x] 4.4 Add logged-out Offline Library entry behavior when playable shared downloads exist.

## 5. Verification

- [x] 5.1 Run `./gradlew :core:database:test :core:preferences:test :feature:auth:test :app:assembleDebug`.
- [x] 5.2 Run Android Keystore instrumented credential tests on emulator/device (also runs automatically in CI via `android-emulator-runner`).
- [x] 5.3 Run `openspec validate auth-persistence-shell --strict`.
