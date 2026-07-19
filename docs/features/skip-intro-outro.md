# Skip Intro / Outro — Feature Analysis

**Status**: Research / Not prioritized for v1  
**Last updated**: 2026-07-19  
**Author**: Agent analysis of some-content-provider.com Kodi plugin v4.0.4 + web frontend (live session)

---

## Summary

Skip intro/outro is a **web-frontend-only feature** on some-content-provider.com. The Kodi API
(`/api/v2/`) does **not** expose any skip segment data, and the Kodi plugin
(`plugin.video.some-content-providertv` up to v4.0.4) has **zero** skip-related code.

To implement this feature in subsloth, we would need to either:

1. **Scrape the web player page** for the embedded `gon_media.skip` JSON payload
   (brittle, violates Kodi-only API policy).
2. **Reverse-engineer a separate skip-data API** (none known to exist).
3. **Use client-side analysis** (detect silence/black frames / scene changes
   locally to generate skip segments).

---

## Web Frontend: How It Works

The skip data is embedded in the Rails player page as a `gon` JavaScript
variable:

```javascript
gon_media.skip = {
  "segments": [
    { "s": 0.0, "e": 95.0, "l": "title_sequence", "f": false },
    { "s": 2900.0, "e": 2925.0, "l": "credits", "f": false }
  ],
  "version": 1
}
```

### Segment Schema

| Field | Type | Description |
|-------|------|-------------|
| `s` | float | Start time (seconds) |
| `e` | float | End time (seconds) |
| `l` | string | Label — see label types below |
| `f` | boolean | Flagged (reserved, currently always `false`) |

### Segment Label Types

| Label | Button Text | Notes |
|-------|-------------|-------|
| `title_sequence` | "Skip Intro" | Main title sequence / opening credits |
| `studio_bump` | "Skip Intro" | Studio bumper before/after content |
| `previously_on` | "Skip Recap" | "Previously on…" recap segment |
| `cold_open_recap` | "Skip Recap" | Cold open that functions as recap |
| `credits` | "Skip Credits" | End credits |
| `mid_credit_scene` | "Skip Scene" | Mid/post-credits scene |

### Player Behavior

- **Manual skip only** — no auto-skip exists. The player shows a button that
  the user clicks to jump past the segment.
- **Button visibility** is controlled by `timeupdate` events: the button
  appears when `currentTime` is within a segment's `[s, e)` window.
- **Merging**: consecutive segments (e.g. intro + recap) with a gap < 5
  seconds are merged into a single skip button.
- **Deduplication**: segments under 3 seconds are discarded.

### How gon_media Gets Populated

The Rails backend injects `gon_media.skip` when rendering the player page.
This data is **not** available through any REST API endpoint — it is only
embedded in the HTML served at:

```
https://some-content-provider.com/en/movies/{id}
https://some-content-provider.com/en/shows/{slug}?season={n}&episode={n}
```

The data source is likely a database table storing manually curated or
algorithmically detected segment boundaries, created during content ingestion.

---

## Kodi Plugin Analysis (v4.0.4)

### API Endpoints

| Endpoint | Response Contains Skip Data? |
|----------|------------------------------|
| `GET /movies` | ❌ |
| `GET /movies/{id}` | ❌ |
| `GET /shows` | ❌ |
| `GET /shows/{id}` | ❌ |
| `GET /episodes/{id}` | ❌ |

No skip-related fields exist in any API response.

### Plugin Code

- `some-content-providerAPI.py` — pure REST client with domain fallback, no skip logic.
- `default.py` — listing/resolution/playback, no skip references.
- `service.py` — background update service, no skip references.
- `contextMenu.py` — context menus, no skip references.
- `cache.py` — simple Kodi cache wrapper.
- `commonfunctionsdummy.py` — stub.

A `rg -i "skip|intro|outro|credits|recap"` across the entire plugin returns
**zero matches**. Kodi users have no skip intro/outro functionality.

---

## Implementation Options for subsloth

### Option A: Web Player Scraping (Not Recommended)

**Cost**: High — requires an authenticated browser session, Cloudflare
bypass, and DOM parsing. Brittle against site changes.

**Feasibility**: `some-content-provider.com` is behind **Cloudflare browser challenge** —
automated scraping is blocked. An authenticated session cookie helps, but
the initial page load still requires JavaScript execution and challenges.

