# Media API Discovery

This document records the credentialed API and web-frontend discovery findings for the Media platform. It serves as the reference for OpenAPI refinement, DTO mapping, fixture capture, and UI capability gates.

## Sources

| Source | Description |
|--------|-------------|
| Kodi plugin `4.0.1` | Python source: `plugin.video.mediatv-4.0.1.zip` from https://media.tv/kodi/ |
| Native API captures | Live responses from `front.media-mirror.tv/api/v2/` via `captureApi` |
| Browser HAR captures | Firefox HAR exports from `media.tv` browsing sessions (May 2026) processed via `exportFixtures` |
| `docs/agent/capture-workflow.md` | Capture pipeline instructions |

---

## Kodi Plugin REST API

### Request identity

```
Base URL:  https://front.media-mirror.tv/api/v2/
Auth:      HTTP Basic (email:password, Base64-encoded)
User-Agent: Kodi (4.0.1)
Accept:     application/json
Content-Type: application/json
```

The User-Agent is built dynamically as `"Kodi (%s)" % addon_version()` (see `mediaAPI.py:new_request`).  The version number comes from the addon manifest and changes with each release.

### Endpoints

The Kodi plugin calls **exactly five** endpoints — no more:

| Method | Path | Usage |
|--------|------|-------|
| `GET` | `/movies` | Full movie catalogue |
| `GET` | `/shows` | Full TV show catalogue |
| `GET` | `/movies/{id}` | Movie detail (subtitles, versions, download URL) |
| `GET` | `/shows/{id}` | Show detail with flat `episodes` list and `seasons` count |
| `GET` | `/episodes/{id}` | Episode playback detail (stream URL, subtitles, qualities) |

### No server-side pagination

The Kodi plugin calls `get('/movies')` and `get('/shows')` **without query parameters**.  The API returns the **complete catalogue** (7,797 movies, 3,915 shows as of May 2026).  The plugin performs all filtering, sorting, and pagination locally in Python.

The `per_page`, `page`, `sort`, `genre`, `country`, `subtitles`, `year_from`, `year_to`, `rating_from`, and `rating_to` query parameters documented in the OpenAPI spec exist on the API but are **not used by the Kodi plugin**.  They are available for future use but must be verified against live API behaviour before the Android app depends on them.

### What the Kodi plugin does NOT call

- **Favourites (`/favorite_media`)**: Stored locally in a file (`favData`). The plugin fetches the full show list, then filters to matching IDs read from the local file.  No server-side favourite API call exists in the plugin.

- **Subscriptions (`/subscriptions`)**: Same pattern — stored in a local file (`subData`), matched against the full show list.

- **Watched media (`/watched_media/toggle`)**: Not present in the plugin. Watched state is tracked locally by Kodi itself.

- **Comments (`/api/frontend/comments`)**: Explicitly excluded by project policy.  The native app must never depend on comments.

- **Subtitles**: The plugin extracts subtitle URLs from the `subtitles` array in episode/movie detail responses.  It downloads them from signed CDN URLs (e.g. `https://static-de3.media.tv/...`).  There is no fixed subtitle API endpoint — the URLs are ephemeral and embedded in detail responses.

- **Video downloads**: The plugin reads `download_url` from episode/movie detail responses.  These are signed CDN URLs that redirect (302) to the actual video file.

### Response shapes (from live captures)

#### Movie list (`GET /movies`)

```json
{
  "movies": [
    {
      "id": 137,
      "name": "Shooter",
      "slug": "shooter",
      "year": "2007",
      "imdb_id": "0822854",
      "imdb_rating": 7.1,
      "desc": "A marksman living in exile is coaxed back into action…",
      "poster": "https://…/poster.jpg",
      "poster_thumb": "https://…/thumb.jpg",
      "backdrop_url": "https://…/backdrop.jpg",
      "array_genres": ["Action", "Crime", "Drama"],
      "resolution": "HD",
      "updated_at": 1776609394
    }
  ]
}
```

Notes:
- No `meta` pagination object is returned (despite the DTO allowing it).
- `desc` can be an empty string for some entries.
- `genres` (comma-separated string) is absent; only `array_genres` is present.
- `year` is a **string** (`"2007"`), not an integer.
- `resolution` is a string label (`"HD"`, `"1080p"`, `"SD"`), not an object.

#### Show list (`GET /shows`)

