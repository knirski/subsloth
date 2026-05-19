## Context

The app needs one central place for offline media and queue state across movies, episodes, and confirmed season queues. Settings and diagnostics must expose useful local state while avoiding credential, URL, request, path, and account leakage.

## Goals / Non-Goals

Goals:

- Give users clear visibility and recovery actions for active, queued, paused, failed, unavailable, and completed downloads.
- Make TV Downloads remote-friendly and confirmation-based.
- Keep destructive storage actions explicit and scoped.
- Keep diagnostics local-only, redacted, and view-only.

Non-goals:

- Do not implement diagnostics export, share, upload, copy-to-clipboard, bug-report bundles, HAR creation, screenshot creation, traces, or raw artifact generation.
- Do not add new-episode notification settings in v1.
- Do not implement in-app update checks or GitHub Releases API calls.

## Decisions

- Use one Downloads model for phone, tablet, and TV because queue state and storage actions should not diverge by device.
- Restrict TV destructive actions to simple choices because complex multi-select/filter-builder deletion is poor remote-control UX.
- Put detailed request-shape verification in tests and discovery docs, not diagnostics UI, because diagnostics must not reveal endpoints, headers, query params, bodies, redirects, auth scheme details, or User-Agent values.

## Risks / Trade-offs

- Diagnostics may be too sparse for debugging -> keep typed status categories, cache ages, counts, and storage sizes, but require a separate design before export/share/copy.
- Storage deletion mistakes are high impact -> require explicit confirmation and state that shared offline progress for deleted media is removed.
- Downloads screen can become dense -> group by state and keep TV action menus simple.

## Migration Plan

1. Add library/settings tests for data rows, logout cleanup, diagnostics redaction, and absence of unsupported features.
2. Implement library and central Downloads screens.
3. Implement storage management and confirmations.
4. Implement settings and diagnostics.
5. Add TV focus/accessibility coverage in the verification change.

## Open Questions

- Whether later releases support diagnostics export/share depends on a separate sanitizer design and tests.

