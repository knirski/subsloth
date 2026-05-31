# subsloth Android App Specification

## Summary
Build a new native Kotlin Android app for phone, tablet, and Android TV to browse, search, play, download, and manage Media movies/series.

Defaults locked:
- New Android project in the empty workspace.
- App id / namespace: `subsloth`.
- Kotlin + Jetpack Compose + Compose for TV.
- Equal first-class TV/tablet/phone UX.
- Login page required before authenticated catalog access.
- User can logout from Settings.
- Network behavior should match the Media Kodi plugin as closely as practical from the server point of view: same API host/prefix, same endpoint set, same Basic auth style, same JSON headers, and Kodi-style `User-Agent` metadata.
- Do not use browser/WebView request identity, Cloudflare-verification flows, web-page scraping, web-only frontend APIs, or unrelated Media endpoints as part of the native app's v1 data source.
- Credentials encrypted at rest with Android Keystore-backed encryption and backup exclusion.
- Resume playback from last watched position.
- Offline playback for saved videos with or without saved subtitles.
- Safe high-value additions: Continue Watching, Next Episode, Offline Home Mode, Storage Management, in-player subtitle switcher, playback speed, local favorites/watch later, diagnostics page.
- Discovery-gated additions: quality selector, confirmed season download queue, and recently added/new episodes precision.
- Do not fetch, show, post, count, sort, or otherwise support comments for movies or TV series.
- Shared device-local app-private downloads for videos/subtitles.
- Optional live API drift tests using local developer credentials only.
- Arrow-first strongly-typed FP core: Kotlin sealed ADTs, exhaustive `when` expressions, immutable values, Arrow typed errors, Arrow Optics, Arrow Resilience, and Arrow ecosystem integrations where they fit.
- Device targets: Android TV 8, Android tablet 13, Android phone 16.
- Build baseline: AGP `9.2.0`, Gradle `9.5.0`, JDK `17`, `minSdk 26`, `targetSdk 36`, `compileSdk 36`.
- JDK `17` is the required build runtime. Do not use JDK `25` as the project baseline unless AGP, Gradle, Kotlin, Android Studio, and CI images explicitly support it for Android builds and verification has passed.

## API Discovery Gate
Before implementation locks DTOs, persistence schema, or UI fields, run credentialed API discovery against Media and update `api/subsloth.openapi.yaml` plus sanitized fixtures.

Discovery must verify:
- Exact JSON shape for `/movies`, `/movies/{movieId}`, `/shows`, `/shows/{showId}`, and `/episodes/{episodeId}`.
- Whether movies and episodes expose one fixed stream URL, multiple quality URLs, adaptive streams, or only a resolution label.
- Whether `download_url` is consistently available, subscription-gated, missing for some content, resumable, or single-use/signed.
- Exact subtitle structure, language identifiers, subtitle URL behavior, and whether subtitle URLs are stable enough for offline download.
- Whether trailer URLs are exposed for movies.
- Whether recently-added/new-episode precision can be based on actual added timestamps or only release/air dates.
- Error bodies and status codes for unauthorized, subscription/free-limit, unavailable content, and malformed IDs.
- Exact Kodi plugin request identity: `User-Agent` value format, required headers, accepted content types, query parameter names, endpoint paths, redirect behavior, and non-JSON error bodies.

Discovery constraints:
- Do not fetch comments endpoints.
- Do not scrape Media web detail pages as the native app's data source. Authenticated web detail pages can auto-load frontend comments resources; use only the Kodi/API contract instead.
- Do not use APIs, frontend resources, or account actions that the Kodi plugin does not use unless a later explicit design revision adds them.
- Do not store credentials, auth headers, signed stream URLs, signed download URLs, or private account data in fixtures.
- Do not keep or commit authenticated Playwright/browser logs, snapshots, screenshots, or HAR files. Only sanitized field names, shapes, and non-sensitive examples may be checked in.
- If discovery disproves a discovery-gated feature, keep the UI honest: hide the control or show an unavailable reason instead of simulating support.

Authenticated web discovery notes from May 4, 2026:
- A normal headed desktop Chrome-style user agent was used for manual authenticated inspection.
- Series web detail pages expose metadata, season tabs, episode anchors shaped like `#season-episode`, watched/favorite/watch-later actions, email notification subscription action, per-episode subtitle download links, and upcoming episode premiere text where available.
- Movie web detail pages expose metadata, Watch and Download buttons, favorite/watch-later/watched actions, and subtitle download links.
- The web player exposes quality labels `1080p`, `720p`, `480p`, `360p`, `240p`, and `auto`; speed labels `0.50x`, `0.60x`, `0.70x`, `0.80x`, `0.90x`, `1.00x`, `1.25x`, `1.50x`, and `2.00x`; subtitle language switching; next episode control; fullscreen; picture-in-picture; and Chromecast.
- Observed web playback uses an HTML5 video element backed by fragmented media resources from an edge host. Treat quality and download URL behavior as API-discovery-gated until the Kodi/API responses are confirmed.
- The web app can auto-load a frontend comments API resource on detail pages. The native app must not call or depend on that resource.

## Content Model And Detail Structure
Preserve Media's movie and series metadata/structure, but intentionally exclude comments everywhere.

Movie detail must include:
- Poster thumbnail.
- Title/name.
- Description/plot.
- Subtitle language availability.
- Watch/resume action.
- Download action.
- Trailer action if a trailer URL is available.
- Rating, release year, genres, countries, duration.
- Available video qualities/resolutions where returned or inferable.
- User/library state: favorite, watch later, watched/progress, downloaded availability.
- No comments UI or comment-derived metadata.

Series detail must include:
- Poster thumbnail and backdrop/fanart where available.
- Title/name and description/plot.
- Rating/IMDb rating, release year, genres, countries, status, duration/episode length.
- TMDb/IMDb IDs where available.
- Latest/newest video metadata where available.
- Available video qualities/resolutions where returned or inferable.
- User/library state: favorite, watch later/subscription, watched/progress, downloaded availability.
- No comments UI or comment-derived metadata.

Series episode structure:
- Seasons as selectable tabs/sections.
- Episodes grouped by season and sorted by episode number.
- Episode rows/cards include episode number, title, description/plot toggle, air date or added/updated age, resolution/available qualities where available, watched state, resume progress, subtitle languages, download action, and next-episode relationship.
- Preserve subtitle-language metadata from Media, including language codes/flags where API data supports it.
- Preserve upcoming episode metadata where Media exposes it, including future premiere dates for unreleased episodes; unreleased episodes must not show playable/downloadable actions.
- On TV, use D-pad-friendly poster/details/action rail, season selector, focused episode list, and clear play/resume/next actions.
- On tablet, use adaptive two-pane detail layout when width allows.
- On phone, use stacked detail layout with sticky primary actions where appropriate.

## Architecture
Use Functional Core / Imperative Shell:

