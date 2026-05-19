## Why

Phone, tablet, and Android TV are equal first-class targets. Adaptive layout, TV focus, accessibility, edge-to-edge handling, predictive back, and state restoration are cross-cutting UI rules that every feature change must comply with. Landing them as a foundation before catalog/playback/library/settings ensures consistent UI behavior and unblocks parallel feature development.

## What Changes

- Phone single-pane layouts.
- Tablet adaptive list-detail layouts.
- TV D-pad-friendly layouts with deterministic focus and overscan-safe spacing.
- Accessibility and input rules.
- Edge-to-edge UI with system bars and cutouts handling.
- Navigation state restoration after process death.
- Predictive back support.

## Capabilities

### New Capabilities

- `android-ui`: phone/tablet/TV layout, focus, accessibility, edge-to-edge, system bars, predictive back, and navigation state restoration.

### Modified Capabilities

- None.

## Impact

- Affects `:app` (Compose theme, navigation infrastructure, edge-to-edge setup, predictive back, state restoration) and shared UI components in feature modules.
- Depends on `core-domain-network` (UI state types) and `auth-persistence-shell` (app shell scaffolding the navigation graph).
- All later feature changes (`catalog-details`, `playback`, `offline-downloads`, `library-settings-diagnostics`) consume this foundation.
