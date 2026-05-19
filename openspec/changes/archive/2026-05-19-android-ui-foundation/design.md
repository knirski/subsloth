## Context

The app must treat phone, tablet, and Android TV as equal first-class targets. UI behavior must be consistent across features. State restoration, predictive back, and TV focus are easy to break if they are not enforced as a baseline.

## Goals

Establish shared adaptive layout primitives, TV focus rules, accessibility requirements, system UI handling, navigation state restoration, and predictive back support that all feature screens consume.

## Non-Goals

- Do NOT implement specific feature screens (catalog, details, player, library, settings) here — those belong in their own changes.
- Do NOT define content models or feature ViewModels.

## Decisions

- Use Compose for TV components on TV.
- Use Material 3 Adaptive layouts for phone/tablet.
- Use deterministic focus order with focus restoration after dialogs/back navigation.
- Use edge-to-edge by default with safe handling for system bars, cutouts, and TV overscan-safe spacing.
- Restore navigation destination, selected tabs, search query, player state, and download queue state across process death where the backing data remains available.
- Pin Material 3 Adaptive `1.2.0`, TV Foundation `1.0.0-rc01`, and TV Material `1.0.1` as the v1 baseline. `androidx.tv:tv-foundation:1.0.0-rc01` is the only approved non-stable pin in v1; revisit when a stable release ships.

## Risks

- TV focus failures are easy to miss in unit tests — include a Compose/TV focus harness as part of this change.
- Predictive back behavior varies by Android version — gate gracefully.

## Migration Plan

1. Add adaptive layout primitives and theme.
2. Add TV focus utilities and overscan-safe spacing primitives.
3. Add navigation state restoration support.
4. Add predictive back integration.
5. Add Compose/TV focus test harness for downstream features to use.

## Open Questions

- Which Material 3 Adaptive APIs are stable enough for v1 — gated on dependency review.
