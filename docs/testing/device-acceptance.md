# Device Acceptance Testing

This document describes the manual device acceptance checklist for the three supported device targets. Each test scenario must pass on every device before a release is considered ready.

## Target Devices

| Target | Minimum API | Form Factor | Primary Concerns |
|---|---|---|---|
| Android TV 8 | API 26 | TV (1920×1080) | D-pad navigation, leanback UX, playback focus |
| Android tablet 13 | API 33 | Tablet (default >= 600dp w) | Adaptive layout, split-screen, landscape/portrait |
| Android phone 16 | API 36 | Phone (default < 600dp w) | Touch navigation, portrait-first, edge-to-edge |

---

## 1. Login / Logout

### 1.1 First-time login
1. Launch the app on a clean install (no stored credentials).
2. Verify the login screen is displayed with email/password fields and a submit button.
3. Enter valid Kodi credentials and tap **Sign In**.
4. Verify the app navigates to the home screen (catalog) after successful authentication.
5. Kill the app and relaunch. Verify the session is restored (home screen, not login).

### 1.2 Invalid credentials
1. On the login screen, enter an invalid email or password.
2. Tap **Sign In**.
3. Verify an appropriate error message is shown and the user remains on the login screen.
4. Verify that no cached catalog or library data is visible after failure.

