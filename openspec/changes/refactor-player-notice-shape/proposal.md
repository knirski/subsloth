# Refactor: PlayerUiState.Notice sealed hierarchy

## Why

`PlayerUiState.Notice` was a `data class` with three optional
fields (`message`, `resKey`, `formatArg`) and a default `message = ""`.
That is the codestyle §8.5 antipattern — 8 combinations, only 3
valid. Empty `Notice()`, `Notice(message="x", resKey="y")`,
`Notice(formatArg="x")` were all representable but never useful.

The audit's "PlayerNotice shape" item called this out as a real
sealed-vs-boolean candidate: the field is logically
"either resource-bound with optional format arg, or raw string".

## Scope

- `PlayerUiState.Notice` becomes a sealed interface with
  `Notice.Localized(resKey: String, formatArg: String? = null)`
  and `Notice.Raw(message: String)`.
- `PlayerViewModel.startPlayback`'s subtitle notice becomes
  `Notice.Localized(resKey = "no_subtitles")` and
  `Notice.Localized(resKey = "subtitle_in", formatArg = ...)`.
- `PlayerScreen.resolve()` exhaustively matches the two variants.
- Existing tests check `notice.isNotNull()` only, so no test
  changes are needed.

## Out of scope

- Other transient Content flags (snapshotCountSinceSave,
  qualityFallbackUsed) are real state, not side-channel
  signals; they stay as fields.
- The `PlayerSnapshot.isLoading` field on the bridge is
  forwarded from the upstream player; not a candidate.

## Risk

- Only the resource key `no_subtitles` and `subtitle_in` are
  used in production; the resolve() fallback for unknown keys
  changes from `message` to `resKey`. The two known keys
  remain in the Localized branch.
- The sealed `Notice` cannot be constructed with `Notice()` —
  existing previews and screenshot test data use `null` for
  these fields, so no other call sites are affected.