- Functional core:
  - Pure Kotlin domain models, parsers, sorting/search/filtering, quality selection, next-episode selection, library update decisions, subtitle selection, playback resume decisions, offline playback decisions, storage cleanup decisions, playback/download intent construction.
  - Model domain states and errors as Kotlin sealed interfaces/classes with data classes/data objects, not strings, nullable sentinels, or exception-driven control flow.
  - Use `when` as an expression for sealed ADTs so the compiler enforces exhaustive handling when domain cases change.
  - Arrow typed errors are the default for recoverable domain/application failures.
  - Use Arrow `Raise` DSL inside use cases and domain services for idiomatic typed-error composition.
  - Use `Either` at module/API boundaries where callers need a value-level result.
  - Use `Option` for meaningful optional domain values; reserve Kotlin nullable types for DTOs, Android framework interop, and truly local implementation details.
  - Use `NonEmptyList` and `zipOrAccumulate`/validation patterns where multiple validation errors should be reported together.
  - Use Arrow Optics for immutable nested state updates where manual `copy(copy(copy(...)))` would obscure intent.
  - Use Arrow Resilience schedules/retry/circuit-breaker policies for retryable Media network operations and download metadata refresh.
  - Use tagless-final-inspired capability interfaces for effectful dependencies, but do not use full HKT/tagless-final machinery. Prefer small `suspend` ports with Arrow typed errors over `Kind<F, A>` abstractions.
  - No Android framework dependencies.

- Imperative shell:
  - Compose UI, ViewModels, Media3 player, WorkManager/startup sync, Room, DataStore, encrypted credentials, network, filesystem, notifications.
  - Side effects isolated behind interfaces.
  - Shell components interpret core ports/effects and translate Android/IO exceptions into typed Arrow errors before returning to the core/application layer.

Arrow design principles:
- Domain types should describe the real Media/media domain precisely enough that invalid states are hard or impossible to represent.
- Behaviors should declare their required capabilities explicitly through small port/effect interfaces; Hilt wires Android implementations only at the shell boundary.
- Prefer `suspend` functions for effectful ports so effects are descriptions that can be composed, tested, and interpreted by shell implementations.
- Capability ports should be tagless-final-inspired: use cases depend on abstract capabilities such as catalog, library, credentials, downloads, and playback ports, while shell modules provide Retrofit, Room, DataStore, filesystem, and Media3 interpreters.
- Avoid classic tagless-final/HKT style in Kotlin because it adds noise without enough benefit for this Android app. Do not introduce `Kind<F, A>`-style effect polymorphism unless a later implementation task proves a concrete need.
- Keep DTOs, Room entities, Media3 objects, Android permissions, and filesystem handles out of the functional core.
- Avoid unchecked exceptions for expected failures such as auth, free-limit, network, decode, missing subtitle, unavailable download, low storage, or unsupported quality.

Recommended modules:
- `:app`
- `:core:model`
- `:core:domain`
- `:core:network`
- `:core:database`
- `:core:preferences`
- `:core:media`
- `:feature:auth`
- `:feature:catalog`
- `:feature:details`
- `:feature:player`
- `:feature:library`
- `:feature:settings`
- `:testing:api-contract`

## Auth, Playback, Offline, Subtitles, And Quality
Authentication:
- Login on first launch when no encrypted credentials exist.
- Validate new or changed credentials by reproducing the Kodi plugin's normal authenticated startup request sequence. Do not add a special auth-only probe unless the Kodi plugin has one.
- Login and password fields allow normal Android Autofill/password manager integration.
- Do not implement custom paste-from-clipboard buttons, clipboard inspection, clipboard history, or clipboard clearing.
- Password input uses secure text entry and does not expose characters except through standard temporary reveal behavior if explicitly enabled by the user.
- Never log login text, password text, Autofill payloads, IME suggestions, clipboard contents, or validation request bodies.
- Login screen screenshots in tests or docs must use fake credentials only.
- On success, store credentials encrypted and navigate to catalog.
- On successful login, derive the active local account profile key from the normalized Media login using a non-reversible hash: `HMAC-SHA256(appLocalProfileSalt, normalizedLogin)`. Normalize by trimming whitespace, applying Unicode NFC, and using locale-independent lowercase for email-style logins.
- `appLocalProfileSalt` is app-private non-credential metadata. It is not cleared by logout or "Reset preferences"; clearing Android app data may delete it together with all local profiles.
- Do not store the raw login/email as a Room account identifier, DataStore key, download directory name, diagnostic field, or log value.
- After credentials are accepted and stored, reuse them for normal Kodi-compatible requests without proactive startup revalidation.
- If a normal request returns `401` or an equivalent auth failure, clear cached auth state, keep shared offline downloads/progress and account-scoped local data intact, and route the user to repair login.
- Logout clears encrypted credentials and in-memory auth state, cancels pending auth-required work, and returns to login.
- Logout retains all non-transient local data by default. Shared offline downloads and shared offline playback progress remain available; account-scoped preferences, online/cache metadata, favorites, watch later, watched state, subscriptions/server mirrors, and local-only library records remain for the active account profile.
- During logout, offer independent optional cleanup choices. Each choice deletes local data only:
  - Delete downloaded videos/subtitles: deletes shared device-local offline media, partial download files, shared offline display metadata, and shared offline playback progress. It does not delete account-scoped preferences, favorites, watch later, watched state, subscriptions/server mirrors, or credentials.
  - Reset preferences: DataStore-backed settings for the active account profile, such as subtitle, quality, speed, notification, downloads-on-Wi-Fi, and related cache timestamp preferences. This does not delete credentials, shared offline downloads/progress, cached catalog metadata, watch/library records, the profile key, or the profile salt.
  - Clear watch/library data: account-scoped favorites, watch later, watched state, subscriptions/server mirrors, account-scoped streamed/online playback progress, cached catalog/detail metadata, and local-only library records for the active account profile. This does not delete shared downloaded media, subtitles, or shared offline playback progress.
- Logout cleanup never calls Media delete/update endpoints and never mutates Media server-side library, progress, favorites, watch-later, watched-state, or subscription state.
- If the user logs back into the same account, the same local account profile is reused and server-backed library state may reappear after Kodi-compatible sync.
- If the user logs into a different account, a separate local account profile is created or reused. Shared offline downloads and shared offline playback progress are device-local and visible across accounts; preferences, favorites/watch later, watched state, subscriptions/server mirrors, account-scoped online playback progress, online cached metadata, and local-only library records are not shared across account profiles.
- Server-side library mutations happen only through explicit library actions while logged in and only when Kodi plugin parity proves the endpoint.
- Redact credentials, auth headers, media URLs, download URLs, subtitle URLs, and artwork URLs in logs/diagnostics.

