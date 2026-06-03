## Context

Downloaded media must remain playable without connectivity or login revalidation. Raw download URLs from the Media API are ephemeral, so the app stages files into app-private storage and references them via opaque local paths. Downloads must always be explicit user actions — there is no automatic next-episode download, smart queue, or background discovery.

## Goals

- Keep downloaded media playable offline regardless of session state.
- Implement user-confirmed item and season downloads with low-storage and metered-network safeguards.
- Keep one shared video asset per content item with safe higher-quality replacement and never duplicate the same item across libraries.
- Persist queues so that interrupted downloads resume across process death and reboot.

## Non-goals

- No automatic next-episode downloads, background discovery, periodic queue workers, unconfirmed download-all actions, or smart downloads.
- No new-episode notifications.
- No exposure of downloaded media through public storage, MediaStore, SAF, or external players.
- No file-level encryption for downloaded media in v1.
- No coverage of playback resume, subtitle in-player switching, or quality in-player UI — those belong in `playback`.

## Decisions

- One shared video asset per content item; safe replacement is allowed only for strictly higher confirmed quality.
- One active video download across the app at a time.
- Explicit season confirmation summary; no "don't ask again" toggle.
- Downloads-on-Wi-Fi-only by default; metered downloads require explicit user opt-in per action.
- App-private storage with opaque path components and `android:allowBackup="false"` semantics for download files.
- Shared offline metadata is retained indefinitely while the corresponding media file exists.

## Risks

- Media3 download capabilities and Media URL resumability may limit pause/resume — UI must expose actual states (paused/resumable/restart-only) honestly.
- Season queue preflight may have partial size data — allow user to proceed with unknown sizes only after explicit confirmation.
- Android foreground-service rules vary by API level — verify manifest with lint and on-device tests.

## Migration Plan

1. Add pure policy tests for downloads, sidecars, storage reserve, metered behavior, and season queues.
2. Add `:core:media` download controller boundary.
3. Add app-private storage with opaque paths and backup exclusion.
4. Add item download UI/state.
5. Add confirmed season queue policy and persistence.
6. Add `dataSync` foreground service and minimal active-download notification.

## Open Questions

- Exact download URL resumability and Media3 download behavior depend on live drift tests against the Media API.
