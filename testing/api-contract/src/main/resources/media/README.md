# Media API Fixtures — Programmatic Replay Inputs

This directory and its subdirectories contain fixture files that the programmatic WireMock stubs replay for captured Media API traffic.

## File Layout

| Directory | Contents | Canonical Source |
|-----------|----------|------------------|
| `web-discovery/` | Web-only discovery fixtures (Comments, FavoriteMedia, Statistics, CatalogFilters, SubtitleDownload) | This directory |
| `media/` (top-level) | Native Kodi-compatible contract fixtures (Movies, Shows, MovieDetail, ShowDetail, EpisodeDetail) | This directory |

All fixture files live in this module's `src/main/resources/media/` directory. Consumers load them via a standard project dependency:

```kotlin
testImplementation(project(":testing:api-contract"))
```

No separate mapping files are generated — WireMock stubs are registered programmatically from the fixture files by `WireMockServerFactory`.

The `web-discovery/` fixtures are exclusive to this module's web-discovery test suite.

## Sanitization

All fixture files have been sanitized per the project policy defined in `scripts/capture/sanitization-rules.json`:

- No credentials, auth headers, signed stream/download URLs, or private account data
- All URLs use the `.invalid` IETF-reserved TLD
- Human-readable values are rewritten to retain the response shape without mirroring real account data
- IDs may be real; fields that contain account-specific data are anonymized

## Contract Validation

Programmatic WireMock stubs are verified by `MockMappingVerificationTest`.
Web-discovery fixture content is validated by `WebDiscoveryFixtureTest`.
