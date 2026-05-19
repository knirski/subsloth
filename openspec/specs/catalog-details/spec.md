# catalog-details Specification

## Purpose
Purpose is documented in the archived proposal.md and design.md from the catalog-details change. This section will be updated to consolidate that information.
## Requirements
### Requirement: Home Rows
The home screen SHALL show Continue Watching, Available Offline when relevant, Movies, Shows, and discovery-gated recency rows.

#### Scenario: Offline content exists
- **WHEN** playable shared offline downloads exist
- **THEN** the home screen includes Available Offline using shared offline metadata and progress

#### Scenario: Recency signal is absent
- **WHEN** Kodi-compatible data lacks added, updated, release, air-date, or `newest_video` recency signals
- **THEN** the app hides recency rows instead of inventing a label

### Requirement: Honest Recency Labels
Recency rows SHALL use labels that match the precise data source available from Kodi-compatible API responses or sanitized fixtures.

#### Scenario: Added timestamp exists
- **WHEN** actual added or updated timestamp data is available
- **THEN** the row may be labeled "Recently Added"

#### Scenario: Only release date exists
- **WHEN** movie release date is the only movie recency signal
- **THEN** the row is labeled "Recent by release date"

#### Scenario: Only episode air date exists
- **WHEN** episode air date is the only episode recency signal
- **THEN** the row is labeled "Recent by air date"

#### Scenario: Only newest video exists
- **WHEN** only show-level `newest_video` exists
- **THEN** the row is labeled "Shows with recent episodes"

### Requirement: Search and Filters
Search SHALL support type, genre, country, subtitle language, watched/unwatched, downloaded/offline availability, year range, and rating range when the backing data exposes those fields.

#### Scenario: Filter field is unsupported
- **WHEN** Kodi-compatible data does not expose a filter field
- **THEN** the UI hides or disables that filter instead of calling unverified endpoints

#### Scenario: Movie sorting fields exist
- **WHEN** Kodi-compatible movie data exposes publication date, popularity, rating, name, or year sorting fields
- **THEN** movie sorting supports those options using verified API fields or local cached data

### Requirement: Startup Refresh and Cache Staleness
Online catalog and detail screens SHALL show cached or offline data first, refresh stale data only through Kodi-compatible requests, and keep connectivity failures non-fatal when offline playable content exists.

#### Scenario: Catalog cache is stale
- **WHEN** catalog list cache data is older than `24 hours`, connectivity is available, and credentials exist
- **THEN** app startup refreshes catalog data using the Kodi-compatible startup sequence without fetching comments

#### Scenario: Detail cache is stale
- **WHEN** detail cache data is older than `7 days`, connectivity is available, and credentials exist
- **THEN** opening or refreshing the detail screen may refresh detail data through Kodi-compatible endpoints

#### Scenario: Ephemeral media URL would be cached
- **WHEN** playback, download, or subtitle URL data is returned by an API response
- **THEN** the app does not cache those URLs as catalog or detail data

#### Scenario: Manual refresh is requested
- **WHEN** the user requests manual refresh on an online catalog or detail screen
- **THEN** the app refreshes that screen through Kodi-compatible requests when connectivity and credentials allow it

#### Scenario: Connectivity fails with playable downloads
- **WHEN** startup or screen refresh fails because connectivity is unavailable and offline playable content exists
- **THEN** the app keeps cached/offline content usable instead of blocking the user with a fatal catalog error

### Requirement: Artwork Source
Artwork SHALL load only from image URLs returned by Kodi-compatible Media API list/detail responses or retained offline cache metadata.

#### Scenario: Offline artwork cache is evicted
- **WHEN** logged-out Offline Library lacks cached artwork
- **THEN** the app shows fallback artwork and does not perform a network refresh

#### Scenario: Multiple posters become visible
- **WHEN** visible cards request artwork
- **THEN** the loader uses low concurrency, single-flight de-duplication of identical URLs, and bounded retry, and does not perform catalog-wide bulk prefetch

#### Scenario: Artwork URL is missing
- **WHEN** a Kodi-compatible response lacks an image URL
- **THEN** the app does not infer URLs and does not call unrelated image or search APIs

### Requirement: Movie Detail Content
Movie detail SHALL include poster, title, plot, subtitle language availability, watch/resume, download, trailer when available, rating, release year, genres, countries, duration, quality/resolution where available, user/library state, progress, and downloaded availability.

#### Scenario: Movie has no trailer URL
- **WHEN** Kodi-compatible data lacks a trailer URL
- **THEN** the movie detail screen hides the trailer action

#### Scenario: Movie detail exposes user actions
- **WHEN** a movie detail screen is shown to a logged-in user
- **THEN** favorite, watch-later, and watched/progress actions are exposed when the API exposes them

#### Scenario: Movie has resume progress
- **WHEN** watched progress exists for a movie
- **THEN** the primary action label switches between Play and Resume based on resume position

### Requirement: Series Detail Content
Series detail SHALL include poster/backdrop where available, title, plot, rating, release year, genres, countries, status, duration or episode length, TMDb/IMDb IDs where available, latest/newest metadata where available, quality/resolution where available, user/library state, progress, downloaded availability, seasons, and episode rows.

#### Scenario: Series detail renders episodes
- **WHEN** a show detail response includes seasons and episodes
- **THEN** episodes are grouped by season, sorted by episode number, and shown under selectable season tabs or sections

#### Scenario: Series detail exposes user actions
- **WHEN** a series detail screen is shown to a logged-in user
- **THEN** favorite, watch-later or subscription, and watched/progress actions are exposed when the API exposes them

#### Scenario: Series has next episode
- **WHEN** partial progress exists on the latest watched episode
- **THEN** the series action exposes the explicit next episode to play with its season/episode markers

### Requirement: Episode Row Content
Episode rows SHALL show episode number, title, plot toggle, air date or added/updated age, quality/resolution where available, watched state, resume progress, subtitle languages, download action, and next-episode relationship where applicable.

#### Scenario: Episode is upcoming
- **WHEN** an episode has future premiere metadata and is unreleased
- **THEN** the row shows upcoming information and no play or download action

### Requirement: Comments Are Absent
Catalog, search, movie detail, series detail, episode rows, and metadata models SHALL NOT support comments.

#### Scenario: Detail screen renders
- **WHEN** a movie or series detail screen is displayed
- **THEN** there is no comments section, comments count, comments sort, comments action, or comments-derived metadata

