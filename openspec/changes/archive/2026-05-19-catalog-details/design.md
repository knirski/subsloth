## Context

Metadata comes only from Kodi-compatible Media API responses and local caches. Comments are intentionally unsupported. Catalog refreshes follow a 24h/7d staleness model with cached/offline data shown first.

## Goals

- Show cached/offline data first while authenticated catalog refreshes occur.
- Provide catalog browsing and search with filters backed by available API/cache fields.
- Preserve Media movie, series, season, episode, subtitle, quality, user-state, and upcoming metadata.
- Honest recency labels.

## Non-Goals

- Do NOT implement playback controls, download queues, storage management, diagnostics, or release workflows here.
- Do NOT call comments endpoints or web-only frontend resources.
- Do NOT scrape artwork.
- Do NOT define cross-cutting adaptive layout, TV focus, or accessibility primitives — those live in `android-ui-foundation`.

## Decisions

- UDF ViewModels with immutable UI state for state restoration and tests.
- One content model with device-specific layouts driven by `android-ui-foundation` primitives.
- Honest recency labels matched to the available data signal.
- Demand-driven artwork loading from returned Kodi-compatible URLs only.

## Risks

- Recency may be imprecise if Media lacks added timestamps — use honest fallback labels or hide the row.
- Filter/sort query support depends on Kodi parity — unsupported remote filters must be local-only or hidden.

## Migration Plan

1. Add ViewModel tests for catalog, search, detail, recency, no-comments, and state restoration.
2. Implement home/search rows and filters.
3. Implement movie and series detail screens using `android-ui-foundation` primitives.
4. Add artwork loader.

## Open Questions

- Exact filter/sort query support remains gated by API discovery and Kodi parity.
