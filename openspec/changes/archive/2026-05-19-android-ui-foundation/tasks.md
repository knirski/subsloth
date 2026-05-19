## 1. Adaptive Layout Primitives

- [x] 1.1 Add phone single-pane layout primitives.
- [x] 1.2 Add tablet adaptive list-detail layout primitives.
- [x] 1.3 Add TV row, large card, action rail, and overscan-safe spacing primitives.

## 2. TV Focus and Accessibility

- [x] 2.1 Add deterministic TV D-pad focus utilities and focus restoration helpers.
- [x] 2.2 Add accessibility primitives for content descriptions, contrast-critical states, large-text resilience, and touch target sizing.
- [x] 2.3 Add Compose/TV focus test harness that downstream feature changes consume.
- [x] 2.4 Provide test recipes covered by the harness: D-pad traversal through rows and episode lists, focus restoration after back/dialog dismiss, remote media-key handling, large-text resilience, contrast-critical states, and touch target sizing.

## 3. System UI and Navigation

- [x] 3.1 Add edge-to-edge theme defaults via app theme XML (`values/styles.xml`) and Manifest, with system-bars/cutouts handling for phone, tablet, and TV.
- [x] 3.2 Add navigation state restoration support for navigation destination, selected tabs, search query, player state, and download queue state.
- [x] 3.3 Add predictive back integration via Manifest `android:enableOnBackInvokedCallback="true"` plus per-destination `OnBackPressedCallback`s, gated by Android version availability, with deterministic remote Back behavior on TV.

## 4. Verification

- [x] 4.1 Run `./gradlew :app:assembleDebug`.
- [ ] 4.2 Run focus harness smoke tests on a TV emulator if available.
- [x] 4.3 Run `openspec validate android-ui-foundation --strict`.
- [x] 4.4 Add Roborazzi screenshot harness with phone, tablet, and TV form-factor configurations that downstream feature changes consume.
- [ ] 4.5 Run process-death state-restoration smoke test for the main navigation graph.