### 1.3 Logout
1. While signed in, navigate to **Settings** → **Sign Out**.
2. Confirm the logout dialog.
3. Verify the app returns to the login screen.
4. Verify that shared offline media and progress data is retained (see [Logout retention](#appendix-logout-retention-partition)).

### 1.4 Multi-profile detection
1. Sign in with an account that has access to multiple Kodi profiles.
2. Verify the profile picker screen appears (if applicable to the Kodi version).
3. Select a profile and verify the home screen loads the correct profile's watch library.

---

## 2. Browsing

### 2.1 Home screen — phone / tablet
1. Verify the home screen loads recommended / recently added rows.
2. Scroll through all rows. Verify artwork, titles, and metadata display correctly.
3. Tap a movie poster. Verify navigation to movie detail.

### 2.2 Home screen — TV
1. Verify the home screen is fully navigable with the D-pad (left/right within a row, up/down between rows).
2. Verify the focused item is visually highlighted.
3. Verify that pressing **OK/Select** on an item navigates to its detail screen.

### 2.3 Search
1. Navigate to the search screen.
2. Enter a partial title. Verify search results update as the query is typed (debounced).
3. Tap a result. Verify navigation to the correct detail screen.

### 2.4 Library
1. Navigate to the **Library** tab/section.
2. Verify the library shows known movies and TV series.
3. Toggle between **Movies** and **Series** views (if available).
4. Tap an item. Verify navigation to detail.

### 2.5 Catalog navigation — TV
1. Verify D-pad can navigate between all top-level sections (Home, Search, Library, Downloads, Settings).
2. Verify focus does not get stuck in any section.
3. Verify pressing **Back** returns to the previous screen.

---

## 3. Details (No Comments)

### 3.1 Movie detail
1. Navigate to a movie detail screen.
2. Verify the following metadata is displayed: title, year, runtime, genres, rating, plot summary, artwork.
3. Verify a **Play** button and **Download** button are present.
4. Verify there is **no comments / reviews section** anywhere on the screen.

### 3.2 Series detail
1. Navigate to a TV series detail screen.
2. Verify series metadata (title, year, seasons, genres, rating, plot).
3. Verify season and episode lists are displayed.
4. Tap an episode. Verify navigation to episode detail with a **Play** button.
5. Verify there is **no comments / reviews section**.

### 3.3 Adaptive layout — detail
1. On a phone, verify the detail layout is vertical (scrollable metadata, then actions).
2. On a tablet, verify the layout uses available horizontal space (e.g., wider metadata panels or side-by-side layout).
3. On TV, verify the layout is optimized for large screens (no scrolling text if focus can reach all elements).

---

## 4. Playback / Resume

### 4.1 Start playback
1. From a movie or episode detail screen, tap **Play**.
2. Verify playback starts within 5 seconds over a reliable network.
3. Verify video and audio output are correct.

### 4.2 Playback controls
1. During playback, verify on-screen controls appear on tap (phone/tablet) or D-pad activation (TV).
2. Verify **Play/Pause**, **Seek forward/backward**, and **Stop** work correctly.
3. Verify the current playback position and duration are displayed.

### 4.3 Playback resume
1. Start playing a movie or episode.
2. Seek to a position at least 3 minutes in, then stop or navigate away.
3. Return to the same detail screen and tap **Play**.
4. Verify playback resumes from the saved position (within a reasonable tolerance, e.g., ±10 seconds).
5. Verify the resume position persists after killing and relaunching the app.

### 4.4 Error and recovery
1. Start playback, then switch the device to airplane mode.
2. Verify the player shows a network error state (not a crash or blank screen).
3. Restore connectivity and verify the player recovers or the user can restart playback.

---

## 5. Subtitles

### 5.1 Subtitle selection
1. During playback of a title with known subtitles, open the subtitle picker.
2. Verify available subtitle tracks (languages) are listed.
3. Select a subtitle track. Verify subtitles appear on screen in sync with audio.

### 5.2 Subtitle toggle
1. With subtitles active, toggle them off via the subtitle picker.
2. Verify subtitles disappear from the video.
3. Re-enable subtitles. Verify they reappear.

### 5.3 Subtitle persistence
1. Select a subtitle track for a movie/episode.
2. Stop playback and restart the same title.
3. Verify the previously selected subtitle track is restored.

---

## 6. Offline Mode

### 6.1 Offline launch
1. Sign in and cache some catalog data (browse the home screen and library) while online.
2. Switch the device to airplane mode.
3. Kill and relaunch the app.
4. Verify the app shows the cached home screen (or an appropriate offline state) instead of a network error login screen.

### 6.2 Offline browsing
1. While offline, browse the home screen and library.
2. Verify previously cached metadata (titles, artwork, plot) is displayed.
3. Verify a subtle offline indicator is shown (e.g., a banner or icon).

### 6.3 Offline playback
1. While offline, attempt to play a title that has not been downloaded.
2. Verify the app shows a "not available offline" message or a download prompt.
3. Play a title that **has** been downloaded (see [Downloads](#7-downloads)).
4. Verify offline playback works with full video and audio.

---

## 7. Downloads

### 7.1 Start a download
1. Navigate to a movie or episode detail screen.
2. Tap **Download**.
3. Verify a download progress indicator appears (percentage or progress bar).
4. Verify the item appears in the **Downloads** section with "Downloading" status.

### 7.2 Download completion
1. Wait for a small download to complete (or use a known-small fixture).
2. Verify the item shows "Downloaded" status in the Downloads section.
3. Verify the item is playable offline.

### 7.3 Download pause / resume
1. Start a download.
2. Pause the download (if the UI supports it).
3. Resume the download. Verify it continues from where it stopped.

### 7.4 Cancel a download
1. Start a download.
2. Cancel the download.
3. Verify the partial file is removed and the item no longer shows as "Downloading".

### 7.5 Delete a download
1. For a completed download, tap **Delete** or **Remove**.
2. Confirm the deletion.
3. Verify the item is removed from the Downloads section and its offline media is deleted from storage.

---

## 8. Storage Management

### 8.1 Storage usage display
1. Navigate to **Settings** → **Storage**.
2. Verify the displayed storage usage shows total downloaded size.
3. Verify the value approximately matches the sum of all downloaded items.

### 8.2 Clear all downloads
1. In **Settings** → **Storage**, tap **Clear All Downloads** (or equivalent).
2. Confirm the action.
3. Verify all downloaded items are removed and storage usage shows 0 bytes (or near 0).

### 8.3 Low storage behavior
1. Fill device storage to near capacity (e.g., < 500 MB free).
2. Attempt to start a new download.
3. Verify the app shows a low-storage warning and refuses the download.
4. Verify existing downloads remain playable.

---

## 9. Adaptive Behavior

### 9.1 Orientation — phone
1. On a phone, start in portrait orientation.
2. Rotate to landscape. Verify the UI reflows correctly (e.g., catalog grid adjusts columns, detail layout adapts).
3. Rotate back to portrait. Verify the UI returns to the original layout.

### 9.2 Orientation — tablet
1. On a tablet, rotate between portrait and landscape.
2. Verify the layout adapts to use available space (e.g., phone-column layout becomes multi-column table, detail panels become side-by-side).
3. Verify no content is clipped or overlapped after rotation.

### 9.3 Multi-window — tablet
1. On a tablet running Android 13+, enter split-screen mode with the app.
2. Verify the app renders correctly in the reduced size.
3. Adjust the split ratio. Verify the app reflows without crashing.

### 9.4 TV overscan
1. On Android TV, verify that all UI elements (navigation bar, titles, buttons) are fully visible within the overscan-safe area.
2. Verify no text or controls are clipped at the screen edges.

### 9.5 Font scaling — phone / tablet
1. Increase the system font size to the largest setting.
2. Launch the app. Verify all text remains readable and no layout breaks occur.
3. Decrease the system font size to the smallest setting. Verify the same.

---

## Appendix: Logout Retention Partition

When testing logout flows (see [1.3 Logout](#13-logout)), verify the following retention boundaries:

| Data | Retained? | Notes |
|---|---|---|
| Shared downloaded videos / subtitles | ✅ Retained | Other profiles can still play them |
| Shared offline media progress | ✅ Retained | Resume positions for shared media |
| Active-profile preferences | ❌ Cleared | Per-profile settings reset |
| Active-profile watch/library data | ❌ Cleared | Watch history, library content |
| Other profiles' preferences | ✅ Unchanged | Unaffected by logout |
| Other profiles' watch/library data | ✅ Unchanged | Unaffected by logout |

---

## Version History

| Date | Version | Notes |
|---|---|---|
| 2026-07-18 | 1.0 | Initial device acceptance checklist |
