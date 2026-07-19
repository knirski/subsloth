# Live Verification Findings — some-content-provider.com Ecosystem

**Date**: 2026-07-19  
**Tools**: agent-browser 0.32.2 (via NixOS Chromium 150), curl, Kodi API  
**Account**: Free-tier some-content-provider.com (sub ID 0000000, email user@example.com)

---

## 1. agent-browser on NixOS

### Problem
The agent-browser binary and its bundled Chrome (Chrome for Testing) are dynamically
linked ELF executables. NixOS rejects these with `stub-ld` because `/lib64/ld-linux-x86-64.so.2`
does not exist in the Nix store.

### Solutions Tested

| Approach | Result | Notes |
|----------|--------|-------|
| `patchelf --set-interpreter` on agent-browser | ✅ Works | Rust binary, only needs glibc (ld-linux) |
| `patchelf` on bundled Chrome | ❌ Fragile | 32 shared libs to patch |
| `buildFHSEnv` (Nix) | ❌ Fails | Libs in per-package subdirectories, Chrome can't find them |
| `steam-run` | ❌ Unsuitable | Requires `allowUnfree = true` |
| **Nix Chromium + `--executable-path`** | ✅ **Best** | Uses nixpkgs `chromium` which has all deps |
| **`AGENT_BROWSER_EXECUTABLE_PATH` env var** | ✅ Also works | Same as `--executable-path` via env |

### Result: Working Wrapper
`./agent-browser` — a shell wrapper that:
1. Detects nixpkgs Chromium path via `nix eval --raw nixpkgs#chromium.outPath`
2. Sets `AGENT_BROWSER_EXECUTABLE_PATH` to the Chromium binary
3. Runs inside `nix shell nixpkgs#chromium` for all library dependencies

```bash
# Quick test
./agent-browser open https://example.com
./agent-browser snapshot -i

# Connect to externally-launched Chrome (e.g. with persistent profile)
./agent-browser --cdp 9222 open "https://some-content-provider.com/en"
```

---

## 2. Cloudflare Bypass

### Challenge
`some-content-provider.com` uses Cloudflare JS challenge (Turnstile) on all pages. curl and
headless agent-browser both hit the challenge. The checkbox auto-resolves in
headless mode but the verification step hangs indefinitely (Cloudflare detects
headless Chrome).

### Solution: Persistent Chrome Profile

```bash
# One-time setup: solve Cloudflare + log in
nix shell nixpkgs#chromium --command chromium \
  --remote-debugging-port=9222 \
  --user-data-dir=/home/krzysiek/.config/content-provider-profile

# Then connect from agent-browser in another terminal
./agent-browser --cdp 9222 open "https://some-content-provider.com/en"
```

The `--user-data-dir` flag creates a persistent Chrome profile that stores:
- Cloudflare challenge cookies (solved once, valid for ~1 year)
- some-content-provider.com session cookies
- localStorage and IndexedDB

This approach requires **manual** Cloudflare solving once, then all subsequent
sessions automatically pass the challenge.

### Cloudflare Turnstile Keys (from gon_media)
- **Invisible**: `0x4AAAAAAB2g0UWBeKhEyjFn`
- **Interactive**: `0x4AAAAAAB2ge-7UbKaQHUYK`

---

## 3. Web Frontend Structure

### Navigation
| Path | Content |
|------|---------|
| `/en` | TV Shows (homepage), no separate Movies section |
| `/en/shows` | TV Shows listing (same as `/en`) |
| `/en/channels` | YouTube channels |
| `/en/users/sign_in` | Login page |
| `/en/shows/{slug}` | Show page with season/episode list + embedded player |
| `/en/shows/{slug}#{season}-{episode}` | Episode player page |

**Note**: There is NO `/en/movies` path. Movies appear to be accessible only
through the Kodi API, not the web frontend.

### Login Flow
1. `GET /en/users/sign_in` — Login form (works without Cloudflare)
2. `POST /en/users/sign_in` — Triggers Cloudflare challenge
3. Use `authenticity_token` from page (CSRF token) in POST body
4. On success: redirect to `/en`, session cookie set

### Page Data (gon_media)
When a video player is loaded, `window.gon_media` contains:

