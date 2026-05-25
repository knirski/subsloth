# Offline Downloads Design

## Context

The `offline-downloads` OpenSpec change covers offline home mode, local-file playback resilience, app-private download storage, shared offline metadata, item downloads, confirmed season queues, queue persistence, storage safety, metered-network handling, and operational notifications.

Downloaded media must remain playable without connectivity or login revalidation when a verified local file exists. Media API download URLs are ephemeral, so the app must stage downloads into app-private storage and treat filesystem paths as implementation details rather than user-facing metadata.

## Goals

- Keep downloaded video playable offline regardless of session state when a verified local file exists.
- Store videos, subtitle sidecars, partial files, and offline metadata only in app-private storage with opaque paths and backup exclusion.
- Support explicit item downloads and explicit confirmed season queues.
- Enforce conservative safety rules for storage, metered networks, queue resume, and local-file failure recovery.
- Support offline subtitle playback from previously downloaded sidecars, including additional sidecars downloaded later by explicit user choice.

## Non-Goals

- No automatic next-episode downloads, smart queues, background discovery, or periodic download workers.
- No public-storage, MediaStore, SAF, or external-player exposure for downloaded media.
- No file-level media encryption in v1.
- No speculative subtitle prefetch beyond the explicit fallback rules and explicit user-triggered sidecar downloads.

## Architecture

The implementation is split into four layers:

- `:core:model`
  Offline/download state types only: queue records, asset identity, persisted status enums, and UI-safe metadata carriers.
- `:core:domain`
  Pure policies only: storage reserve, metered gating, duplicate/shared-asset decisions, safe replacement, season fallback selection, offline-home surfacing, and resume eligibility.
- `:core:media` and `:core:database`
  `:core:media` owns file staging, path generation, runtime download execution, partial cleanup, local playback file resolution, and foreground-service integration. `:core:database` owns shared offline metadata and persisted queue state.
- feature/app layer
  `feature:details` starts item and season flows, offline/download surfaces expose queue state and offline content, `feature:auth` pauses and resume-checks queues across logout/login, and `app` wires navigation plus service/bootstrap entry points.

## Components

### OfflineAssetStore

`OfflineAssetStore` in `:core:media` owns:

- app-private path creation with opaque path components only
- temporary staging files and final file promotion
- subtitle sidecar storage
- backup exclusion hooks
- absolute-path redaction helpers for logs and diagnostics

It does not own UI state, navigation, or business-policy decisions.

### OfflineCatalogRepository

`OfflineCatalogRepository` in the database layer owns:

- retained shared offline metadata for video assets
- retained subtitle-sidecar metadata
- persisted queue state for confirmed items and seasons
- data needed to surface Offline Library content when offline

UI titles and labels come from retained metadata, never from file paths or filenames.

### DownloadCoordinator

`DownloadCoordinator` in `:core:media` owns:

- enqueue, start, pause, cancel, and retry
- single-active-video enforcement across the app
- low-storage and metered checks at runtime
- coordination with `OfflineAssetStore`
- restart and resume checks for already-confirmed incomplete work

It orchestrates runtime work, but policy decisions remain in `:core:domain`.

### SeasonQueuePlanner

`SeasonQueuePlanner` is a thin boundary around pure domain policies. It owns:

- explicit season preflight after the user selects `Download season`
- confirmation summary generation
- fallback-impact counts
- unknown-size, unavailable-item, and skipped-item reporting

It never runs during passive browsing.

### Local Playback Resolver

The playback path first checks for a verified local video file. If one exists, playback uses it directly. If the device is offline and no verified local file exists, the app shows an unavailable or local-file-error state and does not attempt network refresh.

## Asset Model

### Video Assets

- One shared video asset exists per content item.
- A lower-quality request reuses an already-completed higher-quality asset.
- Higher-quality replacement is allowed only when quality ordering is unambiguous and the new file is fully downloaded and verified before replacement.

### Subtitle Sidecars

- Subtitle sidecars are stored independently from the video asset.
- Initial video download attempts subtitle download using this fallback only:
  preferred language when it is not English -> English -> no subtitles
- Subtitle download failure never blocks video download or local playback.
- If the user later opens a downloaded video while online and chooses another subtitle language, the app downloads an additional subtitle sidecar for that same offline video asset.
- When offline, subtitle selection is limited to subtitle sidecars already stored locally for that video.
- Subtitle identity is conservative: if a new sidecar cannot be unambiguously matched to an existing sidecar, it is stored separately and does not overwrite an existing playable subtitle.

