# Player Controls & Gestures

**Status**: Implemented  
**Last updated**: 2026-09-26

Reference for the video player overlay: on-screen controls, touch gestures,
mouse behaviour, keyboard shortcuts, and the platform differences behind them.

Code lives in `:feature:player`:

| File | Responsibility |
|---|---|
| `PlayerScreen.kt` | Overlay composition, gesture detectors, indicator |
| `PlayerOverlaySections.kt` | Control bar, pickers, time formatting |
| `PlayerControllerState.kt` | Chrome visibility (requesters + auto-hide) |
| `PlayerSeekState.kt` | Slider preview / commit / cancel |
| `PlayerGestureState.kt` | Drag routing, scrub/value deltas, indicator state |
| `PlayerKeyboardControls.kt` | Desktop/web keyboard shortcuts |

---

## On-screen controls

| Control | Behaviour |
|---|---|
| **Play / Pause** | Centered primary button; label follows the playback intent |
| **«10s / 10s»** | Rewind / fast-forward 10 s, clamped to the media bounds; disabled while the duration is unknown (live streams, sources still opening) |
| **Seek slider** | Drag previews the position, the player is seeked once on release. Dragging 144 dp above the slider cancels the seek and shows "Release to cancel" |
| **Speed chip** | Opens the speed picker (0.5×–2×); the selection is persisted |
| **CC chip** | Opens the subtitle picker (tracks are parsed and rendered by the app) |
| **Quality chip** | Opens the quality picker |
| **Full / Exit** | Toggles fullscreen |
| **Close** | Leaves the player screen |
| **Next episode** | Prompt with countdown when the current episode ends |

**Auto-hide.** The chrome hides after 4 s of uninterrupted playback. Paused
playback always keeps it on screen; an open picker or an in-flight slider drag
suspends hiding through visibility requesters, and any interaction restarts the
timer.

**Adaptive layout.** At 560 dp and wider (landscape phones, tablets, TV) the
options and transport controls share one compact row. Narrower portrait windows
stack the options above the transport row so nothing collides.

---

## Touch gestures

| Gesture | Action | Feedback |
|---|---|---|
| Tap | Show / hide the chrome | — |
| Double-tap left third | Rewind 10 s | `« 00:10` pill |
| Double-tap right third | Fast-forward 10 s | `00:10 »` pill |
| Double-tap middle | Play / pause | — |
| Long-press the video | Play at 2.5× until release, then restore | `2.5x` pill |
| Swipe horizontally | Scrub (full width ≈ 90 s); seek on release | `« 00:15` / `00:15 »` pill; detached progress line when the chrome is hidden |
| Move up while scrubbing | Cancel the seek | "Release to cancel" pill |
| Swipe vertically, right third | Volume | `Volume 65%` pill + bar |
| Swipe vertically, left third | Brightness | `Brightness 40%` pill + bar |

Gestures are touch-only; mouse input has its own mapping below. The indicator
pill sits top-centre: held gestures (scrub, volume/brightness, long-press) show
while active, one-shot feedback (double-tap skips) hides after 700 ms.

---

## Mouse (desktop, web)

| Input | Action |
|---|---|
| Click | Play / pause |
| Double-click | Toggle fullscreen |
| Move the mouse | Show the chrome (hides 4 s later) |
| Click a control | Normal control behaviour |

---

## Keyboard (desktop, web)

Enabled by the desktop and web hosts; touch-first hosts leave it off.

| Key | Action |
|---|---|
| `Space` / `K` | Play / pause |
| `←` / `→` | Seek ∓5 s; **hold** to run at 2× until released |
| `F` | Toggle fullscreen |

While a picker is open its buttons own the keyboard; focus returns to the player
when the last picker closes.

---

## Platform differences

| Capability | Android | Desktop | Web (wasmJs) |
|---|---|---|---|
| Volume swipe | System media volume (`AudioManager`) | Player volume | Player volume |
| Brightness swipe | Window brightness | Not available (ignored) | Not available (ignored) |
| Fullscreen | Host immersive mode (landscape, system bars hidden) | Player-library window | Browser layout |
| Keyboard shortcuts | Off (touch-first; TV D-pad model not implemented) | On | On |

---

## External player handoff (Android)

An account-scoped setting (`Settings → Quality & Playback → Open in external player`) routes
online streams to an installed third-party player instead of the in-app player. Tapping Play
resolves the signed stream URL and hands it to the system `ACTION_VIEW` chooser; the selected
subtitle track (when subtitles are enabled) and the resume position are attached through the
extras VLC and MX Player document, so players that support them start on the right track and
position. The in-app player screen closes itself once the handoff succeeds, and falls back to
in-app playback when no external app accepts the stream.

Downloaded (offline) media always plays in-app: its app-private files are not exposed to
third-party apps. External playback is fire-and-forget — the external player does not report
position or completion back, so in-app progress is not updated by an external session, and the
in-app stream-refresh flow cannot apply once the URL has been handed off.

---

## Behaviour notes

- Skips and scrubs are clamped to `0..duration`; non-finite positions or
  durations (browsers report `NaN` before metadata) are ignored instead of
  seeking to zero or `NaN`.
- The slider preview and the scrub indicator are pure UI state — the player is
  seeked once per gesture, on release.
- Consecutive skips accumulate from the player's slider position, so rapid taps
  are not lost while the backend is still applying the previous seek.
- The volume fallback on desktop/web uses the player's own volume; Android
  hosts pass `AndroidVolumeControl`/`AndroidBrightnessControl` so the gestures
  behave like the phone's volume and brightness controls.

---

## Not implemented

- Android picture-in-picture and background playback
- TV D-pad player model
- Haptics
- Frame previews while scrubbing and buffered-range painting
- Configurable skip duration; skip intro/outro data (see
  [skip-intro-outro.md](skip-intro-outro.md))
