## Why

After all feature changes are implemented, the project needs end-to-end verification: architecture boundary tests, UI/TV/accessibility tests, screenshot coverage, performance benchmarks, baseline profiles, manual device acceptance, and the final no-comments invariant scan. CI infrastructure landed earlier in `release-and-ci-foundation`; this change is the final-gate verification slice.

## What Changes

- Add core architecture boundary tests across all modules.
- Add domain, mapper, request identity, retry, no-comments, and unexpected-response tests.
- Add Room, DataStore, credential, backup-exclusion, logout, and ViewModel tests.
- Add media, playback, offline, download, storage, and process-restoration tests.
- Add Compose UI, TV D-pad focus, accessibility, and Roborazzi screenshot coverage.
- Add baseline profiles and macrobenchmarks.
- Add manual device acceptance documentation for Android TV 8, Android tablet 13, and Android phone 16.
- Add final no-comments invariant scan and full-app verification.

## Capabilities

### Modified Capabilities

- `testing-release`: extends the CI/release scope established in `release-and-ci-foundation` with end-to-end verification expectations.

## Impact

- Affects `:app`, `:core:*`, `:feature:*`, benchmark module, baseline profile module, `docs/testing/device-acceptance.md`. Depends on all earlier changes (foundation-api-contract, release-and-ci-foundation, core-domain-network, auth-persistence-shell, android-ui-foundation, catalog-details, playback, offline-downloads, library-settings-diagnostics) being implemented.