```json
{
  "shows": [
    {
      "id": 3381,
      "name": "The Boys",
      "slug": "the-boys",
      "year": "2019",
      "imdb_id": "1190634",
      "imdb_rating": 8.5,
      "tmdb_id": 76479,
      "desc": "A group of vigilantes set out to take down corrupt superheroes…",
      "poster_thumb": "https://…/thumb.jpg",
      "backdrop_url": "https://…/backdrop.jpg",
      "array_genres": ["Action", "Drama", "Sci-Fi"],
      "array_countries": ["US"],
      "ended": false,
      "length": 60,
      "newest_video": 1778300000,
      "user_popularity": 980,
      "kinopoisk_id": ""
    }
  ]
}
```

Notes:
- `kinopoisk_id` is present but **empty string** for most shows (not null).
- `length` is episode duration in minutes.
- `newest_video` is a Unix timestamp (seconds) of the most recent episode.

#### Movie detail (`GET /movies/{id}`)

```json
{
  "id": 137,
  "name": "Shooter",
  "slug": "shooter",
  "year": "2007",
  "imdb_id": "0822854",
  "imdb_rating": 7.1,
  "desc": "…",
  "poster": "https://…/poster.jpg",
  "poster_thumb": "https://…/thumb.jpg",
  "backdrop_url": "https://…/backdrop.jpg",
  "array_genres": ["Action", "Crime", "Drama"],
  "array_countries": ["US"],
  "resolution": "HD",
  "length": 7200,
  "updated_at": 1776609394,
  "trailer": "https://www.youtube.com/embed/…",
  "url": "https://edge-se.media.tv/…",
  "download_url": "https://edge-se.media.tv/…",
  "subtitles": [
    {
      "lang": "English",
      "code": "en",
      "url": "https://static-de3.media.tv/…",
      "download_url": "https://static-de3.media.tv/…",
      "format": "vtt"
    }
  ],
  "versions": [
    {
      "resolution": "1080p",
      "url": "https://…",
      "mime_type": "video/mp4"
    }
  ]
}
```

Notes:
- **No `qualities` array** — the field is absent.  Video sources are in `versions`.
- `resolution` at the top level is a string label.
- `trailer` is a YouTube embed URL, not a direct video URL.
- `url` and `download_url` are **signed, ephemeral** — never persist in fixtures.

#### Show detail (`GET /shows/{id}`)

```json
{
  "id": 3381,
  "name": "The Boys",
  "slug": "the-boys",
  "year": "2019",
  "imdb_id": "1190634",
  "imdb_rating": 8.5,
  "tmdb_id": 76479,
  "desc": "…",
  "poster_thumb": "https://…/thumb.jpg",
  "backdrop_url": "https://…/backdrop.jpg",
  "array_genres": ["Action", "Drama", "Sci-Fi"],
  "array_countries": ["US"],
  "ended": false,
  "length": 60,
  "seasons": 5,
  "newest_video": 1778300000,
  "episodes": [
    {
      "id": 82476,
      "name": "The Name of the Game",
      "season": 1,
      "number": 1,
      "show_name": "The Boys",
      "airdate": 1562200000,
      "plot": "…",
      "resolution": "1080p",
      "url": "https://edge-se.media.tv/…",
      "download_url": "https://edge-se.media.tv/…",
      "subtitles": [
        {
          "lang": "English",
          "code": "en",
          "url": "https://static-de3.media.tv/…",
          "format": "vtt"
        }
      ],
      "updated_at": 1737240351
    }
  ]
}
```

Notes:
- `seasons` is an **integer** count (e.g. `5`), not a list of season objects.
- `episodes` is a **flat array** across all seasons — the Kodi plugin uses this
  directly.
- Each episode has its own `subtitles` array and `url`/`download_url`.

#### Episode detail (`GET /episodes/{id}`)

```json
{
  "id": 82476,
  "name": "The Name of the Game",
  "show_name": "The Boys",
  "season": 1,
  "number": 1,
  "plot": "…",
  "resolution": "1080p",
  "airdate": 1562200000,
  "updated_at": 1737240351,
  "url": "https://edge-se.media.tv/…",
  "download_url": "https://edge-se.media.tv/…",
  "subtitles": [
    {
      "lang": "English",
      "code": "en",
      "url": "https://static-de3.media.tv/…",
      "download_url": "https://static-de3.media.tv/…",
      "format": "vtt"
    }
  ],
  "versions": [
    {
      "resolution": "1080p",
      "url": "https://…",
      "mime_type": "video/mp4"
    }
  ]
}
```

