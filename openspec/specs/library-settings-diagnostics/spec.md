# library-settings-diagnostics Specification

## Purpose
TBD - created by archiving change library-settings-diagnostics. Update Purpose after archive.
## Requirements
### Requirement: Library Rows
The library SHALL expose Continue Watching, favorites, watch later, Available Offline, and relevant downloaded/offline filters while logged in.

#### Scenario: Continue Watching combines scopes
- **WHEN** logged-in library content includes streamed account progress and shared offline progress
- **THEN** the library combines them using the resume merge rules from playback requirements

### Requirement: Logged Out Offline Library Scope
Logged-out Offline Library SHALL allow only retained downloaded-title browsing, playback of downloaded videos/subtitles, local storage usage viewing, and deleting downloads.

#### Scenario: Logged-out user opens Offline Library
- **WHEN** the user is logged out
- **THEN** Offline Library does not allow catalog, online search, network-refreshed details, favorites, watch-later, server library actions, new downloads, progress sync, notifications, account settings, or diagnostics with auth/network state

### Requirement: Central Downloads Screen
The Downloads screen SHALL show all offline media and queue state across movies, episodes, and confirmed season queues.

#### Scenario: Downloads render groups
- **WHEN** downloads or queues exist
- **THEN** the screen groups active, queued/paused, failed/unavailable, and completed items

#### Scenario: Download row renders
- **WHEN** a download row is shown
- **THEN** it includes title, media type or episode info, effective or target quality, subtitle languages/status, size or estimate when known, progress, status reason, and state-specific actions

### Requirement: Season Queue Visibility
Confirmed season queues SHALL expose per-episode status from the central Downloads screen.

#### Scenario: Season queue has failures
- **WHEN** one or more season episodes fail
- **THEN** the user can see per-episode status and retry failed items without returning to the season detail screen

### Requirement: TV Downloads UX
TV Downloads SHALL use large rows/cards, deterministic D-pad focus order, overscan-safe spacing, focus restoration, and simple confirmation-based actions.

#### Scenario: TV destructive action starts
- **WHEN** a TV user chooses delete this download, delete watched completed, or delete all downloads
- **THEN** a confirmation dialog states what will be deleted and that shared offline progress for deleted media will also be removed

### Requirement: Storage Management
Storage management SHALL show app-private media storage, per-title size, selected quality, subtitle languages, and download status, and SHALL support explicit user-driven deletes.

#### Scenario: Bulk delete is requested
- **WHEN** the user chooses to delete watched completed downloads or all downloads
- **THEN** the app requires explicit confirmation before deleting media and shared offline progress for those media items

#### Scenario: Storage is low
- **WHEN** a low-storage state is shown
- **THEN** the UI shows required space, available space, reserve requirement when known, and a Manage downloads action without auto-selecting or auto-deleting completed media

### Requirement: Settings Controls
Settings SHALL include account-scoped logout, subtitle enabled/language, quality preference, playback speed, downloads-on-Wi-Fi-only where applicable, and view-only diagnostics.

#### Scenario: New-episode settings are absent
- **WHEN** v1 settings are displayed
- **THEN** no new-episode notification setting, channel setup, or scheduling control is present

### Requirement: Logout Cleanup UI
Logout cleanup UI SHALL expose independent local-only choices for deleting downloaded videos/subtitles, resetting active-profile preferences, and clearing active-profile watch/library data.

#### Scenario: Cleanup confirmation is visible
- **WHEN** logout cleanup confirmation is visible
- **THEN** it is treated as credential-sensitive for `FLAG_SECURE`

### Requirement: View Only Diagnostics
Diagnostics SHALL be local-only, view-only, redacted, and credential-sensitive.

#### Scenario: Diagnostics render
- **WHEN** diagnostics are displayed
- **THEN** they may show app version, build type, version code, Git SHA, release channel, device/API level, API base URL, auth state category, cache age, refresh times, download queue counts, storage usage, last status category, last successful refresh age, and `Kodi-compatible request mode: enabled`

#### Scenario: Sensitive diagnostic fields are possible
- **WHEN** diagnostics are assembled
- **THEN** credentials, auth headers, media URLs, download URLs, absolute media file paths, raw login/email, profile keys, cookies, endpoint paths, query params, request headers, header values, auth scheme details, User-Agent value, request/response bodies, redirect targets, and raw request/response headers are omitted

### Requirement: Diagnostics Export Exclusion
v1 diagnostics SHALL NOT provide export, share, upload, copy-to-clipboard, attachment, bug-report bundle, HAR, screenshot, trace, or raw artifact creation from inside the app.

#### Scenario: User views diagnostics
- **WHEN** the diagnostics screen is open
- **THEN** there are no export, share, upload, copy, attachment, bug-report, HAR, screenshot, trace, or raw artifact actions

### Requirement: Update Check Exclusion
v1 SHALL NOT include in-app update checking, update downloading, install prompting, GitHub Releases API calls, or calls to non-Media services for update checks.

#### Scenario: Settings displays version
- **WHEN** settings shows build metadata
- **THEN** it is display-only and does not contact any update service

