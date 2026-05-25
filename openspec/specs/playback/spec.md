# playback Specification

## Purpose
TBD - created by archiving change playback. Update Purpose after archive.
## Requirements
### Requirement: Resume Thresholds
Playback resume SHALL use a `30s` lower threshold and a `95%` completion threshold when duration is known.

#### Scenario: Position is below lower threshold
- **WHEN** saved position is below `30s`
- **THEN** playback starts from the beginning

#### Scenario: Position is at completion threshold
- **WHEN** saved position is at or beyond `95%` of known duration
- **THEN** the item is treated as completed for resume purposes and starts from the beginning unless the user explicitly seeks from history or player UI

#### Scenario: Duration is unknown
- **WHEN** duration is unknown
- **THEN** only the `30s` lower threshold applies and completion is not inferred by percentage

### Requirement: Progress Scope Merge
Logged-in views SHALL apply resume thresholds independently to account-scoped streamed progress and shared offline progress before choosing the later resumable point for downloaded content.

#### Scenario: Shared offline progress is ahead
- **WHEN** shared offline progress is ahead of account-scoped progress and both are resumable
- **THEN** the UI may offer "Resume from this device" while account-scoped progress remains unchanged until explicit logged-in playback

### Requirement: Completion and Watched State
Playback completion SHALL update only the local scope relevant to the current context.

#### Scenario: Logged-in streamed playback completes
- **WHEN** logged-in online playback reaches at least `95%` of known duration
- **THEN** account-scoped progress/watched state is marked completed locally

#### Scenario: Downloaded playback completes
- **WHEN** downloaded or logged-out offline playback completes
- **THEN** shared offline progress/watched state is marked completed locally

### Requirement: Next Episode Prompt
The app SHALL compute next episodes locally from season/episode metadata and SHALL NOT autoplay next episodes by default.

#### Scenario: Episode completes
- **WHEN** an episode reaches local completion and a released next episode is known
- **THEN** the player shows a prompt with a short countdown and explicit Play and Cancel actions

#### Scenario: Prompt is dismissed
- **WHEN** the user cancels or dismisses the next-episode prompt
- **THEN** it stays hidden for that completed episode in the current playback session across focus changes, controls reopening, orientation changes, and process restoration

#### Scenario: Episode is replayed later
- **WHEN** the user replays the completed episode later and reaches completion again
- **THEN** the next-episode prompt may appear again and v1 does not expose a global next-episode prompt disable setting

#### Scenario: Offline next episode is unavailable
- **WHEN** the user is logged out or offline and the next episode is not already downloaded and playable locally
- **THEN** no next-episode play action is shown

### Requirement: Subtitle Behavior
Subtitles SHALL default to enabled English, fall back predictably, and never block video playback.

#### Scenario: Preferred subtitle is unavailable
- **WHEN** subtitles are enabled and the preferred language is unavailable
- **THEN** playback falls back to English, then first available subtitle, then no subtitles with a non-blocking notice

#### Scenario: Subtitle fails during playback
- **WHEN** a subtitle file, track, or subtitle download fails
- **THEN** video continues and the app offers explicit actions to retry the current subtitle, switch language when available, or turn subtitles off

#### Scenario: Offline subtitle retry is requested
- **WHEN** playback is using a local downloaded file while offline or logged out
- **THEN** subtitle retry is limited to local sidecar files unless the user is logged in and explicitly requests adding or retrying a subtitle sidecar

#### Scenario: System caption styling is available
- **WHEN** Media3 or platform caption styling is available
- **THEN** the player honors supported system caption styling for subtitle presentation

#### Scenario: A second subtitle failure occurs
- **WHEN** a subtitle fails again after an initial fallback
- **THEN** the app does not automatically cycle through additional languages and instead exposes explicit retry/switch/off actions to the user

### Requirement: Quality Behavior
The app SHALL select default quality by device class and SHALL scope manual in-player quality changes to the current playback session.

#### Scenario: Quality labels are shown
- **WHEN** Media exposes the observed quality set
- **THEN** the app uses labels `Auto`, `1080p`, `720p`, `480p`, `360p`, and `240p`

