## 1. Download Policy Tests

- [x] 1.1 Add download policy tests for storage reserve, metered networks, item queueing, sidecars, duplicate assets, and safe replacement.
- [x] 1.2 Add tests for offline home mode, local-file playback, missing/corrupt local file behavior, and shared offline metadata retention.
- [x] 1.3 Add tests for confirmation, preflight, per-episode quality/subtitle fallback, one active video download, explicit retries, cancellation, and restart persistence.
- [x] 1.4 Add tests for logout pause and login resume checks for incomplete queues.

## 2. App-Private Download Storage

- [x] 2.1 Implement app-private video and subtitle download storage with opaque paths and backup exclusion.
- [x] 2.2 Implement Room metadata for shared offline assets with indefinite retention rules.
- [x] 2.3 Verify downloaded media is not exposed through public storage, MediaStore, SAF, or external player handoff.

## 3. Item Downloads

- [x] 3.1 Implement item downloads with state tracking, partial cleanup, low-storage refusal, and metered confirmation.
- [x] 3.2 Implement shared video/sidecar reuse, safe higher-quality replacement, and ambiguous-quality refusal.
- [x] 3.3 Implement active download foreground-service and minimal notification behavior when required (`dataSync` foreground-service type).

## 4. Confirmed Season Queues

- [x] 4.1 Implement season preflight only after "Download season" selection.
- [x] 4.2 Implement per-episode quality fallback, subtitle fallback, confirmation summaries, and no "don't ask again" behavior.
- [x] 4.3 Implement confirmed sequential season queues and persisted queue resume without adaptive download concurrency.
- [x] 4.4 Implement logout pause and login resume checks for incomplete queues.

## 5. Verification

- [x] 5.1 Run `./gradlew :core:domain:jvmTest :core:media:jvmTest :feature:library:jvmTest :androidApp:assembleDebug`.
- [x] 5.2 Run manifest/lint checks for `dataSync` foreground-service type and notification permission behavior.
- [x] 5.3 Run `openspec validate offline-downloads --strict`.