Notes:
- **No `created_at` field** — only `updated_at` is present.
- **No `qualities` array** — video sources are in `versions`.
- `airdate` is a Unix timestamp (seconds).

---

## Web-Frontend Endpoints (Browser-Only)

These endpoints were discovered from Firefox HAR captures of browsing sessions on `media.tv`. They are **not part of the Kodi plugin** and the Android app must not depend on them. They are documented here for completeness and to justify the `kodiSource = false` flag in `Endpoint.kt`.

### Comments (`/api/frontend/comments`)

```
GET /api/frontend/comments?commentable_type=Show&commentable_id=1723&sort=newest
```

Returns a JSON object with nested comment threads:

```json
{
  "comments": [
    {
      "id": 245076,
      "created_at": "2026-05-07T22:11:30.723+03:00",
      "text": "Now that's a dark fucking ending",
      "like_count": 9,
      "liked": false,
      "spoiler": false,
      "commentable_name": "The Boys",
      "commentable_slug": "the-boys",
      "author": {
        "id": 2737917,
        "avatar": null,
        "name": "Mediavich2.0",
        "staff": false
      },
      "comments": [
        {
          "id": 244992,
          "parent_id": 244966,
          "text": "…",
          "author": { "id": 1777718, "name": "Dan39" }
        }
      ]
    }
  ]
}
```

**Excluded from the native app by project policy.**

### Catalog filters (`/en/shows?filters[...]=...`)

```
GET /en/shows?filters[genres]=Animation&filters[countries]=US&page=1
GET /en/movies?filters[genres]=Action&filters[year_from]=2024&page=1
```

Returns JSON (not HTML) with filtered, paginated results:

```json
{
  "total_count": 302,
  "items": [
    {
      "id": 4188,
      "title": "Star Wars: Maul - Shadow Lord",
      "translated_genres": "Animacja, Fantastyczny…",
      "year": "2026",
      "imdb_rating": 8.7,
      "desc": "…",
      "network": "Disney+",
      "videos_count": 10,
      "image": "https://…"
    }
  ]
}
```

The response shape differs from the native API — it uses `items` (not `movies`/`shows`) and `total_count` (not `meta.total`).  Genre/country names are translated to the user's UI language.

### Favourite media (`/en/favorite_media`)

```
POST   /en/favorite_media?media_id=1723&media_list=favorites&media_type=Show
DELETE /en/favorite_media?media_id=1723&media_list=favorites&media_type=Show
POST   /en/favorite_media/reload
POST   /en/favorite_media?media_id=1723&media_list=watch_later&media_type=Show
```

Responses are **JavaScript** (content-type `text/javascript`), not JSON:
```javascript
media.alert('success', 'Dodano do listy "Ulubione"');
```

The Kodi plugin does **not** call these — it stores favourites locally.

### Watched media (`/en/watched_media/toggle`)

```
POST /en/watched_media/toggle?media_id=1723&media_type=Show
```

Response is JavaScript that updates checkbox UI state.  Not JSON.

### Subscriptions (`/en/shows/{slug}/subscriptions`)

```
POST   /en/shows/the-boys/subscriptions?kind=email
DELETE /en/shows/the-boys/subscriptions/574880
```

Response is JavaScript.  The Kodi plugin stores subscriptions locally.

### Push subscriptions (`/push_subscriptions`)

```
POST /push_subscriptions
POST /push_subscriptions/current_browser_subscribed
```

Browser service-worker push notification registration.  Response is JSON: `{"subscription":""}`.

### Message-bus polling (`/message-bus/{session}/poll`)

```
POST /message-bus/4f2d8a0e…/poll?_=9036
```

Returns a JSON array of real-time status updates:

```json
[
  {
    "global_id": -1,
    "message_id": -1,
    "channel": "/__status",
    "data": {
      "/Show/1723": 234
    }
  }
]
```

The `data` values are concurrent viewer counts.  Responses can be empty (`[]`).

### Subtitle download (web frontend)

```
GET /en/shows/the-boys/videos/100299/download_subtitle/pl
GET /en/movies/anniversary/download_subtitle/en
```

Returns **SRT plain-text**, not JSON.  The Kodi plugin downloads subtitles from signed CDN URLs embedded in episode/movie detail responses instead.

### Video download (web frontend)