#### Scenario: Phone or tablet starts playback
- **WHEN** phone or tablet playback starts with multiple qualities
- **THEN** the default policy selects the highest available quality up to `1080p`

#### Scenario: TV starts playback
- **WHEN** Android TV playback starts with multiple qualities
- **THEN** the default policy selects the highest available quality with no app-level cap

#### Scenario: Manual quality changes in player
- **WHEN** the user changes quality inside the player
- **THEN** only the current playback session changes and account-scoped quality preference is not updated

#### Scenario: Persistent quality preference change is requested
- **WHEN** the user wants to change the persistent default quality
- **THEN** that change is available only from Settings and never as a side effect of in-player quality selection

### Requirement: Playback Speed
Playback speed SHALL support `0.50x`, `0.60x`, `0.70x`, `0.80x`, `0.90x`, `1.00x`, `1.25x`, `1.50x`, and `2.00x`.

#### Scenario: Logged-in user changes speed
- **WHEN** a logged-in user changes speed in the player
- **THEN** the active account profile's playback speed preference updates immediately and later streamed, downloaded, and trailer playback for that account start with that speed

#### Scenario: Logged-out offline speed is used
- **WHEN** logged-out offline playback uses a local/default speed
- **THEN** account-scoped preferences are not mutated

### Requirement: Stream URL Recovery
Stream URL expiry or failure SHALL use at most one same-item Kodi-compatible detail/media refresh and one playback retry.

#### Scenario: Refreshed playback retry fails
- **WHEN** refreshed streamed playback still fails
- **THEN** the player shows a recoverable error with Retry and Back to details actions

### Requirement: Current Playback Quality Fallback
If selected streamed quality fails and lower compatible qualities are known, the app SHALL perform at most one current-playback-only fallback to the nearest lower compatible quality.

#### Scenario: Quality fallback occurs
- **WHEN** playback falls back to a lower quality
- **THEN** the app shows a non-blocking notice and does not persist the fallback as the user's quality preference

### Requirement: Auth Failure During Playback
Auth failure (HTTP `401` or equivalent typed auth-invalid category from streamed playback or same-item URL refresh) SHALL stop online playback, save progress, mark auth invalid, and route to auth repair without auth-sensitive retry loops.

#### Scenario: Local downloaded playback sees auth failure elsewhere
- **WHEN** auth fails while the user is playing a local downloaded file
- **THEN** playback is not interrupted and auth repair is deferred until an online-only action

### Requirement: Explicit Watched Toggle Scope
Explicit watched and unwatched actions SHALL update only the local progress scope relevant to the current UI context.

#### Scenario: Toggle occurs in logged-in online context
- **WHEN** the user toggles watched or unwatched from logged-in online catalog, detail, or library context
- **THEN** only active account-scoped watched/progress state is updated

#### Scenario: Toggle occurs in downloaded or offline context
- **WHEN** the user toggles watched or unwatched from downloaded, offline, or logged-out Offline Library context
- **THEN** only shared offline watched/progress state for that downloaded media is updated

#### Scenario: Logged-in user acts from downloaded view
- **WHEN** a logged-in user toggles watched or unwatched from a downloaded/offline view
- **THEN** shared offline watched/progress state is updated and the UI may label the result as local to this device

#### Scenario: Scopes would be mirrored automatically
- **WHEN** a watched/unwatched toggle updates one local scope
- **THEN** the app does not automatically mirror that change into the other account-scoped or shared offline scope

#### Scenario: User logs in after offline playback
- **WHEN** shared offline progress exists and the user logs in
- **THEN** shared offline progress is not copied into account-scoped progress unless explicit logged-in playback, resume, seek, or completion later updates account-scoped progress

### Requirement: Playback Service Notification
Playback foreground services SHALL declare `mediaPlayback` foreground-service type and create a platform-compliant playback notification channel only when background or TV playback requires it.

#### Scenario: Background playback starts
- **WHEN** playback transitions to background or TV mode
- **THEN** the service shows a platform-compliant foreground notification on its dedicated playback channel and does not start from `BOOT_COMPLETED`