Kodi-plugin request identity:
- Media HTTP requests must match the Kodi plugin as closely as practical: `https://front.media-mirror.tv/api/v2`, `Accept: application/json`, `Content-Type: application/json`, HTTP Basic auth, and `User-Agent: Kodi ({version})` unless live discovery proves the plugin sends a different value.
- The network shell owns Kodi request metadata. The functional core receives typed outcomes such as unauthorized, free-limit/payment-required, not found, unavailable, decode failure, and unexpected non-JSON/redirect responses; it must not depend on Android `WebView`, `CookieManager`, `HttpUrl`, or raw response objects.
- Do not derive the Media API `User-Agent` from Android WebView/Chrome. Do not use `okhttp/*`, `Dalvik/*`, `HeadlessChrome/*`, browser automation, test-runner, emulator-debug, or Android-browser identity for production Media API requests.
- Do not implement a WebView verification screen in v1. If the Kodi API request receives a redirect, HTML body, or other non-API response, map it to a typed recoverable network/service state, keep offline downloads usable, and rely on credentialed live drift tests to determine whether the Kodi-compatible request shape needs adjustment.
- Do not automate, script, bypass, or background-solve browser verification challenges. The app should behave like a Kodi-plugin-compatible API client, not like a browser.
- Do not persist cookies/tokens from web verification flows. Logout must clear credentials and any in-memory HTTP auth state.

Credential security:
- Store Media credentials with Android Keystore-backed encryption compatible with API 26.
- Exclude encrypted credential files and key material from Android Auto Backup and device-to-device transfer.
- Prefer direct Android Keystore plus authenticated encryption for credential material; use AndroidX Security Crypto only if its stable APIs remain appropriate and deprecated APIs are avoided or explicitly justified.
- Never persist Basic auth headers, stream URLs, download URLs, subtitle URLs, or artwork URLs in logs, diagnostics, screenshots, crash reports, or API fixtures.
- Keep credentials separate from general DataStore preferences.
- Keep encrypted credentials separate from account-scoped local profile data and shared offline data. Clearing credentials must not delete profile rows, profile preferences, shared offline download files, shared offline progress, or the profile-key derivation salt.
- Logout must clear encrypted credentials, cached auth validation state, and any in-memory OkHttp/Retrofit auth state.

Traffic shape:
- v1 network traffic is user-driven: first-login credential validation, app open/startup using the Kodi startup sequence, explicit refresh, opening details, play/download actions, and Kodi-verified library actions.
- Do not run periodic background catalog polling in v1.
- Use single-flight request de-duplication for identical in-flight catalog/detail/library requests.
- Keep concurrency low: at most one catalog/list request and a small bounded number of detail/media metadata requests at a time.
- Bound retries for transient network/server failures, respect `429` and `Retry-After` when present, and never retry in tight loops.
- Do not retry `401`, `402`, malformed IDs, decode failures, unexpected HTML/redirect responses, or non-Kodi endpoints.

Resume and Continue Watching:
- Persist account-scoped local progress for streamed/online playback and shared device-local progress for downloaded media: stable media id, type, position, duration, timestamp, completed state.
- Save progress periodically, on pause/background/stop, and before playback errors.
- Resume only when saved position is meaningful and unfinished: at least `30s` and less than `95%` of known duration.
- If position is below `30s`, start from the beginning. If position is at or beyond `95%` of known duration, treat the item as completed for resume purposes and start from the beginning unless the user explicitly seeks from history/player UI.
- If duration is unknown, use only the `30s` lower threshold and do not infer completion.
- Apply the resume threshold independently to streamed account-scoped progress and shared offline progress before choosing the later resume point.
- Logged-in resume/download UI may use the later resumable point from account-scoped progress and shared offline progress for downloaded content.
- When playback reaches at least `95%` of known duration, mark the item locally completed/watched for the relevant local scope: account-scoped progress for logged-in streamed/online playback, shared offline progress for logged-out or downloaded playback as applicable.
- If duration is unknown, mark completed only on an actual playback-ended event, not by percentage.
- Users may explicitly toggle watched/unwatched locally. Server watched/progress mutation remains gated by verified Kodi plugin behavior.
- Explicit watched/unwatched actions apply to the current context. Logged-in online/catalog/detail/library contexts update only active account-scoped watched/progress state. Downloaded/offline contexts update shared offline watched/progress state for that downloaded media.
- If a logged-in user acts from a downloaded/offline view, update shared offline watched/progress state and optionally label the result as local to this device.
- Do not automatically mirror explicit watched toggles between account-scoped and shared offline state.
- Shared offline progress is not copied into account-scoped progress on login.
- Account-scoped progress is updated only after the logged-in user plays, resumes, seeks, or completes that item.
- Server progress sync may happen only from account-scoped progress, only after explicit logged-in playback, and only when Kodi-compatible progress write support is proven.
- If shared offline progress is ahead of account-scoped progress, UI may show a subtle local indicator such as "Resume from this device".
- Clearing watch/library data clears account-scoped progress only. Deleting downloaded media clears shared offline progress for that media.
- Show "Continue Watching" on home and library. Logged-in views combine account-scoped progress with shared offline progress using the merge rule above; logged-out Offline Library uses shared offline progress only.
- Show progress bars on movie cards and episode rows.
- Playback can resume streamed or downloaded media.

Next Episode:
- Compute next episode locally from show season/episode metadata.
- Show next unwatched episode on series detail.
- After an episode reaches local completion, show a next-episode prompt with a short countdown and clear Play/Cancel actions.
- Do not autoplay next episode by default. Playback starts only after explicit user action.
- If the user cancels/dismisses the next-episode prompt, hide it for that completed episode in the current playback session. Do not show it again due to focus changes, player controls reopening, orientation changes, or process restoration for that same session.
- If the user replays the episode later and reaches completion again, the prompt may appear again. Do not add a global next-episode prompt disable setting in v1.
- For streaming/logged-in playback, the prompt may use cached/detail metadata to identify the next released episode. Fetch/play only after the user confirms.
- For offline/logged-out playback, show next episode only when the next episode is already downloaded and playable locally.
- Never show next-episode actions for unreleased/upcoming episodes.

Offline playback and Offline Home Mode:
- Downloaded videos remain playable without connectivity, with or without subtitle files.
- When offline, open/surface the downloaded library first instead of showing failed catalog screens.
- Non-downloaded content opened offline shows a clear unavailable state.
- Player uses local app-private media first when a valid download exists.
- Failed or partial video downloads are not playable.
- Missing subtitles never block playback.
- Offline playback must not require login revalidation while a valid local media file exists.
- If auth expires while offline, keep downloaded playback available and defer login repair until the user tries an online-only action.
- If logout retained downloaded videos/subtitles, the login/auth screen shows an "Offline Library" entry point when at least one playable shared offline download exists.
- Logged-out Offline Library never sends Media requests, validates credentials, refreshes metadata, mutates server state, or starts online-only work.
- Logged-out Offline Library allows only retained downloaded-title browsing, playback of downloaded videos/subtitles, local storage usage viewing, and deleting downloads.
- Logged-out Offline Library does not allow catalog, online search, network-refreshed details, favorites/watch-later/server library actions, new downloads, progress sync, notifications, account settings, or diagnostics that include auth/network state.
- Logged-out Offline Library is a single combined library across all shared offline downloads. Do not show account/profile labels or profile selection.
- Shared offline display metadata is kept indefinitely while the downloaded media exists. Logged-out Offline Library never refreshes metadata or sends network requests.
- Offline entries may show the last known title, poster/backdrop if cached, episode/season info, effective downloaded quality, subtitle languages, duration, and shared offline progress.
- If image cache entries are evicted, show placeholder artwork instead of attempting network refresh.
- Shared offline metadata updates happen only during logged-in Kodi-compatible catalog, detail, play, or download flows.
- Playback progress saved while logged out is shared device-local offline progress. It is not copied into an account on login and may sync to server later only through account-scoped progress after explicit logged-in playback and proven Kodi-compatible progress write support.
- If no playable downloads exist, do not show Offline Library from the login/auth screen.

