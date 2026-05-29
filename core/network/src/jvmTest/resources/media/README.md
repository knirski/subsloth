# Media API Fixtures

These fixtures contain sanitized response shapes from the Media Kodi-compatible API.

## Location

The canonical fixtures live in `:testing:api-contract` (`testing/api-contract/src/main/resources/media/`)
and are consumed here via `testImplementation(project(":testing:api-contract"))`.

This directory exists only as a bridge — the source-of-truth fixtures are in the module above.

## Files

| File | Endpoint | Type |
|------|----------|------|
| `Movies.json` | `GET /movies` | Movie list response |
| `Shows.json` | `GET /shows` | Show list response |
| `MovieDetail.json` | `GET /movies/{movieId}` | Movie detail response |
| `ShowDetail.json` | `GET /shows/{showId}` | Flat show detail response |
| `EpisodeDetail.json` | `GET /episodes/{episodeId}` | Episode playback detail |

## Sanitization

All fixtures have been sanitized and obfuscated per the project policy:

- No credentials, auth headers, signed stream URLs, signed download URLs, or private account data
- No raw browser logs, HAR files, snapshots, or authenticated screenshots
- URLs are replaced with plausible placeholder values on non-existent hosts such as `.invalid`
- Human-readable values are rewritten to retain the response shape without mirroring real account data
- IDs may be real; fields that contain account-specific data are anonymized

## Contract Validation

The fixtures are validated by `FixtureTest` through typed JSON decoding against the handwritten models in `:core:network`.