| Key | Content |
|-----|---------|
| `env` | Rails environment (`"production"`) |
| `user` | User object: `{id, uid, email, admin, staff, lang, active, guest, avatar, ...}` |
| `u` | User settings: `{s: false}` (subscription status?) |
| `i18n` | Full Rails i18n translations JSON |
| `svg_sprite` | SVG sprite URL |
| `web_push_vapid_public_key` | Web Push VAPID key |
| `hosts` | CDN host list + speed test steps |
| `captcha_invisible_key` | Cloudflare Turnstile invisible site key |
| `captcha_interactive_key` | Cloudflare Turnstile interactive site key |
| `s` | Base64-encoded stream URLs per quality |
| `h` | Page HTML (escaped, embedded) |

### User Account State
```json
{
  "id": 0,  // REDACTED
  "uid": "REDACTED",
  "email": "user@example.com",
  "admin": false,
  "staff": false,
  "lang": "pl",
  "active": false,
  "guest": false,
  "wants_web_push_notifications": true,
  "comments_blocked": false
}
```

`active: false` + `guest: false` → likely a free tier account (not subscribed,
but registered).

### Stream URL Structure
The `gon_media.s` object maps base64-encoded quality labels to base64-encoded
stream URLs:

```javascript
// Decoded example:
// Key "MjQwcA==" → "240p"
// Value → "https://{cdn}.some-content-provider.com/uploads/video/file/{id}/{filename}_{quality}.mp4?wmsAuthSign={token}"
```

Qualities observed: `240p`, `360p`, `480p`, `720p`, `1080p`

The `wmsAuthSign` token is a URL-safe base64 encoded payload that includes:
- User ID and UID (for access control)
- Video ID
- Server timestamp
- Hash/validity window

Format: Direct `.mp4` files (NOT HLS `.m3u8` playlists — the Kodi plugin uses
SMIL files to serve HLS, but the web frontend uses direct MP4 URLs).

---

## 4. Kodi API (front.some-content-provider.com)

### Authentication
- **Method**: HTTP Basic Auth
- **Credentials**: Same as web login (email + password)
- **User-Agent**: `Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1`
- **Headers**: `Accept: application/json`, `Content-Type: application/json`

### API Domains
| Domain | Status | Notes |
|--------|--------|-------|
| `front.some-content-provider.com` | ✅ HTTP 200 with auth | Primary API |
| `front.some-content-provider-mirror.tv` | ✅ Same as above | Fallback |
| `front.unreachable-mirror.example.com` | ❌ Unreachable | Dead/misconfigured (default in CaptureApi) |

### Endpoints

| Endpoint | Method | Returns |
|----------|--------|---------|
| `/api/v2/shows` | GET | `{"shows": [...]}` — list of all shows |
| `/api/v2/shows/{id}` | GET | Full show detail with episodes array |
| `/api/v2/episodes/{id}` | GET | Episode detail (404 if invalid ID) |
| `/api/v2/movies` | GET | `{"movies": []}` — **empty** (no movies available on this account) |
| `/api/v2/movies/{id}` | GET | Movie detail |

### Show Response Structure
```json
{
  "id": 2446,
  "name": "1971: The Year That Music Changed Everything",
  "imdb_rating": 7.6,
  "year": "2021",
  "desc": "...",
  "slug": "1971-the-year-that-music-changed-everything",
  "ended": true,
  "length": 45,
  "user_popularity": 0,
  "array_genres": ["Documentary"],
  "array_countries": ["US"],
  "backdrop_url": "https://...",
  "poster_thumb": "https://...",
  "imdb_id": "14544732",
  "tmdb_id": "124339",
  "myshows_id": 72991,
  "kinopoisk_id": "",
  "newest_video": 1624903243,
  "updated_at": 1773492497,
  "seasons": 1,
  "episodes": [...]
}
```

### Episode Structure (from show detail)
```json
{
  "id": 65535,
  "name": "What's Happening?",
  "plot": "...",
  "season": 1,
  "number": "1",
  "resolution": "HD",
  "watched_media_count": 331,
  "myshows_id": 17577282,
  "info": {
    "smil": "/uploads/video/file/65535/...smil",
    "thumbs": 597,
    "original": "/uploads/video/file/65535/...mkv"
  },
  "versions": {
    "240p": "..._240p.mp4",
    "360p": "..._360p.mp4",
    "480p": "..._480p.mp4",
    "720p": "..._720p.mp4"
  },
  "airdate": "2021-05-22",
  "updated_at": 1725478014
}
```