Signed media URL handling:
- Raw stream, download, and subtitle URLs are ephemeral. They may exist only in memory or active Media3/download request state.
- Persisted download records store opaque local file path, Media content id, media type, size/status, and timestamps, not raw remote URLs. Video records store selected quality. Subtitle sidecar records store language plus source/format when Media/Kodi-compatible data exposes it.
- For streamed playback, if the active stream URL expires or fails, perform at most one bounded Kodi-compatible detail/media refresh for the same item, then retry playback once with the refreshed URL.
- If the refreshed streamed playback retry fails, show a recoverable playback error with Retry and Back to details actions.
- If streamed playback fails for a selected quality and lower compatible qualities are known, the app may perform one current-playback-only fallback to the nearest lower compatible quality and show a non-blocking notice. Do not persist that fallback as the user's quality preference, and do not cycle through every quality.
- If no lower compatible quality exists or the fallback also fails, show a recoverable playback error with Retry and Back to details actions.
- If streamed playback or same-item URL refresh returns `401` or an equivalent auth failure, stop online playback, save local progress, mark auth state invalid, and route the user to auth repair/login. Do not retry auth-sensitive playback requests in a loop.
- Playback URL refresh must use the same Kodi-compatible endpoint/request identity rules and must not introduce non-Kodi endpoints, web scraping, or browser/WebView behavior.
- Offline local-file playback must not perform network refresh. Missing or corrupt local files show a local-file error and offer Back to Downloads/Details.
- If auth fails while the user is playing a local downloaded file, do not interrupt playback; defer auth repair until the user takes an online-only action.
- Download URL refresh uses the download queue-specific bounded retry rules.

Download robustness:
- Support pause/resume/retry where Media3 and Media URL behavior allow it.
- Detect and clearly label queued, downloading, paused, failed, partial, complete, and unavailable download states.
- Clean up partial files when the user cancels a download or chooses storage cleanup.
- Verify downloaded file existence and non-zero length before marking a video playable.
- Handle low-storage conditions before queueing and while downloading; show required/available storage when known.
- Before queueing or resuming a download, require known/estimated remaining download size plus a safety reserve: `2 GB`, or `10%` of total device storage on small devices, whichever is smaller.
- If size is unknown, require the safety reserve before starting and continue checking during download.
- If the storage check fails, mark the queue item paused/unavailable with a clear low-storage reason before starting more transfer.
- Low-storage states show required space, available space, and the reserve requirement when known, plus a "Manage downloads" action.
- Do not delete completed downloads automatically to make room. Completed media deletion is always user-driven through storage/download management.
- Provide a setting for downloads on Wi-Fi only, default enabled for phone/tablet and configurable for TV.
- Treat metered-network downloads as user-confirmed actions.
- Use foreground download notifications where required.

Android services and notifications:
- Playback uses a dedicated Media3 playback service only while playback needs to continue in the background or on Android TV. The service declares `android:foregroundServiceType="mediaPlayback"` and `FOREGROUND_SERVICE_MEDIA_PLAYBACK` for Android 14+.
- Downloads prefer user-initiated transfer APIs where practical. If a foreground service is needed for active visible downloads, it declares `android:foregroundServiceType="dataSync"` and `FOREGROUND_SERVICE_DATA_SYNC` for Android 14+.
- Do not launch `mediaPlayback` or `dataSync` foreground services from `BOOT_COMPLETED`.
- Do not start WorkManager, JobScheduler, DownloadManager, or other quota-governed background jobs from a foreground service to bypass Android 16+ quotas.
- Define notification channels only for playback and active downloads.
- v1 creates only an active-download notification when required for the active visible download or foreground service.
- Do not create a queue summary notification or notifications for completed/failed queued items.
- The active-download notification shows the current item title, or a redacted generic label if needed, progress when known, paused/metered/low-storage state when relevant, and a tap target to the Downloads screen.
- Notification actions are minimal and safe: pause or cancel the active download when platform-appropriate. Queue-wide retry/delete actions stay inside the app.
- v1 excludes new-episode notifications from product UI and settings. Do not create a new-episode notification channel, request notification permission for new-episode notifications, or schedule new-episode notification work.
- Android 13+ requests `POST_NOTIFICATIONS` only when required for playback/download foreground notifications. Permission denial must not break foreground service correctness.
- On Android TV, treat playback/download notifications as platform-required operational UI, not as the primary control surface.

Subtitle preferences:
- Default subtitles: enabled.
- Default subtitle language: English.
- Persist enabled/language in account-scoped DataStore preferences.
- Provide Settings controls and in-player subtitle/language switcher.
- If subtitles are disabled, playback starts without subtitles even when files exist.
- If preferred language is unavailable, fall back to English, then first available subtitle, then no subtitles with a non-blocking notice.
- If a subtitle file, track, or subtitle download fails during playback, video playback continues.
- Subtitle playback failure shows a non-blocking subtitle error with explicit actions: retry the current subtitle, switch language when another downloaded or available subtitle exists, or turn subtitles off.
- Do not automatically cycle through every subtitle language or change subtitle language without user action.
- Subtitle retry uses the same Kodi-compatible URL rules and bounded retry policy as other media requests. During offline local playback, retry is limited to local sidecar files unless the user is logged in and explicitly requests adding or retrying a subtitle sidecar.
- Download flow downloads preferred subtitles by default and allows other available languages.
- Authenticated web inspection observed subtitle download path shapes `/en/movies/{movieSlug}/download_subtitle/{language}` and `/en/shows/{showSlug}/videos/{videoId}/download_subtitle/{language}`. API discovery must verify whether Kodi/API subtitle URLs use the same shape or return direct subtitle URLs.

Video quality:
- Default policy:
  - Phone/tablet: highest available quality up to `1080p`.
  - Android TV: highest available quality with no app-level cap.
- Persist quality preference in account-scoped DataStore preferences.
- Allow per-playback and per-download override when multiple qualities exist.
- Manual quality changes inside the player affect only the current playback session. They do not update the account-scoped quality preference; persistent default quality changes happen only from Settings.
- Persist selected download quality with each downloaded file.
- If preferred quality is unavailable, choose nearest lower quality, otherwise nearest higher.
- When Media exposes the observed web quality set, use labels `Auto`, `1080p`, `720p`, `480p`, `360p`, and `240p`.
- Discovery-gated behavior:
  - If Media exposes separate quality URLs, select matching URL.
  - If Media exposes adaptive or fragmented streams, constrain Media3 track selection and persist the effective selected quality.
  - If Media exposes only one URL, show quality as `Auto`/reported resolution and disable manual quality selection for that item.

