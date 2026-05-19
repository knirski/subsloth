# android-ui Specification

## Purpose
TBD - created by archiving change android-ui-foundation. Update Purpose after archive.
## Requirements
### Requirement: Phone Layout
Phone UI SHALL use single-pane flows, bottom navigation where appropriate, sticky primary actions where useful, and adaptive/resizable behavior on Android phone 16.

#### Scenario: Phone opens detail
- **WHEN** a phone user opens a movie or series detail
- **THEN** content is stacked with accessible primary actions and no fixed orientation or aspect-ratio assumption

### Requirement: Tablet Layout
Tablet UI SHALL use adaptive list-detail or two-pane layouts when width allows.

#### Scenario: Tablet has wide window
- **WHEN** a tablet has sufficient width
- **THEN** catalog/detail navigation can show list and detail panes without losing core actions

### Requirement: TV Layout and Focus
Android TV UI SHALL use D-pad-friendly rows, large cards, detail-first playback actions, deterministic focus order, visible high-contrast focus indicators, and focus restoration.

#### Scenario: TV user returns from detail
- **WHEN** a TV user backs out of detail or player to a browse row
- **THEN** focus returns to the previous meaningful card or row action

### Requirement: Detail Adaptation
Movie and series detail layouts SHALL preserve the same content and actions across phone, tablet, and TV while adapting presentation to input mode and screen size.

#### Scenario: Series detail on TV
- **WHEN** a TV user opens series detail
- **THEN** the screen exposes poster/details/action rail, season selector, focused episode list, and clear play/resume/next actions

### Requirement: Accessibility and Input
Every interactive element SHALL have meaningful text or content description, accessible contrast, large-text resilience, and touch or remote operation appropriate for the device.

#### Scenario: Remote-only navigation
- **WHEN** a TV user has only directional navigation, select, back, and media keys
- **THEN** browsing and details remain operable without touch or pointer input

### Requirement: System UI Handling
The app SHALL use edge-to-edge UI with safe handling for system bars, display cutouts, and TV overscan-safe spacing.

#### Scenario: Overscan-prone TV
- **WHEN** the app runs on Android TV 8
- **THEN** critical actions and focused content remain inside overscan-safe spacing

### Requirement: Navigation State Restoration
The app SHALL restore key navigation and interaction state after process death where the backing data remains available.

#### Scenario: Process is restored
- **WHEN** Android restores the app after process death
- **THEN** login state, navigation destination, selected tabs, search query, player state, and download queue state are restored or safely reconstructed from persisted state

### Requirement: Predictive Back
The app SHALL support predictive back where available and deterministic remote Back behavior on TV.

#### Scenario: Back navigation occurs
- **WHEN** a phone, tablet, or TV user navigates back
- **THEN** the app returns to the expected previous destination without losing required restored state