## Core Flows

### Item Download

1. The user explicitly starts a download from details UI.
2. Policy checks determine duplicate handling, replacement eligibility, storage sufficiency, and metered confirmation requirements.
3. The queue entry is persisted.
4. The coordinator stages and downloads the video file.
5. The initial subtitle sidecar attempt follows the fallback rule.
6. The asset is marked playable only after file verification succeeds.
7. Shared offline metadata is updated for Offline Library and future replacement decisions.

### Season Download

1. The user explicitly selects `Download season`.
2. Preflight runs only at that point.
3. The confirmation screen summarizes episode count, known size or unknown-size state, skipped/already-downloaded counts where known, quality policy, subtitle policy, fallback counts, unavailable counts, and metered warning when relevant.
4. After confirmation, the queue is persisted.
5. Episodes execute sequentially with only one active video download across the app.
6. Completed episodes remain playable even if later episodes fail.

### App Restart And Login Flow

1. Persisted incomplete queues reload on restart.
2. The coordinator rechecks connectivity, storage, metered policy, auth, content access, and URL freshness.
3. Only already-confirmed incomplete work may resume.
4. Logout pauses incomplete queues and suppresses Media requests while logged out.
5. After login, incomplete items may resume only if the authenticated session can still access the same content.

### Offline Home Flow

If connectivity is unavailable and playable retained downloads exist, the app surfaces Offline Library content before failed online catalog states.

## Error Handling And UX

The failure model must be explicit, conservative, and recovery-oriented.

### State Categories

User-facing blocked states should be expressed by cause, not just a generic failure flag:

- `Needs Wi-Fi`
- `Not enough storage`
- `Download failed`
- `Missing local file`
- `Subtitle unavailable`

Each blocked state should carry one obvious primary action:

- `Needs Wi-Fi` -> `Resume on Wi-Fi` or `Allow mobile data`
- `Not enough storage` -> `Manage storage`
- `Download failed` -> `Retry`
- `Missing local file` -> `Redownload`
- `Subtitle unavailable` -> no blocking action for video playback

### Storage Failures

Before queueing or resuming, the app checks required size plus the safety reserve. If storage is insufficient, the item or queue becomes paused or unavailable with a concrete low-storage reason. Completed downloads are never auto-deleted to free space.

### Metered-Network Failures

Downloads default to Wi-Fi-only behavior. If a confirmed queue later encounters a metered network, transfer pauses before more bytes are downloaded. Resume requires explicit user approval for that queue.

### Video File Integrity Failures

A downloaded video becomes playable only after the file exists and has non-zero length. If verification fails, the item remains failed or partial with a clear reason.

### Subtitle Sidecar Failures

Subtitle-sidecar download failures do not fail the video download. If a sidecar is missing, corrupt, or unavailable, playback still starts with any other locally available subtitle sidecar if one exists, otherwise without subtitles.

### Local Playback Failures

If a verified local video file does not exist or is corrupt, playback does not perform a network refresh. It shows a recovery-oriented local-file error with actions back to Downloads or Details and a redownload path.

### Queue Execution Failures

Season queues are sequential. If one episode fails after bounded retries, completed episodes remain playable and the failed episode remains failed until explicit retry. Queue cancellation stops remaining queued or active work and cleans partial files where safe, while preserving completed assets and retained metadata.

### UX Priorities

- Separate video availability from subtitle availability in the UI.
- Prefer recovery wording over storage or filesystem jargon.
- Emphasize partial success in season queues, for example `6 of 8 episodes available offline`.
- Make metered and low-storage pauses obviously intentional rather than bug-like.

## Verification Shape

The implementation plan should be split into 8 phases:

1. download and queue models in `:core:model`
2. pure download policies in `:core:domain`
3. app-private storage, opaque paths, and redaction in `:core:media`
4. shared offline metadata and queue persistence in `:core:database`
5. item-download execution
6. foreground service and operational notifications
7. confirmed season queues, pause/resume, and lifecycle rules
8. offline UX wiring and final verification

Each phase should end with targeted verification. Final verification must include:

- `./gradlew :core:domain:test :core:media:test :feature:library:test :app:assembleDebug`
- manifest and lint validation for `dataSync` foreground-service and notification-permission timing
- `openspec validate offline-downloads --strict`
