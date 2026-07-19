# Content Provider API Surfaces

A categorized inventory of every API surface, protocol, and data source that
subsloth interacts with or could potentially use. Each section describes what
it offers, how to authenticate, known limitations, and cross-references to
deeper documents.

---

## Table of Contents

1. [Kodi Content API (`/api/v2/`)](#1-kodi-content-api-apiv2)
2. [Web Frontend API (`/api/frontend/`)](#2-web-frontend-api-apifrontend)
3. [Embedded Page Data (`gon_media`)](#3-embedded-page-data-gon_media)
4. [Streaming & CDN Layer](#4-streaming--cdn-layer)
5. [Kodi Plugin (Reference Implementation)](#5-kodi-plugin-reference-implementation)
6. [Cloudflare Protection Layer](#6-cloudflare-protection-layer)
7. [Summary Table](#7-summary-table)

---

## 1. Kodi Content API (`/api/v2/`)

**The primary API for subsloth.** This is the same API that the official Kodi
plugin uses. It provides all content data (shows, episodes, movies) via simple
HTTP GET requests with Basic authentication.

### Base URLs

| Host | Status | Notes |
|------|--------|-------|
| `https://front.some-content-provider.com/api/v2` | ✅ Working | Primary — used by Kodi plugin first |
| `https://front.content-provider-mirror.tv/api/v2` | ✅ Working | Fallback mirror |
| `https://front.some-content-provider.com/api/v2` | ❌ Dead | **Default in `CaptureApi` — needs update** |

### Authentication

- **Method**: HTTP Basic Auth
- **Credentials**: some-content-provider.com email + password (same as web login)
- **Format**: `Authorization: Basic base64("email:password")`

### Endpoints

| Method | Path | Returns | Notes |
|--------|------|---------|-------|
| GET | `/movies` | `{"movies": [...]}` | List of movies (currently empty on free tier) |
| GET | `/movies/{id}` | Movie detail object | |
| GET | `/shows` | `{"shows": [...]}` | List of all shows (full objects, ~2700+ shows) |
| GET | `/shows/{id}` | Show detail + episodes array | Episodes include `versions` (stream URLs) |
| GET | `/episodes/{id}` | Episode detail | |

### Request Requirements

| Header | Value | Required |
|--------|-------|----------|
| `Authorization` | `Basic {base64 credentials}` | ✅ |
| `User-Agent` | `Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1` | ✅ (recommended) |
| `Accept` | `application/json` | ✅ |
| `Content-Type` | `application/json` | ✅ |

### Rate Limits / Timeouts

- **Default timeout**: 10s (Kodi plugin) / 30s (subsloth)
- **Movies list timeout**: 16s (Kodi plugin)
- No explicit rate limiting observed, but Cloudflare may throttle.

### Data Shape

See `api/subsloth.openapi.yaml` for the full OpenAPI specification.

Key differences from what the web frontend shows:
- ❌ No `subtitles` array (VTT URLs are not in API responses)
- ❌ No `skip` / intro/outro data
- ❌ No `backdrop_url` at episode level (only at show level)
- ❌ No stream auth tokens (Kodi returns path; tokens generated client-side)

### Authentication Details

The Kodi plugin embeds credentials directly in the URL:
```python
self.protocol + '%s:%s@' % (quote_plus(login), quote_plus(password)) + self.url + self.api_prefix
```

This causes the browser to prompt for auth but works with curl / HttpClient.
subsloth uses the `Authorization` header approach which is equivalent.

### Domain Fallback Logic (Kodi Plugin)

```python
DOMAINS = ['front.content-provider-mirror.tv', 'front.some-content-provider.com']
```
1. Try cached working domain first
2. Try each domain in order
3. On HTTP error (4xx/5xx): cache the domain, handle error
4. On network error (timeout/DNS): invalidate cache, try next domain
5. Cache persists for 1 year via Kodi cache mechanism

➡️ **subsloth gap**: No domain fallback implemented yet.

### References

- [Live Verification §4](live-verification.md#4-kodi-api-frontsome-content-providertv) — endpoint details, response examples
- [Live Verification §7](live-verification.md#7-kodi-plugin-v404-vs-subsloth-client-alignment) — plugin vs subsloth comparison
- [Live Verification §8](live-verification.md#8-scripts--tooling) — CaptureApi & ApiLiveDriftTest details
- `api/subsloth.openapi.yaml` — OpenAPI spec
- `core/network/src/.../client/ClientFactory.kt` — Ktor client setup
- `testing/api-contract/src/.../CaptureApi.kt` — Fixture capture tool

---

## 2. Web Frontend API (`/api/frontend/`)

A secondary API used by the Rails web frontend for non-content features.
Requires a session cookie (web login), not Basic auth.

### Base URL

`https://some-content-provider.com/api/frontend`

### Authentication

- **Method**: Rails session cookie (`_some-content-provider_session`)
- **Acquisition**: POST to `/en/users/sign_in` with CSRF `authenticity_token`
- **Challenge**: Login POST triggers Cloudflare Turnstile challenge
- **Persistence**: Session cookie lasts until expiry or logout

### Known Endpoints

| Endpoint | Method | Returns | Purpose |
|----------|--------|---------|---------|
| `/comments` | GET | JSON array with author, text, likes, spoiler flags | Show/movie comments |
| `/search` | GET | HTML page (redirect) | Search query |
| `/notifications` | GET | JSON `{}` | User notifications (empty on free tier) |

### Comments API Example

```
GET /api/frontend/comments?commentable_type=Show&commentable_id=2119&sort=newest
```

Response fields: `id`, `created_at`, `text`, `liked`, `spoiler`, `complained`,
`author` (with `id`, `avatar`, `name`), `commentable_name`, `commentable_slug`.

### Limitations

- ❌ Returns HTML for search (not JSON — requires parsing)
- ❌ Comments return HTTP 500 status but valid body (Rails quirk)
- ❌ Requires Cloudflare bypass for session acquisition
- ❌ No skip intro/outro data
- ❌ No subtitle data
- ❌ No stream URL data

### References

- [Live Verification §9](live-verification.md#9-web-frontend-api-apifrontend) — endpoint discovery and analysis

---

## 3. Embedded Page Data (`gon_media`)

The Rails web frontend embeds a JavaScript variable `gon_media` in every HTML
page. This contains server-rendered state that the video player and UI
components consume.

### Where It Lives

`window.gon_media` — injected into the `<script>` tags of every page served
by `https://some-content-provider.com`.

### Keys

| Key | Always Present? | Content |
|-----|-----------------|---------|
| `env` | ✅ | `"production"` |
| `user` | ✅ | `{id, uid, email, admin, staff, lang, active, guest, avatar, ...}` |
| `u` | ✅ | `{s: bool}` — subscription status? |
| `i18n` | ✅ | Full Rails i18n translations JSON |
| `svg_sprite` | ✅ | SVG sprite asset URL |
| `web_push_vapid_public_key` | ✅ | Web Push API VAPID key |
| `hosts` | ✅ | `{list: [10 CDN hosts], speed_steps: [[low, high], ...]}` |
| `captcha_invisible_key` | ✅ | Cloudflare Turnstile invisible site key |
| `captcha_interactive_key` | ✅ | Cloudflare Turnstile interactive site key |
| `s` | Only on player pages | Base64-encoded stream URLs per quality |
| `h` | Only on player pages | Escaped HTML for the page content |
| `skip` | **Not found in testing** | Intro/outro segments (hypothetical, per earlier reverse-engineering) |
| `media` | **Not found in testing** | Media object (hypothetical) |

### Stream Object (`s`) Structure

```javascript
{
  "MjQwcA==": "base64-encoded-mp4-url",   // 240p
  "MzYwcA==": "base64-encoded-mp4-url",   // 360p
  "NDgwcA==": "base64-encoded-mp4-url",   // 480p
  "NzIwcA==": "base64-encoded-mp4-url",   // 720p
  "MTA4MHA=": "base64-encoded-mp4-url"    // 1080p (if available)
}
```

Each key is base64 of the quality label. Each value is base64 of a full URL:
```
https://{cdn}/uploads/video/file/{id}/{filename}_{quality}.mp4?wmsAuthSign={token}
```

### Usage in subsloth

`gon_media` is **not available to native apps** — it's only present in the
rendered HTML page. subsloth cannot access it without either:
1. Scraping the web page (brittle, requires Cloudflare bypass)
2. Reimplementing the server-side logic that generates it (proprietary)

### References

- [Live Verification §3](live-verification.md#3-web-frontend-structure) — page structure and data
- [Skip Intro/Outro Analysis](features/skip-intro-outro.md) — `gon_media.skip` investigation

---

## 4. Streaming & CDN Layer

The video delivery infrastructure. This is not a REST API but a media serving
layer with multiple CDN endpoints.

### CDN Hosts

```javascript
// From gon_media.hosts.list
[
  "static-us2.some-content-provider.com",    // Static origin - US
  "static-sg2.some-content-provider.com",    // Static origin - Singapore
  "static-de3.some-content-provider.com",    // Static origin - Germany
  "static-fr.some-content-provider.com",     // Static origin - France
  "edge-se.some-content-provider.com",       // Edge - Sweden
  "edge-ch.some-content-provider.com",       // Edge - Switzerland
  "edge-tr.some-content-provider.com",       // Edge - Turkey
  "edge-es.some-content-provider.com",       // Edge - Spain
  "edge-ru2.some-content-provider.com",      // Edge - Russia
  "edge-ru4.some-content-provider.com",      // Edge - Russia (alt)
]
```

Plus mirror: `uploads.content-provider-mirror.tv`

### Stream Formats

| Source | Format | URL Pattern |
|--------|--------|-------------|
| **Kodi API** | Direct MP4 | `https://{static}/{path}_{quality}.mp4?wmsAuthSign={token}` |
| **Web frontend** | SMIL → HLS (fMP4) | `https://{edge}/{path}.smil/playlist_fmp4.m3u8?wmsAuthSign={token}` |

### Auth Token (`wmsAuthSign`)

A URL-safe base64-encoded payload containing:
- User ID + UID (access control)
- Video ID
- Server timestamp (validity window)
- HMAC hash

Format (decoded):
```
aWQ9{euser_id}+{uid}+video+{video_id}&server_time={timestamp}&hash_value={hmac}&validminutes=1920&strm_len={len}
```

Validity window: **1920 minutes** (32 hours).

### Speed Selection

The player selects the optimal CDN by measuring latency against the `hosts.list`
and matching against `speed_steps`:
- Band 1: 10–1000ms
- Band 2: 340–1600ms

### References

- [Live Verification §6](live-verification.md#6-cdn-infrastructure) — CDN details and speed selection

---

## 5. Kodi Plugin (Reference Implementation)

The official some-content-provider.com Kodi addon at version 4.0.4. This is the authoritative
reference for how the API is consumed.

### Source

```
https://some-content-provider.com/kodi/plugin.video.some-content-providertv/
```

Files available: `plugin.video.some-content-providertv-{version}.zip` (3.5.1–4.0.4).

### Key Files

| File | Purpose |
|------|---------|
| `some-content-providerAPI.py` | REST API client — domain fallback, Basic auth, error handling |
| `default.py` | Main plugin logic — listings, player, search, subtitles |
| `service.py` | Background library update service |
| `contextMenu.py` | Context menu handlers |
| `cache.py` | Kodi cache wrapper |

### Architecture Summary

```
default.py (Kodi plugin entry point)
  └── ApiClient (some-content-providerAPI.py) — REST client
        ├── GET /api/v2/shows        → index().shows()
        ├── GET /api/v2/shows/{id}   → index().episodes()
        ├── GET /api/v2/episodes/{id} → index().resolve()
        ├── GET /api/v2/movies       → index().movies()
        └── GET /api/v2/movies/{id}  → index().resolve()
  └── player (default.py) — Kodi Player subclass, handles HLS via InputStream Adaptive
  └── cache (cache.py) — Time-based Kodi cache
  └── contextMenu (contextMenu.py) — Favorites, subscriptions, library
```

### What subsloth Can Learn

| Feature | Kodi Plugin | subsloth |
|---------|-------------|----------|
| Domain fallback | ✅ Two domains + 1-year cache | ❌ Missing |
| Timeout config | ✅ 10s default, 16s for movies | ⚠️ Hardcoded 30s |
| Error notifications | ✅ Kodi UI dialogs per error code | ✅ `ResponseValidationException` → `Outcome` |
| Stream playback | ✅ SMIL/HLS via InputStream Adaptive | 🔄 Planned |
| Caching | ✅ 24h for listings, 2h for media info | ❌ Missing |
| Subtitles | ✅ From API episode response | ❌ Not implemented |

### References

- [v4.0.4 Source Files](https://some-content-provider.com/kodi/plugin.video.some-content-providertv/plugin.video.some-content-providertv-4.0.4.zip)
- [Live Verification §7](live-verification.md#7-kodi-plugin-v404-vs-subsloth-client-alignment) — detailed comparison
- [Skip Intro/Outro Analysis §Kodi Plugin Analysis](features/skip-intro-outro.md#kodi-plugin-analysis-v404)

---

## 6. Cloudflare Protection Layer

Not an API, but affects access to all some-content-provider.com HTTP endpoints. Subsloth must
navigate this layer when accessing the web frontend.

### Challenge Types

| Type | Affected Endpoints | Bypass |
|------|-------------------|--------|
| **Turnstile (non-interactive)** | Login POST (`/en/users/sign_in`) | Real browser + JavaScript |
| **JS Challenge** | All `some-content-provider.com` pages | Real browser + JavaScript |
| **None** | Kodi API (`front.some-content-provider.com`) | None needed — no Cloudflare |

### Keys (from `gon_media`)

- **Invisible**: `0x4AAAAAAB2g0UWBeKhEyjFn`
- **Interactive**: `0x4AAAAAAB2ge-7UbKaQHUYK`

### Impact on subsloth

| Access Path | Cloudflare? | Feasible for KMP? |
|-------------|-------------|-------------------|
| Kodi API (`front.some-content-provider.com`) | ❌ No | ✅ Yes — Basic auth from any HTTP client |
| Web frontend (`some-content-provider.com`) | ✅ Yes | ❌ Requires browser + session |
| Stream URLs (CDN) | ❌ No | ✅ Yes — token-based auth |

### Persistent Profile Workaround

For development/testing, a Chrome persistent profile (`--user-data-dir`) with
a once-solved Cloudflare challenge bypasses the check for ~1 year.

### References

- [Live Verification §2](live-verification.md#2-cloudflare-bypass) — bypass details
- [Live Verification §10](live-verification.md#10-persistent-profile-for-future-work) — persistent profile setup

---

## 7. Summary Table

| Surface | Base URL | Auth | Cloudflare? | Used by subsloth? | Data Value |
|---------|----------|------|-------------|-------------------|------------|
| **Kodi API** | `front.some-content-provider.com/api/v2` | Basic | ❌ No | ✅ **Primary** | Shows, episodes, movies (all content) |
| **Frontend API** | `some-content-provider.com/api/frontend` | Session cookie | ✅ Yes | ❌ Not recommended | Comments, notifications (social only) |
| **gon_media** | (embedded in HTML) | Session cookie | ✅ Yes | ❌ Not available | Stream URLs, user state, i18n |
| **CDN Streams** | `*.some-content-provider.com/uploads/...` | Token (`wmsAuthSign`) | ❌ No | 🔄 Planned | Video/audio segments |
| **Kodi Plugin** | (source code) | N/A (reference) | N/A | ✅ Inspected | Implementation patterns |
| **Cloudflare** | (middleware) | N/A | N/A | ⚠️ Must avoid | Blocks headless/automated access |

### Recommendations

1. **Use the Kodi API** for all content fetching — it's the only surface that
   works from a native KMP app without browser infrastructure.
2. **Avoid the frontend API** — it requires session cookies and provides no
   data that the Kodi API doesn't already offer.
3. **Parse `gon_media` only if necessary** — it's only accessible via web
   scraping, which is fragile and Cloudflare-blocked.
4. **Implement domain fallback** for the Kodi API (matching the Kodi plugin's
   two-domain strategy with caching).
5. **Consider SMIL/HLS parsing** for adaptive streaming if subsloth needs
   quality selection beyond what direct MP4 URLs provide.