Playback speed:
- Provide Media3 playback speed controls matching useful Media web behavior: `0.50x`, `0.60x`, `0.70x`, `0.80x`, `0.90x`, `1.00x`, `1.25x`, `1.50x`, `2.00x`.
- Changing speed inside the logged-in player updates the active account profile's playback speed preference immediately.
- Future streamed playback, downloaded playback, and trailer playback for that account starts with the last selected speed until the user changes it or resets speed to `1.0x`.
- Logged-out offline playback may use the last available local/default speed, but must not mutate account-scoped preferences while logged out.

## Library, Downloads, And Discovery-Gated Features
Local library:
- Account-scoped Room rows include preferences-related mirrors, favorites, watch later, watched state, subscriptions/server mirrors, online cached metadata, streamed/online playback progress, and local-only library records.
- Shared offline Room rows include downloaded media records, minimal offline display metadata, and shared offline playback progress.
- Favorites, watch later, watched/progress, and subscriptions may sync or mutate server-side state only when the Kodi plugin demonstrably reads or writes the same state.
- Server-side library sync must match the Kodi plugin endpoint, method, payload, headers, and triggering context. Anything not plugin-verified remains app-local.
- Downloaded items appear in an "Available Offline" section/filter.
- Continue Watching row is built from account-scoped progress plus shared offline progress where relevant.

Storage management:
- A central Downloads screen shows all offline media and queue state across movies, episodes, and confirmed season queues.
- Downloads are grouped into active, queued/paused, failed/unavailable, and completed sections.
- Download rows show title, media type/episode info, effective or target quality, subtitle languages/status, size/estimate when known, progress, status reason, and allowed actions.
- Actions are state-specific: pause, resume, cancel remaining, retry failed, delete completed media, and manage storage.
- Confirmed season queues expose per-episode status from the Downloads screen; users do not need to return to the season detail screen to understand or recover the queue.
- Downloads screen shows total app-private media storage, per-title size, selected quality, subtitle languages, and download status.
- User can delete individual downloads, all downloads completed according to shared offline progress, or all downloads.
- Storage management may sort/filter downloads by largest size, completed/watched status, and recently played/downloaded to help the user choose what to delete.
- Storage management must not auto-select downloads, auto-delete downloads, or imply deletion is required. Bulk deletes require explicit confirmation.
- Deleting downloads deletes shared offline playback progress for the deleted media. It does not delete account-scoped streamed/online progress unless the user explicitly clears watch/library data.
- v1 stores downloaded videos and subtitles only in app-private storage. Downloaded media is not additionally encrypted beyond Android app sandbox and device-storage protections.
- Shared offline storage keeps one video asset per content item. UI shows one offline entry per content item, not per quality.
- If a higher-quality completed asset already exists, lower-quality download requests for the same content reuse the existing asset or are skipped with an "already available in higher quality" state.
- If a lower-quality asset exists and the user requests a higher quality, the higher-quality download may replace the lower-quality asset only after the new file completes and verifies successfully. Do not delete the existing playable asset until replacement verification succeeds.
- If exact quality ordering cannot be determined from Media/API data, do not auto-replace; keep the existing playable asset and show a clear unavailable/ambiguous reason.
- Subtitles are shared sidecar assets by content item, language, and source/format when available.
- If a shared video asset already exists and a user requests more subtitle languages, download only the missing subtitle sidecars and attach them to the existing offline item. Do not duplicate or re-download the video for subtitle-only changes.
- Subtitle sidecars are shared across accounts and visible in the logged-out Offline Library.
- If subtitle identity is ambiguous, keep separate sidecars rather than overwriting an existing playable subtitle.
- Shared offline metadata records the effective downloaded quality.
- Deleting a downloaded media item deletes its shared offline display metadata when no other shared offline asset for that content remains.
- Downloaded media directories and filenames use opaque identifiers only. Path components may include Media content ID/video ID, subtitle language code, random UUID, and file extension.
- Downloaded media path components must not contain raw login/email values, account profile keys, movie/show/episode titles, slugs, search queries, subtitle filenames returned by Media, or human-readable account labels.
- UI display titles come from Room metadata, not filesystem names.
- Exclude downloaded videos, subtitles, partial download files, and download metadata from Android Auto Backup and device-to-device transfer.
- Do not expose downloaded media through public/shared storage, MediaStore, Storage Access Framework exports, or external player handoff in v1.
- Diagnostics and logs must not include absolute media file paths when those paths could reveal opaque content identifiers, title names, or subtitle filenames; use redacted IDs, counts, and byte sizes instead.
- Stronger media-at-rest protection, such as file-level encryption for downloaded videos/subtitles, requires a later explicit design decision.

Artwork loading:
- Load poster/backdrop artwork only from image URLs returned by Kodi-compatible Media API list/detail responses.
- Do not scrape web pages, infer image URLs, call unrelated image/search APIs, or bulk-prefetch the catalog for artwork.
- Artwork loading is demand-driven from visible UI surfaces, with memory/disk caching, low concurrency, single-flight de-duplication, bounded retry, and no retry storms.
- Cached artwork may be reused offline. Logged-out Offline Library must show placeholders when artwork is missing or evicted instead of refreshing artwork over the network.
- Logs and diagnostics must not expose raw artwork URLs.