```
GET /en/shows/the-boys/videos/100790/download
GET /en/movies/remarkably-bright-creatures/download
```

Returns **302 redirect** to a signed CDN URL.  No response body to capture.

### Speed tests (web frontend)

Exact URL pattern unconfirmed.  Believed to be playback-page requests to measure connection speed for adaptive bitrate selection.  Placeholder in `Endpoint.kt`.

### Trailer (web frontend)

Movie/show detail pages embed a YouTube iframe.  The `trailer` field in API responses contains a YouTube embed URL (e.g. `https://www.youtube.com/embed/PafnQJksbXs`).  No Media-specific trailer API exists.

---

## Fixture Capture

### Native fixtures

```bash
./gradlew :testing:api-contract:captureApi -Pemail=... -Ppassword=...
```

Calls the five Kodi API endpoints directly, applies sanitisation rules from `scripts/capture/sanitization-rules.json`, and writes fixture JSON to `testing/api-contract/src/main/resources/media/`.  No browser or HAR intermediate step.

### Web-discovery fixtures

Export HAR from Firefox DevTools (Network tab → Save All As HAR), then:

```bash
./gradlew :testing:api-contract:exportFixtures -PharFiles=session.har
```

The export pipeline categorises entries by URL pattern via `Endpoint.parse`, applies sanitisation rules, and writes fixtures to `testing/api-contract/src/main/resources/media/web-discovery/`.

### What can and cannot be captured as JSON

| Endpoint | Capturable as JSON? | Reason |
|----------|-------------------|--------|
| Movies, Shows | ✅ | Native API |
| Movie/Show/Episode Detail | ✅ | Native API |
| Comments | ✅ | Web frontend returns JSON |
| Catalog Filters | ✅ | Web frontend returns JSON |
| Message-bus (Statistics) | ✅ | Returns JSON array |
| Push Subscriptions | ✅ | Returns JSON |
| Favorite Media | ❌ | Returns JavaScript, not JSON |
| Watched Media | ❌ | Returns JavaScript, not JSON |
| Subscriptions | ❌ | Returns JavaScript, not JSON |
| Subtitle Download | ❌ | Returns SRT plain-text |
| Video Download | ❌ | 302 redirect, no body |
| Speed Tests | ❌ | Path unconfirmed |

---

## Endpoint Inventory

Defined in `testing/api-contract/src/main/kotlin/net/subsloth/testing/contract/Endpoint.kt`.
Each constant carries a `kodiSource` flag:

| Endpoint | `kodiSource` | Category |
|----------|-------------|----------|
| Movies | `true` | Native |
| Shows | `true` | Native |
| MovieDetail | `true` | Native |
| ShowDetail | `true` | Native |
| EpisodeDetail | `true` | Native |
| Comments | `false` | WebDiscovery |
| CatalogFilters | `false` | WebDiscovery |
| Statistics | `false` | WebDiscovery |
| PushSubscriptions | `false` | WebDiscovery |
| FavoriteMedia | `false` | WebDiscovery |
| WatchedMedia | `false` | WebDiscovery |
| Subscriptions | `false` | WebDiscovery |
| SubtitleDownload | `false` | WebDiscovery |
| Download | `false` | WebDiscovery |
| Speedtests | `false` | WebDiscovery |

---

## Fixture Sanitisation Rules

Every committed fixture must:

- Contain no credentials, auth headers, signed stream URLs, signed download
  URLs, or private account data
- Contain no raw browser logs, HAR files, snapshots, or authenticated screenshots
- Use `.invalid` TLD for all hostnames (e.g. `media.subsloth.invalid`)
- Use `[REDACTED]` for sensitive field values (password, email, auth tokens, etc.)

Rules are defined in `scripts/capture/sanitization-rules.json`.  Add new sensitive field patterns there before committing fixtures.

Real API response data is authoritative.  When a live capture produces different field shapes, values, or endpoints than the committed fixtures, the captured data wins — update fixtures and DTOs to match.

---

## Verification

After any fixture or DTO change:

```bash
./gradlew :testing:api-contract:test
./gradlew :core:network:testDebugUnitTest
```

This validates:
- All JSON fixtures exist on the classpath and parse as valid JSON
- Replay metadata and URL patterns are valid
- Programmatic WireMock stubs register and respond to HTTP requests with the expected method, status, and content type
- Web-discovery fixtures contain no sensitive fields
- Native fixtures decode against typed DTOs in `:core:network`
