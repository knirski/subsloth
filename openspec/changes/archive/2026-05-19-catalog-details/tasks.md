## 1. Catalog and Search

- [x] 1.1 Add HomeViewModel and SearchViewModel tests for cached data first, offline rows, filters, honest recency labels, no-comments behavior, and process-death state restoration covering selected home tab and active search query.
- [x] 1.2 Implement home rows for Continue Watching, Available Offline, Movies, Shows, and supported recency rows.
- [x] 1.3 Implement search and filters backed by available Kodi-compatible fields, including movie sort options where data exposes them.
- [x] 1.4 Implement demand-driven artwork loading from returned API URLs and offline cache metadata only.
- [x] 1.5 Implement startup refresh with `24 hours` catalog staleness, `7 days` detail staleness, manual refresh, ephemeral URL non-caching, cached/offline-first display, and non-fatal connectivity failures when playable downloads exist.

## 2. Details

- [x] 2.1 Add movie and series detail ViewModel tests for required metadata, user/library state (favorite, watch-later, subscription where applicable), progress, play/resume label switching, downloads, upcoming/unreleased episodes, season grouping, episode ordering, next-episode action, and absence of comments.
- [x] 2.2 Implement movie detail screen and state.
- [x] 2.3 Implement series detail screen, season selector, and episode rows.
- [x] 2.4 Implement unreleased/upcoming episode unavailable states.

## 3. Adaptive Catalog/Detail Layouts

- [x] 3.1 Apply phone single-pane layouts using `android-ui-foundation` primitives.
- [x] 3.2 Apply tablet adaptive list-detail layouts using `android-ui-foundation` primitives.
- [x] 3.3 Apply TV rows, large cards, action rail, season selector, and focused episode list using `android-ui-foundation` primitives.

## 4. Verification

- [x] 4.1 Run `./gradlew :feature:catalog:test :feature:details:test :app:assembleDebug`.
- [ ] 4.2 Run Compose smoke tests for catalog and details using the focus harness from `android-ui-foundation`. _(deferred to verification-release)_
- [x] 4.3 Run `openspec validate catalog-details --strict`.
- [ ] 4.4 Add Roborazzi screenshot tests for movie and series detail at phone, tablet, and TV dimensions that assert no comments UI is rendered. _(deferred to verification-release)_
