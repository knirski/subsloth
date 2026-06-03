# offline-downloads Specification

## Purpose
TBD - created by archiving change offline-downloads. Update Purpose after archive.
## Requirements
### Requirement: Offline Home Mode
When offline, the app SHALL surface downloaded library content before failed online catalog states.

#### Scenario: Offline with downloads
- **WHEN** connectivity is unavailable and playable downloads exist
- **THEN** the app opens or surfaces Available Offline/Offline Library content first

### Requirement: Local File Playback
Downloaded videos SHALL remain playable without connectivity or login revalidation when a verified local app-private file exists.

#### Scenario: Non-downloaded content is opened offline
- **WHEN** the user opens content that is not downloaded while offline
- **THEN** the app shows a clear unavailable state instead of attempting playback or network refresh

#### Scenario: Subtitle is missing
- **WHEN** a downloaded video lacks a subtitle sidecar
- **THEN** video playback still starts without subtitles

#### Scenario: Local file is corrupt
- **WHEN** a local downloaded file is missing or corrupt
- **THEN** playback does not perform network refresh and shows a local-file error with Back to Downloads or Details

### Requirement: App Private Download Storage
Downloaded videos, subtitles, partial files, and download metadata SHALL stay in app-private storage and be excluded from Android backup/transfer.

#### Scenario: Download path is created
- **WHEN** a video or subtitle file is stored
- **THEN** path components use opaque identifiers and never include raw login/email, profile keys, titles, slugs, search queries, Media subtitle filenames, or human-readable account labels

#### Scenario: UI displays downloaded title
- **WHEN** a downloaded item is displayed in UI
- **THEN** the display title comes from Room metadata, not filesystem path or filename data

#### Scenario: External media access is requested
- **WHEN** v1 download storage is implemented
- **THEN** downloaded media is not exposed through public storage, MediaStore, Storage Access Framework exports, or external player handoff

#### Scenario: Stronger file protection is considered
- **WHEN** downloaded media is stored in v1
- **THEN** it is not additionally encrypted beyond Android app sandbox and device-storage protections unless a later explicit design adds file-level encryption

#### Scenario: Allowed path components
- **WHEN** a download path is constructed
- **THEN** path components may include only Media content/video IDs, subtitle language codes, random UUIDs, and file extensions

#### Scenario: Diagnostics or logs reference a downloaded file
- **WHEN** download paths or filenames appear in diagnostics, logs, or crash data
- **THEN** absolute media file paths are redacted so opaque content identifiers, titles, and subtitle filenames are not leaked

### Requirement: Shared Offline Metadata Retention
Shared offline display metadata SHALL be retained indefinitely while the downloaded media exists and SHALL be deleted only when no shared offline asset for that content remains.

#### Scenario: User is logged out
- **WHEN** logged-out Offline Library displays retained downloads
- **THEN** it uses retained shared offline metadata and never refreshes metadata over the network

#### Scenario: Effective quality is recorded
- **WHEN** a video asset completes
- **THEN** shared offline metadata records the effective downloaded quality so safe higher-quality replacement and "already available in higher quality" responses can be evaluated without network access

### Requirement: Shared Offline Assets
The system SHALL keep one shared video asset per content item and shared subtitle sidecars by content, language, and source/format where available.

#### Scenario: Higher-quality asset already exists
- **WHEN** a lower-quality download is requested for content that already has a higher-quality completed asset
- **THEN** the request reuses or skips the existing asset with an "already available in higher quality" state

#### Scenario: Higher-quality replacement is requested
- **WHEN** a lower-quality playable asset exists and the user requests higher quality
- **THEN** the app downloads and verifies the higher-quality file before replacing the existing playable asset

#### Scenario: Subtitle sidecar identity is ambiguous
- **WHEN** a new subtitle sidecar's content/language/source identity cannot be unambiguously matched to an existing sidecar
- **THEN** the app stores it as a separate sidecar and does not overwrite the existing playable subtitle

### Requirement: Download State Robustness
Downloads SHALL expose queued, downloading, paused, failed, partial, complete, and unavailable states with clear reasons.

#### Scenario: Download finishes
- **WHEN** a video or sidecar download finishes
- **THEN** the system verifies file existence and non-zero length before marking the asset playable, otherwise the item moves to failed/partial with a clear reason

#### Scenario: User cancels a download
- **WHEN** the user cancels an active or partial download
- **THEN** partial files are cleaned up unless safe resume is supported and explicitly retained by the platform behavior

### Requirement: Low Storage Safety
Before queueing or resuming a download, the app SHALL require known or estimated remaining size plus a safety reserve of `2 GB` or `10%` of total storage on small devices, whichever is smaller.

#### Scenario: Size is unknown
- **WHEN** download size is unknown
- **THEN** the app requires the safety reserve before starting and continues checking during download

#### Scenario: Storage check fails
- **WHEN** available storage is below the required size plus reserve
- **THEN** the queue item is paused or unavailable with required/available/reserve values when known and no completed downloads are deleted automatically

### Requirement: Metered Network Safety
Downloads SHALL respect downloads-on-Wi-Fi-only defaults and require explicit user confirmation for metered-network transfer.

#### Scenario: Network becomes metered
- **WHEN** a queue confirmed on unmetered network later becomes metered
- **THEN** queued and active items pause before further transfer until the user explicitly allows metered resume for that queue