Download queues:
- v1 supports explicit user-initiated downloads for a single movie/episode and explicit user-confirmed season downloads.
- Season download starts only after the user opens a season, selects "Download season", reviews episode count, preferred quality, subtitle language choices, known storage estimate where available, and metered-network warning when relevant, then confirms.
- After the user selects "Download season" and before confirmation, the app may fetch only the minimum Kodi-compatible metadata needed to estimate download sizes for that selected season. Do not run season-size preflight during passive browsing, startup refresh, or background work.
- Size preflight uses low concurrency, single-flight de-duplication, bounded retries, and `429`/`Retry-After`. It must not scrape pages or call non-Kodi endpoints.
- Every season download requires this explicit confirmation screen. Do not add "don't ask again", preference-based auto-confirm, or one-tap season queueing from season rows/cards in v1.
- The confirmation screen shows episode count, already-downloaded/skipped count when known, selected quality policy, subtitle language policy, exact/known sizes when available, partial estimate when only some sizes are known, "size unknown" for episodes without Kodi-compatible size data, metered-network warning when relevant, and clear unavailable reasons for episodes that cannot be queued.
- A confirmed season queue may run on metered network only when the user explicitly allows metered use for that specific queue.
- If a season queue was confirmed on unmetered network and the network later becomes metered, pause queued/active season items before starting more transfer and show a "paused on metered network" state. The user may explicitly allow metered resume for that existing queue; this does not change global preferences.
- For season downloads, selected quality is a per-episode policy, not a guarantee. Each episode tries the preferred quality first, then nearest lower quality, then nearest higher quality. If no compatible quality is available or quality ordering is ambiguous, mark that episode unavailable/skipped with a clear reason.
- When known before confirmation, summarize quality fallback impact, such as preferred quality, episode count using fallback quality, and unavailable episode count. Per-episode final quality is shown in queue/downloads UI and persisted as the effective downloaded quality.
- For season downloads, subtitle selection is a per-episode policy matching playback defaults: preferred language first, then English, then first available subtitle, then no subtitles.
- Missing subtitles or subtitle download failure must not fail a season video download. When known before confirmation, summarize subtitle fallback impact, such as preferred language, fallback-language counts, and no-subtitle count.
- Additional subtitle sidecars may be added later without re-downloading video.
- If an episode is already downloaded in equal or higher effective quality, skip/reuse it and show "already available" in confirmation and queue UI. Do not re-download it.
- If an episode exists only in lower quality and the user selected a higher quality, the queue may upgrade it using the safe replacement rule: download and verify the higher-quality file first, then replace the lower-quality asset. If quality ordering is ambiguous, keep the existing playable asset and mark the upgrade unavailable/ambiguous.
- If size preflight fails, show the failure/unavailable reason and let the user proceed with unknown sizes or cancel.
- v1 allows one active video download at a time across the app, including confirmed season queues. Other item/episode downloads remain queued.
- Subtitle sidecar downloads may run only as part of the active episode/item workflow and must not create parallel video-like request bursts.
- Metadata/detail refresh for the active item or selected season preflight remains low-concurrency and single-flight. Do not use adaptive download concurrency based on Wi-Fi, charging, or device class in v1.
- Confirmed season queues download the season's available episodes sequentially. They must use only Kodi-compatible list/detail/download URL flows, single-flight duplicate de-duplication, bounded retries, `429`/`Retry-After` handling, partial-file cleanup, low-storage refusal, and foreground download notification where required.
- If episode download URLs are missing, the app may fetch only the required Kodi-compatible episode/detail data for that confirmed season queue.
- Season queue items are independent. Completed episodes remain playable when other episodes fail.
- Season queue UI must show per-episode status: queued, downloading, paused, completed, skipped/already available, unavailable, or failed.
- After the bounded retry budget is exhausted, retrying failed season items is explicit and user-driven: retry one failed episode or retry failed items in that season queue.
- Do not silently keep retrying failed season items after the user leaves or in background automation. Partial/failed files are cleaned up unless Media3/Media URL behavior makes safe resume possible.
- Canceling a season queue means "cancel remaining downloads": stop queued and active items for that queue, clean up partial files for canceled/failed active items unless safe resume is possible, and keep completed episodes playable.
- Season queue cancellation does not delete completed episodes, shared offline progress, metadata, or subtitle sidecars. Deletion remains a storage/download management action.
- Confirmed season queues are persisted across process death/app restart. Restart resume is limited to incomplete items that were already part of the confirmed queue.
- Restart resume must not discover or add new episodes, expand the season, refresh unrelated metadata, or create new queues.
- Before resuming persisted queue items, recheck connectivity, storage, metered-network policy, credential/auth state as encountered by normal Kodi-compatible requests, and URL freshness.
- Expired URLs for persisted queue items refresh only through the relevant Kodi-compatible detail/download flow for the already-confirmed item.
- Failed persisted items whose bounded retry budget was exhausted remain failed until the user explicitly retries.
- Logout pauses incomplete confirmed queues and keeps queue state. Completed downloads remain available in the shared logged-out Offline Library.
- While logged out, queued downloads must not resume, refresh URLs, validate credentials, discover queue metadata, or send Media requests.
- After login, incomplete queue items may resume only if the persisted queue was previously confirmed and the authenticated session can access the same content through Kodi-compatible flows. If auth/content access fails, items remain paused/failed with a clear reason until the user retries or cancels remaining downloads.
- Do not implement automatic next-episode downloads, background discovery of new episodes, periodic queue workers, unconfirmed download-all actions, or smart-download behavior in v1.
- Queue must respect app-private storage availability, selected quality, preferred subtitle language, and connectivity.
- If only streaming URLs exist or downloads are subscription-gated, show unavailable reason instead of queueing.

Recently added/new episodes, discovery-gated:
- Show "Recently Added" only when Kodi-compatible Media data exposes an actual added/updated timestamp suitable for that meaning.
- If only movie release date is available, label the movie row "Recent by release date".
- If only episode air date is available, label the episode row "Recent by air date".
- If only `newest_video` is available for shows, label the show-level row "Shows with recent episodes".
- If none of those signals exist, hide the row.
- Sorting and labels must be backed by fields present in Kodi-compatible API responses or sanitized fixtures.
- Do not use web scraping or non-Kodi endpoints to improve recency precision.

New-episode notifications:
- v1 does not implement new-episode notifications.
- Discovery may record whether Media exposes reliable new-episode metadata, but implementation must not surface notifications in v1.
- Adding new-episode notifications later requires a separate explicit design decision.

Privacy and distribution:
- v1 is for personal/internal sideload distribution.
- Manual release artifacts are debug-signed APKs intended for internal sideloading. Release docs must cover APK naming, install/update, rollback, and changelog expectations.
- Signed release APKs with a dedicated release key are deferred until a later explicit release-signing decision.
- Releases use `release-please` for the whole repository as one product version.
- `version.txt` is the source for Android `versionName`; Android `versionCode` is deterministically derived from SemVer.
- Release tags use `vX.Y.Z`; release notes are written to `CHANGELOG.md`.
- Release artifacts are named `subsloth-vX.Y.Z-debug-<shortsha>.apk`.
- v1 has no in-app update checking, update downloading, install prompting, GitHub Releases API calls, or calls to any non-Media service for update checks.
- Settings/diagnostics may show display-only build metadata: installed app version, build type, version code, Git SHA if available, and release channel label such as `debug-sideload`.
- Release discovery remains manual through GitHub Releases outside the app.
- Do not include analytics SDKs, crash-reporting SDKs, remote telemetry, or external diagnostics upload in v1.
- Diagnostics are local-only, view-only, and redacted.
- v1 diagnostics must not provide export, share, upload, copy-to-clipboard, attachment, bug-report bundle, HAR, screenshot, trace, or raw artifact creation from inside the app.
- Do not share credentials, media URLs, downloaded media, file paths, or account data outside app-private storage.

Diagnostics:
- Settings includes a view-only diagnostics screen with app version, device/API level, API base URL, auth state category, cache age, last refresh time, download queue counts, storage usage, last status category, last successful refresh age, and `Kodi-compatible request mode: enabled`.
- Last status category is one of the typed network/service categories, such as unauthorized, payment/free-limit, not found, network unavailable, server failure, decode failure, or unexpected non-JSON/redirect.
- Diagnostics must never reveal credentials, auth headers, media URLs, download URLs, absolute media file paths, raw account login/email, profile keys, cookies, endpoint paths, query params, request headers, header values, auth scheme details, User-Agent value, request/response bodies, redirect targets, or raw request/response headers.
- Detailed request-shape verification belongs in tests and sanitized discovery docs, not in the app UI.
- Export/share/copy support for diagnostics requires a later explicit design decision and sanitizer tests first.

