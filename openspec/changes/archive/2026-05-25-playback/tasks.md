## 1. Playback Domain Tests

- [x] 1.1 Add tests for resume thresholds, progress scope merge, local completion, watched toggles, and server sync gates.
- [x] 1.2 Add tests for next-episode calculation, prompt dismissal, no autoplay, offline-only next episode, and unreleased episode exclusion.
- [x] 1.3 Add tests for subtitle fallback, subtitle failure actions, and offline subtitle retry limits.
- [x] 1.4 Add tests for explicit watched/unwatched toggle scoping, no automatic mirroring, and no shared-offline-progress copy on login.
- [x] 1.5 Add tests for quality labels, quality selection, quality fallback, caption styling, and playback speed persistence rules.

## 2. Media3 Playback Shell

- [x] 2.1 Implement Media3 item factory and playback controller behind `:core:media` boundaries (playback only — download controller belongs in `offline-downloads`).
- [x] 2.2 Implement local-file-first playback and no-network-refresh behavior for offline files.
- [x] 2.3 Implement bounded stream URL refresh, current-playback quality fallback, and recoverable player errors.
- [x] 2.4 Add playback service manifest entries, permissions, and notification channel behavior for `mediaPlayback` foreground-service type.

## 3. Player UI

- [x] 3.1 Add PlayerViewModel tests for controls, progress, subtitle, quality, speed, and next-episode state.
- [x] 3.2 Implement player controls for play/pause, seek, subtitles, quality, speed, and next episode.
- [x] 3.3 Implement auth failure handling that saves progress and routes to auth repair only for online playback.

## 4. Verification

- [x] 4.1 Run `./gradlew :core:domain:test :core:media:test :feature:player:test :app:assembleDebug`.
- [x] 4.2 Run manifest/lint checks for `mediaPlayback` foreground-service type.
- [x] 4.3 Run `openspec validate playback --strict`.