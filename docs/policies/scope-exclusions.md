# v1 Scope Exclusions

This document lists features, capabilities, and integrations that are explicitly **out of scope** for the v1 release of the SubSloth Android app. These may be reconsidered for future versions, but in v1 they must not be implemented, stubbed, or depended upon.

## Chromecast / Casting

- Chromecast (Google Cast) support is excluded from v1.
- Cast button, cast SDK integration, and `MediaRouteActionProvider` must not be added.
- Remote playback session negotiation is deferred.

## External Player Handoff

- Handoff to external media players (e.g. VLC, MX Player) is excluded from v1.
- `Intent`-based playback routing to third-party apps must not be implemented.

## Public-Folder Downloads

- Downloading media to user-visible public folders (e.g. `Downloads/`, `Movies/`) is excluded from v1.
- All downloaded media must remain in app-private storage or the app's dedicated external-files directory.

## Kodi NFO Export

- Generating or exporting Kodi-compatible `.nfo` metadata files is excluded from v1.
- The app must not write NFO files alongside media files or in any user-facing directory.

## Play Store Billing

- Google Play Billing (in-app purchases, subscriptions) is excluded from v1.
- Subscription management, product listings, purchase flows, and receipt verification must not be integrated.

## Intro / Recap Skip

- Automatic or manual skip of intro sequences and recaps is excluded from v1.
- No player integration with intro-detection services or manual skip UI.

## User-Visible Multi-Profile Switching

- UI for switching between Media account profiles is excluded from v1.
- Only the primary profile is used. Profile selection UI, switcher, or multi-account management must not be implemented.
- Account-scoped data isolation is implemented internally for correctness but must not expose a profile-switching surface to the user.

## Comments

- Fetching, showing, posting, counting, sorting, modeling, or depending on movie or TV-series comments is excluded from v1.
- The `/api/frontend/comments` resource must not be called, modeled, or present in any API contract.
- This is a hard exclusion — no "future support" infrastructure for comments may be added.

## Server Mutation (Favorites, Watch Later, Watched, Subscriptions)

- Server-side library mutations (favorites, watch later, watched/progress, subscriptions) are disabled in v1 unless Kodi plugin parity proves the exact endpoint, method, payload, headers, and triggering context.
- Library state changes are recorded only as local account-scoped or shared offline state.

## Live Media Tests in CI

- Live credential-gated drift tests must not run in GitHub Actions or any CI system.
- They are local-only, executed manually with `SUBSLOTH_LOGIN` and `SUBSLOTH_PASSWORD`.
- CI must not store, access, or be exposed to Media credentials.

## WebView / Browser Identity

- No WebView, Chrome Custom Tabs, headless browser, or automation identity may be used for API requests.
- OkHttp/Dalvik, emulator-debug, or Android-browser `User-Agent` values must not be sent to Media API endpoints.
- All API requests must use the Kodi-compatible identity defined in the OpenAPI contract.

## Release Signing & Public Distribution

- Dedicated release signing configuration and public Play Store / APK distribution are deferred.
- v1 debugging APKs are distributed manually via GitHub Releases (see `docs/release.md`).