## API Contract
Base API discovered from the Kodi add-on:
- Base URL: `https://front.media-mirror.tv/api/v2`.
- Auth: HTTP Basic auth.
- Endpoints:
  - `GET /movies`
  - `GET /movies/{movieId}`
  - `GET /shows`
  - `GET /shows/{showId}`
  - `GET /episodes/{episodeId}`

Use `api/subsloth.openapi.yaml` as the contract source. It reflects Kodi Python source inspection, with authenticated browser discovery retained only as supplemental field evidence. It is still an initial discovery contract and may require tweaks once implementation starts and real generated DTOs, fixtures, and live drift tests expose exact Media response shapes. Generate network DTOs from it with OpenAPI Generator, keep generated code inside `:core:network`, and map DTOs into stable domain models manually. If generator output is too noisy for the discovered Media schema, use handwritten DTOs only after documenting the reason and keeping OpenAPI schema validation as the compatibility gate.

The OpenAPI contract must model:
- Show metadata: poster, backdrop, description, rating, year, genres, countries, status, duration, IDs, newest video, popularity.
- Season/episode metadata: season, number, title, plot, air date, resolution, available qualities if returned, stream/download URLs where returned, subtitles and subtitle languages.
- Movie metadata: poster, backdrop if available, description, rating, year, genres, countries, duration, trailer URL if available, IDs, resolution/qualities, stream/download URLs, subtitles.
- Do not include comments endpoints except to document they are intentionally unsupported.

API drift tests:
- Offline contract tests validate recorded JSON fixtures against OpenAPI schemas.
- Mapper tests assert required domain fields and tolerant parsing.
- Optional live tests run only from a local developer environment when `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD` are present.
- Live tests check auth, Kodi-compatible request headers/metadata, endpoint availability, top-level shape, required fields, quality/resolution fields when present, download URL presence when expected, and status-code behavior.
- Tests must not fetch comments for movies or TV series.
- Tests must assert that web-only frontend comments resources such as `/api/frontend/comments` are outside the native data source contract.

## Android Implementation
Use current Android best practices:
- Single-activity Compose app.
- Gradle Kotlin DSL, version catalogs, convention plugins.
- Latest stable recommended dependencies by default.
- Dependency policy: stable releases only; no alpha/beta/RC dependencies unless a documented device-blocking bug or platform requirement requires it.
- Approved exception: `androidx.tv:tv-foundation:1.0.0-rc01` may be used because first-class Android TV support is locked and the TV foundation artifact has no stable release yet. Replace it with the first stable TV foundation release once available.
- Dependency update automation may open PRs, but major upgrades and Android toolchain upgrades require manual review and test evidence.
- Enable Gradle dependency verification metadata and dependency locking once the Gradle project exists.
- Add secret scanning/pre-commit or CI checks so credentials, Basic auth headers, signed media URLs, browser logs, screenshots, and Playwright artifacts are not committed.
- UDF with immutable UI state.
- Hilt DI.
- Room for account-scoped cache/library/progress metadata plus shared offline download/progress metadata.
- DataStore for account-scoped settings including subtitle, quality, notification, and playback speed preferences.
- Encrypted credential storage.
- Coil for images.
- Media3 for playback/downloads.
- WorkManager for reliable startup/background refresh where platform-appropriate; no new-episode notification workers in v1.
- Retrofit/OkHttp preferred for OpenAPI-driven API client.
- No AndroidX WebKit dependency is required for Media API request identity in v1.
- Arrow Core for typed errors, `Option`, `Either`, `NonEmptyList`, and validation.
- Arrow Retrofit integration for mapping HTTP responses into typed Arrow results at the network boundary.
- Arrow Resilience for retry/circuit-breaker policies around retryable network and refresh work.
- Arrow Optics for immutable nested domain/UI state updates where useful.
- Arrow Detekt rules and Kotest Arrow matchers/property helpers for enforcing and testing the FP style.
- Edge-to-edge UI with safe handling for system bars, display cutouts, and TV overscan-safe spacing.
- Predictive back support where available, with deterministic back behavior on phone/tablet and remote Back on TV.
- State restoration after process death for login state, navigation destination, selected tabs, search query, player state, and download queue state.
- Android 16 phone behavior must support resizable/adaptive layouts without relying on fixed orientation or aspect ratio assumptions.

UI:
- Phone: bottom navigation, single-pane flows.
- Tablet: adaptive list-detail panes.
- TV: Compose for TV components, D-pad focus, large cards, browse rows, detail-first playback actions.
- TV Downloads uses the same central Downloads model, adapted for remote control: large rows/cards, active/queued/failed/completed sections, deterministic D-pad focus order, focus restoration after returning from detail/player/dialogs, and overscan-safe spacing.
- TV Downloads actions are simple and confirmation-based where destructive: resume, retry failed, cancel remaining, delete completed media, and manage storage.
- TV storage deletion supports only "Delete this download", "Delete watched completed", and "Delete all downloads". Each destructive action requires a confirmation dialog that states what will be deleted and that shared offline progress for deleted media will also be removed.
- TV sorting/filtering may help inspect storage, but deletion actions remain simple and explicit.
- TV Downloads excludes long menus, multi-select, drag/reorder, and dense table layouts in v1.
- Home includes Continue Watching, Available Offline when relevant, Movies, Shows, and discovery-gated recency rows with labels that match the available Kodi-compatible data.
- Catalog filtering should include type, genre, country, subtitle language, watched/unwatched, downloaded/offline availability, year range, and rating range when the API exposes enough data. Movie sorting should support publication date, popularity, rating, name, and year where available.
- No comments UI or comment actions anywhere.

Accessibility and input:
- Every interactive element has a meaningful content description or text label.
- TV focus order is deterministic and focus is restored when returning from detail/player screens.
- Focus indicators are high contrast and visible on Android TV 8.
- Support remote media keys where practical: play/pause, back, directional navigation, select, fast-forward, and rewind.
- Apply Android `FLAG_SECURE` only while credential-sensitive screens are visible: login, auth repair, diagnostics, and logout cleanup confirmation.
- Do not apply `FLAG_SECURE` globally to catalog, details, library, settings, or playback in v1.
- Tests/docs screenshots for credential-sensitive screens must use fake credentials and redacted state. Roborazzi and device screenshots for non-sensitive screens remain allowed.
- If a later release adds diagnostics export/share or more sensitive account-management screens, revisit the `FLAG_SECURE` scope.
- Support large text without clipping essential actions on phone/tablet.
- Maintain accessible contrast for metadata, subtitles controls, progress bars, disabled states, and focus states.
- Player supports caption styling provided by Media3/system caption settings where available.
- Touch targets on phone/tablet meet Android accessibility sizing guidance.