**Key fields missing from Kodi API** (compared to web frontend):
- No `subtitles` array (available in web player)
- No `url` or `download_url` with auth tokens (generated client-side by web player)
- No `skip` / intro/outro data
- No `backdrop_url` or poster URLs at episode level

### Movies
The `/api/v2/movies` endpoint returns `{"movies": []}` — no movies available.
Movies may require a paid subscription or may have been removed from the catalog.

---

## 5. Skip Intro / Outro

### Live Verification Result
**No skip intro/outro data found** on any inspected TV show episode page:

| Show | Episode | `gon_media.skip` |
|------|---------|-------------------|
| Silo S01E01 | "Freedom Day" | `null` |
| Grey's Anatomy S01E01 | Unnamed | `null` |
| Ted Lasso S01E01 | "Pilot" | `null` |
| Various | Home/shows page | Key absent entirely |

### Conclusion
The `gon_media.skip` feature described in the earlier reverse-engineering
analysis could not be confirmed. Possible explanations:
1. **Only available for select curated content** (not all shows/movies)
2. **Feature removed or changed** since the original analysis
3. **Requires a paid subscription** (free tier doesn't get skip data)
4. **Loaded dynamically** via a separate API call after player initialization
   (not in the initial gon_media payload)

The Kodi API also returns **no skip-related fields** in any response.

---

## 6. CDN Infrastructure

some-content-provider.com uses a multi-CDN architecture with 10 edge servers:

```
Static origins:
  static-us2.some-content-provider.com
  static-sg2.some-content-provider.com
  static-de3.some-content-provider.com
  static-fr.some-content-provider.com

Edge servers:
  edge-se.some-content-provider.com
  edge-ch.some-content-provider.com
  edge-tr.some-content-provider.com
  edge-es.some-content-provider.com
  edge-ru2.some-content-provider.com
  edge-ru4.some-content-provider.com
```

Plus a mirror domain for API: `uploads.some-content-provider-mirror.tv`

Speed selection uses two latency bands:
- Band 1: 10–1000ms
- Band 2: 340–1600ms

---

## 7. Kodi Plugin (v4.0.4) vs subsloth Client Alignment

### Auth
| Aspect | Kodi Plugin | subsloth |
|--------|-------------|----------|
| Auth method | Basic auth in URL (`user:pass@host`) | Basic auth header |
| User-Agent | `Kodi ({version})` | `Kodi/20.2 (Nexus; Linux; Android) Media/4.0.1` |
| Login | Set via settings | Env vars / Gradle config |

### API
| Aspect | Kodi Plugin | subsloth |
|--------|-------------|----------|
| Domain fallback | ✓ Two domains + cache | ❌ Single domain (`DEFAULT_BASE_URL`) |
| Timeout | 10s default, 16s for movies | 30s default |
| Error handling | HTTP 401/402 → Kodi notifications | `ResponseValidationException` → `Outcome.Failure` |
| Caching | Kodi cache (24h for lists) | No client-side caching yet |

### Gaps Fixed in PR #194
- ✅ `Content-Type: application/json` header added
- ✅ HTTP 402 (free limit) handling added to `ResponseValidationPlugin`
- ✅ `DEFAULT_BASE_URL` made `internal` for test access
- ✅ `SUBSLOTH_URL` env var support for tests and capture scripts
- ✅ API drift CI workflow

### Remaining Gaps
- ❌ Domain fallback (front.some-content-provider.com → front.some-content-provider-mirror.tv)
- ❌ Request timeout configuration (currently hardcoded)
- ❌ `User-Agent` format variance (Kodi plugin uses `Kodi ({version})`)
- ❌ No SMIL/HLS streaming support (might not be needed for KMP)

---

## 8. Scripts & Tooling

### CaptureApi (`testing/api-contract/`)
- Default API base: `https://front.unreachable-mirror.example.com/api/v2` (DEAD — should be `front.some-content-provider.com`)
- Captures 5 endpoints: movies list, shows list, movie detail, show detail, episode detail
- **Movies endpoint returns empty** — capture will produce empty fixtures
- Auth via Basic header with env vars `SUBSLOTH_LOGIN`, `SUBSLOTH_PASSWORD`

### ApiLiveDriftTest (`core/network/src/jvmTest/`)
- Reads `SUBSLOTH_URL`, `SUBSLOTH_LOGIN`, `SUBSLOTH_PASSWORD` from env
- Hits all 5 endpoints via the Ktor client
- Uses `apiCall{}` wrapper for structured error handling
- Has connectivity check + diagnostic messages
- **Requires `SUBSLOTH_URL` to include `/api/v2/` path** (GitHub secret needs update)

---

## 9. Web Frontend API (`/api/frontend/`)

### Discovery
During live exploration, the web frontend was found to make requests to a
frontend-specific API at `https://some-content-provider.com/api/frontend/`. This is separate
from the Kodi API (`https://front.some-content-provider.com/api/v2/`).

### Endpoints Found

| Endpoint | Method | Auth Required | Returns |
|----------|--------|---------------|---------|
| `/api/frontend/comments` | GET | Session cookie | JSON — comments for shows/movies |
| `/api/frontend/search` | GET | Session cookie | HTML (not JSON — renders search results page) |
| `/api/frontend/notifications` | GET | Session cookie | JSON `{}` — user notifications |

### Comments API Example
```
GET /api/frontend/comments?commentable_type=Show&commentable_id=2119&sort=newest
```
Returns JSON array of comments with author info, timestamps, likes, spoiler
flags, and nested replies.

### Key Observations

1. **The `/api/v2/` Kodi API works from the web frontend too** — calling
   `/api/v2/shows/2446` from within a browser session returns exactly the same
   data as when called via Basic auth from curl. The Rails session cookie does
   **not** change the API response.

2. **No skip data from any API** — neither `/api/v2/` (Kodi) nor
   `/api/frontend/` (web) provides skip intro/outro data.

3. **Subtitles are not available via any REST endpoint** — they are embedded
   in `gon_media.s` as base64-encoded HLS stream URLs that serve SMIL
   playlists. The VTT subtitle files are hosted on the CDN but there is no
   dedicated subtitles API.

4. **Stream URLs differ between Kodi API and web frontend**:
   - **Kodi API** returns direct `.mp4` file paths (e.g.
     `/uploads/video/file/{id}/{filename}_{quality}.mp4`)
   - **Web frontend** uses SMIL-based HLS with fMP4 segments (e.g.
     `/uploads/video/file/{id}/{filename}.smil/playlist_fmp4.m3u8`)
   - Both require `wmsAuthSign` tokens for access

### Can subsloth Use the Frontend API?

**Yes, but with significant caveats:**

| Requirement | Status |
|-------------|--------|
| Rails session cookie | ❌ Requires web login (CSRF token + Cloudflare bypass) |
| Cloudflare clearance | ❌ Headless browsers get challenged |
| Basic auth support | ✅ `/api/v2/` supports Basic auth (Kodi API) |
| Data beyond Kodi API | ⚠️ Minimal — comments and notifications only |
| Skip intro/outro data | ❌ Not available via any API |

**Verdict: Not recommended for the main data pipeline.** The frontend API
offers no significant data beyond what the Kodi API already provides. The
Kodi API is simpler (Basic auth, no Cloudflare, no CSRF) and covers all
content needs. The frontend API could be useful for:
- Reading user comments (social feature)
- Checking notifications
- But both require a maintained web session.

---

## 10. Persistent Profile for Future Work

```bash
# Start Chrome with persistent profile
nix shell nixpkgs#chromium --command chromium \
  --remote-debugging-port=9222 \
  --user-data-dir=/home/krzysiek/.config/content-provider-profile

# In another terminal (or agent session):
export SUBSLOTH_LOGIN SUBSLOTH_PASSWORD  # from ~/.env
./agent-browser --cdp 9222 snapshot -i
```

The profile at `~/.config/content-provider-profile` persists:
- Cloudflare challenge clearance
- some-content-provider.com login session
- Any other site auth

This makes it possible to resume authenticated exploration in future sessions
without re-authenticating.
