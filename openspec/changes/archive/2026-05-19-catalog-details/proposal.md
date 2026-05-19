## Why

Catalog browsing, search, recency rows, movie and series detail, episode structure, and comments-exclusion are a cohesive feature slice independent from playback, downloads, and settings.

## What Changes

- Catalog home, search, filters, sort, and honest recency rows.
- Movie detail content.
- Series detail content.
- Episode row content.
- Artwork loading rules.
- Startup refresh and cache staleness.
- Comments-absent invariant.

## Capabilities

### New Capabilities

- `catalog-details`: Home, catalog, search, movie detail, series detail, episode structure, artwork, recency, and comments exclusion.

### Modified Capabilities

- None.

## Impact

- Affects `:feature:catalog`, `:feature:details`, image loading, ViewModel tests, and Compose tests.
- Depends on `core-domain-network` (catalog/detail ports), `auth-persistence-shell` (login state and app shell), and `android-ui-foundation` (adaptive layouts and TV focus primitives).
- Can be developed in parallel with `playback`, `offline-downloads`, and `library-settings-diagnostics` once the foundation lands.
