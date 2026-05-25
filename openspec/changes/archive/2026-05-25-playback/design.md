## Context

Playback covers both online streaming and downloaded local-file playback semantics. Streaming uses ephemeral signed URLs and adaptive/progressive sources via Media3; local playback runs against app-private files produced by the offline-downloads capability. Watched state is split between account-scoped progress (online) and shared offline progress (downloaded media).

## Goals

- Persist meaningful progress and choose safe resume points across resume thresholds.
- Keep subtitles non-blocking — failures must not abort playback.
- Bound URL and quality fallback for the current playback session only.
- Correctly scope watched toggles between account-scoped and shared offline progress without auto-mirroring.

## Non-goals

- Download queueing, season queues, storage management, foreground notifications for downloads, and app-private storage rules belong in `offline-downloads`.
- New-episode notifications, smart downloads, and background discovery are out of scope.
- File-level encryption for downloaded media is out of scope.

## Decisions

- Resume thresholds: 30s minimum and 95% completion cutoff.
- Stream URL refresh: at most one same-item refresh per playback session.
- Quality fallback: at most one nearest-lower quality fallback for the current playback only.
- Progress scopes: account-scoped progress and shared offline progress are distinct and never automatically mirrored.
- No copy-on-login of shared offline progress to account-scoped progress.
- Watched toggles act on the explicit scope chosen by the user.
- Media item factory supports direct URLs, adaptive playlists, and progressive/fragmented sources discovered from the Media API. Subtitles are attached as Media3 external subtitle configurations (not HTML `<track>` elements) because authenticated web playback uses custom subtitle controls. Quality selection maps to Media3 track-selection constraints when adaptive variants are exposed.
- Media3 types do not leak into `:core:domain` or `:core:model`; all Media3 surface area is contained behind `:core:media` ports consumed via typed capability interfaces.

## Risks

- Media3 subtitle/track behavior may differ between adaptive and progressive streams.
- Auth failure during streamed playback must save progress and route to auth repair, but must NOT interrupt local-file playback.
- Quality URL behavior may depend on the Media API and live discovery.

## Migration Plan

1. Add pure policy tests for resume thresholds, subtitle fallback, quality fallback, playback speed, and next-episode logic.
2. Add Media3 shell boundaries in `:core:media` for playback only (download controller belongs in `offline-downloads`).
3. Add player UI/ViewModel in `:feature:player`.
4. Add playback foreground-service manifest entries with `mediaPlayback` foreground-service type.

## Open Questions

- Exact quality URL behavior and adaptive stream variant exposure depend on live API discovery against Media.