### Requirement: Single Active Video Download
v1 SHALL allow only one active video download across the app, including confirmed season queues.

#### Scenario: Multiple downloads are queued
- **WHEN** several item or season episode downloads are pending
- **THEN** only one video download is active and other items remain queued

### Requirement: Confirmed Season Queue
Season downloads SHALL require explicit user confirmation after the user opens a season and selects "Download season".

#### Scenario: Confirmation screen is shown
- **WHEN** the user selects "Download season"
- **THEN** the app shows episode count, already-downloaded/skipped count when known, selected quality policy, subtitle language policy, size information or unknown-size labels, metered-network warning when relevant, and unavailable reasons before queueing

#### Scenario: Confirmation is repeated
- **WHEN** the user starts a later season download
- **THEN** the app shows the confirmation flow again and v1 does not provide "don't ask again", preference-based auto-confirm, or one-tap season queueing from season rows or cards

#### Scenario: Passive browsing occurs
- **WHEN** the user only browses catalog or season rows
- **THEN** the app does not run season-size preflight or queue downloads

### Requirement: Season Queue Fallback Policy
Confirmed season queues SHALL apply per-episode quality and subtitle fallback policies and summarize known fallback impact before confirmation.

#### Scenario: Preferred episode quality is unavailable
- **WHEN** an episode in a confirmed season queue lacks the preferred quality
- **THEN** the queue tries nearest lower quality, then nearest higher quality, then marks that episode unavailable or skipped with a clear reason

#### Scenario: Quality ordering is ambiguous
- **WHEN** exact quality ordering cannot be determined for an episode or existing asset
- **THEN** the queue does not auto-replace or guess quality ordering and shows an ambiguous/unavailable reason for that episode or upgrade

#### Scenario: Preferred non-English subtitle is unavailable
- **WHEN** an episode in a confirmed season queue requests a non-English preferred subtitle language and that language is unavailable
- **THEN** the queue falls back to English, then no subtitles without failing the video download

#### Scenario: English subtitle is unavailable
- **WHEN** an episode in a confirmed season queue lacks English subtitles and English is the preferred language
- **THEN** the queue uses no subtitles without failing the video download

#### Scenario: Fallback impact is known before confirmation
- **WHEN** season preflight can determine quality or subtitle fallback outcomes
- **THEN** the confirmation screen summarizes preferred quality/language, fallback counts, unavailable episode count, and no-subtitle count where known

#### Scenario: Download flow starts for one item
- **WHEN** a single movie or episode download starts
- **THEN** preferred subtitles are selected by default and the user may add other available languages without re-downloading an existing shared video asset

### Requirement: Season Queue Execution
Confirmed season queues SHALL download available episodes sequentially, preserve completed episodes when others fail, and expose per-episode status.

#### Scenario: One episode fails
- **WHEN** a season queue episode fails after bounded retries
- **THEN** completed episodes remain playable and the failed episode remains failed until explicit retry

#### Scenario: Download concurrency would increase
- **WHEN** network, charging, or device class changes during v1 queue execution
- **THEN** the app does not use adaptive download concurrency and still allows only one active video download across the app

#### Scenario: Queue is canceled
- **WHEN** the user cancels a season queue
- **THEN** remaining queued/active items stop, partial files are cleaned up where safe, and completed episodes, sidecars, metadata, and shared offline progress remain

### Requirement: Queue Persistence
Confirmed season queues SHALL persist across process death and app restart without discovering new episodes or creating new queues.

#### Scenario: App restarts
- **WHEN** the app resumes a persisted queue
- **THEN** it resumes only already-confirmed incomplete items after rechecking connectivity, storage, metered policy, auth, content access, and URL freshness

### Requirement: Logout Queue Safety
Logout SHALL pause incomplete confirmed queues and retain queue state without sending Media requests while logged out.

#### Scenario: User logs back in
- **WHEN** the user logs back in after a paused queue
- **THEN** incomplete items may resume only if the authenticated session can access the same content through Kodi-compatible flows

### Requirement: Operational Notifications
Playback and active visible downloads SHALL use narrow foreground-service types and minimal operational notifications only when platform-required.

#### Scenario: Playback service is active
- **WHEN** playback needs background or TV service behavior
- **THEN** the service declares `mediaPlayback` foreground-service type and does not start from `BOOT_COMPLETED`

#### Scenario: Active download notification is required
- **WHEN** an active visible download requires a foreground notification
- **THEN** the notification shows current item title or a redacted generic label when the title cannot be shown safely, progress and paused/metered/low-storage state when relevant, a tap target that opens the Downloads screen, and only safe active-item actions such as pause/cancel; queue-wide retry/delete actions stay inside the app and there are no queue summary, completed-item, failed-item, or new-episode notifications

#### Scenario: Android 13 notification permission is unset
- **WHEN** an active visible download or playback service requires a foreground notification on Android 13+
- **THEN** the app requests `POST_NOTIFICATIONS` only at that point and not on first launch

#### Scenario: Notification permission is denied
- **WHEN** the user denies `POST_NOTIFICATIONS`
- **THEN** required playback/download foreground-service notifications still display per platform rules and the foreground service is not aborted

#### Scenario: App runs on Android TV
- **WHEN** playback or active-download notifications appear on Android TV
- **THEN** they are treated as platform-required operational UI and never as the primary control surface for playback or download management