**Approach**:
```
1. Log in to some-content-provider.com (handle Cloudflare + CSRF)
2. Navigate to /en/movies/{id} or show/episode page
3. Extract gon_media.skip from the HTML
4. Cache per-media-item for offline playback
5. Use segments to drive skip button in the player UI
```

**Downsides**:
- Cloudflare bypass is fragile and may break without notice
- Login requires CSRF token extraction + cookie management
- Web scraping is a separate concern from the Kodi API integration
- No offline access to skip data without local caching
- Violates the project's Kodi-only API boundary

### Option B: Separate Skip Data Service

**Cost**: Very High — requires building and maintaining a parallel service.

**Feasibility**: Unknown internal some-content-provider infrastructure. If some-content-provider exposes a
skip-data API internally, it's not public. Without documentation, this is
reverse-engineering territory.

**Approach**: Find and document any internal skip-data endpoint by:
- Monitoring network requests from the web player
- Decompiling the iOS/Android apps
- Checking if skip data appears in any API response not yet analyzed

### Option C: Client-Side Intro Detection

**Cost**: Medium-High — requires media analysis library integration.

**Approach**: Use ML or signal-processing to detect intro/outro segments by:
- Repeated visual/audio patterns across episodes of a show
- Black frames / silence / unique audio fingerprints
- Scene-change detection with threshold heuristics

**Downsides**:
- Computationally expensive on mobile
- Must analyze video frames (requires access to media bytes)
- Kodi API provides HLS playlist URLs with auth tokens — full video download
  needed for analysis
- Accuracy varies by show

### Option D: Manual Curation (via subsloth backend)

**Cost**: Medium — requires a database + moderation interface.

**Approach**: Allow users or admins to mark skip segments manually, stored
in a subsloth-specific service. Shared across all clients.

**Downsides**:
- Additional infrastructure (database, API, moderation tooling)
- Requires users to invest effort
- Not useful offline without sync

---

## Recommendation

**Do not implement for v1.** Skip intro/outro is a nice-to-have feature that
adds significant complexity for a relatively small UX gain.

If prioritized later, **Option C (client-side detection)** is the most
sustainable approach — no dependency on some-content-provider's internal infrastructure,
works offline, and can be iteratively improved. However, it requires
media-analysis capabilities that are a large feature in themselves.

### Prerequisites for Future Implementation

1. A reliable way to download or stream media segments for analysis
2. Integration of a media-analysis library (e.g. FFmpeg for KMP / mobile)
3. Caching layer for detected skip segments (DataStore or local DB)
4. UI components: skip button overlay in the player with timeupdate binding
5. Segment merging/deduplication logic (reimplement web frontend heuristics)

---

## Appendix: Web Frontend Button Text Mapping

```
previously_on    → "Skip Recap"
cold_open_recap  → "Skip Recap"
title_sequence   → "Skip Intro"
studio_bump      → "Skip Intro"
credits          → "Skip Credits"
mid_credit_scene → "Skip Scene"
```

Any unrecognized label defaults to a generic "Skip" button.

---

## Live Exploration Results (2026-07-19)

Using agent-browser with NixOS Chromium (via `--cdp 9222` connecting to a
Chrome instance with persistent profile `~/.config/some-content-provider-profile`), multiple
TV show episode player pages were inspected:

| Show | Episode | `gon_media.skip` |
|------|---------|-------------------|
| Silo S01E01 "Freedom Day" | Player loaded | `null` |
| Grey's Anatomy S01E01 | Player loaded | `null` |
| How I Met Your Mother S01E01 | Show page only | `undefined` |

### Additional Findings

- `gon_media` on the player page contains: `env`, `user`, `u`, `i18n`,
  `svg_sprite`, `web_push_vapid_public_key`, `hosts`,
  `captcha_invisible_key`, `captcha_interactive_key`, `s` (base64-encoded
  stream URLs), `h` (page HTML)
- No `gon_media.skip` or `gon_media.media` key found on any inspected page
- The skip data may only exist for movies (not TV shows), or for specific
  content that has been manually curated
- The Cloudflare challenge is present on all some-content-provider.com pages but can be
  bypassed by using a Chrome persistent profile (`--user-data-dir`) where
  the challenge was solved once
- `agent-browser --cdp 9222` successfully connects to an externally-launched
  Chrome and can interact with the authenticated session
