# Refactor: promote data-only sealed types to enums

## Why

Five sealed types in the codebase have only `data object` variants and
carry no per-variant state. They are pure enumerations, but they pay
the cost of the sealed-type machinery (synthetic `Companion`, no
`entries` constant, no compile-time `values()` total). Converting them
to `enum class`:

- gives the compiler a guarantee of the variant set
- exposes the standard `entries` and `values()` APIs for iteration
- removes the `is` antipattern flagged by detekt (`'is' over enum entry
  is prohibited. Use comparison instead.`)
- makes the contract uniform with the existing `QualityDescriptor`
  etc. that already use `enum class`

## Scope

Five types, all in `:core:model` or `:core:domain`:

- `EnqueueOutcome` (`Queued`, `AlreadyAvailableHigherQuality`)
- `TransferPreference` (`WifiOnly`, `MeteredAllowed`)
- `SubtitleEnqueueOutcome` (`Queued`, `AlreadyAvailable`)
- `DownloadFailureReason` (`NeedsWifi`, `InsufficientStorage`,
  `MissingLocalFile`, `SubtitleUnavailable`, `AmbiguousQuality`,
  `DownloadFailed`, `Unavailable`)
- `DownloadCommandOutcome` (`Applied`, `NoOp`)

All are stable contracts: no new variants, no removal.

## Out of scope

- Types that carry per-variant data (`DownloadState`, `SizeEstimate`,
  `SubtitleSelection`, `Session`, `Media`, `DomainError`, `UiError`,
  all `*UiState` types). They stay sealed.
- New detekt rules. The existing `'is' over enum entry` rule already
  flags the antipattern; we let the compiler drive the change.

## Risk

- One downstream `when (reason) { is X -> ... }` site needed the
  `is` removal (DownloadsScreen.formatFailureReason).
- All other consumers either reference variants as values
  (`EnqueueOutcome.Queued`) or match without `is` already.
- No public API change to consumers; reference values are unchanged.