Startup refresh:
- On app open, refresh stale catalog data using the Kodi-compatible startup sequence only when connectivity is available and credentials exist.
- Catalog list caches become stale after 24 hours. Detail caches become stale after 7 days.
- Playback, download, and subtitle URLs are never cached.
- Manual refresh is always available for online catalog/detail screens.
- Kodi-verified server library state may refresh only as part of user-driven startup/explicit-refresh/detail/library actions; do not poll it periodically.
- Show cached/offline data first.
- Connectivity failures are non-fatal if offline playable content exists.
- Startup refresh must not fetch comments.

CI policy:
- Required CI on pull requests and pushes to `main` is deterministic and offline-only.
- Required CI uses JDK 17 and the Gradle wrapper.
- Required CI runs Gradle wrapper validation, dependency verification/locking checks, OpenAPI validation/generation, `check`, `lintDebug`, `testDebugUnitTest`, `assembleDebug`, secret/artifact scanning, no-comments invariant checks, and Kodi-parity invariant tests.
- Required CI must not use Media credentials, contact Media, run live drift tests, upload browser traces/HAR/screenshots, or publish signed URLs/credentials in artifacts.
- GitHub Actions must not store Media credentials in v1. Live Media drift tests are local-only and are never required for ordinary pull request merge.
- A protected manual GitHub live-drift workflow may be added only after a later explicit design decision.
- `release-please` runs on pushes to `main` using a dedicated `RELEASE_PLEASE_TOKEN` PAT or GitHub App token.
- Release Please uses `release-type: simple` and maintains `version.txt` plus `CHANGELOG.md`.
- All Conventional Commit release-worthy changes can produce repository releases, including docs/spec changes. Before the app scaffold exists, releases may be changelog-only.
- When Release Please creates a GitHub Release and the app scaffold exists, the same workflow builds `assembleDebug` and uploads the debug-signed sideload APK.
- Required PR CI builds `assembleDebug` only. Dedicated release signing is deferred until a later explicit decision.

## Test Plan
Core/domain:
- Search/filter/sort, season grouping, next episode, subtitle preference/fallback/disabled behavior, quality selection/fallback, playback speed preference, resume decisions, offline playback decisions, storage cleanup decisions, and explicit item/season download queue decisions.

Network/API:
- OpenAPI schema validation against fixtures.
- OpenAPI generation/validation task must run in CI so API contract drift is caught before mapper/domain tests.
- Mapper tests for movie/show detail, seasons, episodes, subtitles, qualities/resolutions, downloads.
- Optional live drift tests are local-only and gated by local environment credentials.
- Verify no code path calls comments endpoints.
- Verify production Media requests use the Kodi-style `User-Agent` and request headers from the Kodi plugin, never Android WebView/browser, OkHttp/Dalvik, headless/automation, test, or emulator/debug identity.
- Verify HTML/redirect/non-JSON responses from the Kodi API surface map to typed recoverable network/service errors instead of DTO parsing, web scraping, retry loops, or blocking offline playback.
- Verify request cadence: no periodic background polling, duplicate in-flight requests are coalesced, retries are bounded, and `429`/`Retry-After` are respected.
- Verify remote filters/sorts and server-side library writes are sent only when Kodi plugin behavior proves the exact request shape.

Android shell:
- Room DAO tests for account-scoped progress/library persistence and shared offline download/progress persistence.
- Encrypted credential storage tests.
- Backup exclusion tests or manifest/resource checks for encrypted credential storage.
- Manifest/lint tests for foreground-service types and permissions: playback uses `mediaPlayback`, active downloads use `dataSync` only when needed, and neither launches from `BOOT_COMPLETED`.
- DataStore tests for account-scoped subtitle, quality, notification, and playback speed preferences.
- Logout tests verifying credentials always clear, all non-transient local data remains by default, deleting downloaded videos/subtitles clears shared offline media/progress only, resetting preferences clears only active-profile preferences, clearing watch/library data clears only active-profile watch/library data, other account profiles remain untouched except for intentionally shared offline media/progress deletion, and no logout path calls Media server mutation endpoints.
- Login tests verifying new credentials run the Kodi startup validation sequence, stored credentials are not proactively revalidated on later startup, and auth failure during normal requests routes to login repair.
- ViewModel UDF tests.
- Media progress save/restore tests.
- Offline playback tests, including no-subtitle playback.
- Network-loss-during-playback and network-loss-during-download tests.
- Low-storage and metered-network download behavior tests.
- Process-death state restoration tests for navigation, search, selected season, playback progress, and download queue.
- Quality-selection and playback-speed tests.
- Storage management tests.
- Explicit item and season download queue tests with fake availability.
- Compose UI tests for login/logout, detail screens without comments, offline library, Continue Watching, subtitle/quality/speed settings, storage management.
- TV D-pad focus tests for login/logout, detail, library, Downloads screen sections/actions, offline playback, Continue Watching, and next episode.
- Accessibility checks for labels, focus visibility, contrast-critical states, large text, and remote-only operation.
- Roborazzi screenshot tests for movie/series detail across phone/tablet/TV, plus device screenshots only where real TV focus/rendering matters.
- Macrobenchmark + Baseline Profile tests for startup, catalog scroll, detail open, playback start.

## Device Acceptance Matrix
- Android TV 8: login/logout, encrypted credentials, D-pad browse, detail screens without comments, highest available quality default, playback/resume, next episode, subtitles default enabled/English, playback without subtitles, offline home mode, offline saved playback, central Downloads screen with remote-friendly queue/status/actions, storage management.
- Android tablet 13: adaptive detail screens without comments, default quality capped at 1080p, notification permission for playback/download foreground notifications when required, playback/resume, subtitle/quality/speed persistence, offline playback, storage management.
- Android phone 16: adaptive/resizable behavior, detail screens without comments, default quality capped at 1080p, playback/resume, subtitle/quality/speed persistence, offline playback, Continue Watching, startup refresh.

## Assumptions
- Sideload/internal distribution.
- Credentials are supplied in-app and via env vars only for optional live tests.
- Favorites, watch later, watched state, subscriptions, and account-scoped online playback progress are local unless the Kodi plugin demonstrably reads/writes compatible server-side state. Shared offline playback progress is not server-synced directly; it may influence logged-in local resume UI, then account-scoped progress may sync only after explicit logged-in playback and proven Kodi-compatible progress write support.
- Offline playback is limited to successfully downloaded app-private video files; subtitles are optional.
- Quality selection, confirmed season download queues, and precise recently-added/new-episode rows depend on verified Media API support.
- Comments are intentionally out of scope for movies and TV series even if Media exposes them.
- No analytics, crash-reporting SDK, telemetry upload, public distribution, or remote diagnostics upload in v1.
- No Chromecast, external player handoff, public-folder downloads, Kodi NFO export, Play Store billing, intro/recap skip, or user-visible multi-profile switcher in v1. Account-scoped local storage is required for preferences and online/library state; downloaded media and offline playback progress are shared device-local data.
