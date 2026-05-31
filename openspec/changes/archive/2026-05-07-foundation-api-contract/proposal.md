## Why

The initial planning material grew too large to execute safely in one pass. This change creates the v1 foundation for a greenfield Android app and locks the API discovery rules before DTOs, persistence, or UI depend on unstable assumptions.

## What Changes

- Add the project baseline for the native `subsloth` Android app.
- Add the Android toolchain, module, and dependency policy required before implementation.
- Add the Media Kodi-compatible API contract and discovery gate.
- Add fixture, OpenAPI, and optional local live-drift test expectations.
- Exclude comments, browser/WebView identity, web scraping, and web-only frontend resources from the native v1 data source.

## Capabilities

### New Capabilities

- `project`: Project identity, toolchain, module baseline, dependency policy, and distribution defaults.
- `api-contract`: Kodi-compatible Media API contract, discovery workflow, fixtures, and drift tests.

### Modified Capabilities

- None.

## Impact

- Affects `settings.gradle.kts`, root Gradle files, convention plugins, module build files, `api/subsloth.openapi.yaml`, API fixtures, and local API discovery docs.
- Establishes constraints that downstream changes must obey for networking, auth, UI, playback, downloads, diagnostics, and tests.
