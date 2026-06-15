## Why

`DomainError` currently mixes two semantically distinct categories of failure in a single flat sealed hierarchy:

- **Technical failures** — what went wrong at the I/O layer (network timeout, server HTTP error, JSON decode failure). The user cannot fix these directly; the appropriate response is "try again" with a backoff and a diagnostic log.
- **Business failures** — what the user did wrong or what's blocking them in domain terms (invalid credentials, geo-restricted media, insufficient storage, subscription required). The appropriate response is a specific, actionable message ("Sign in again", "Subscribe to watch this", "Free up storage").

Collapsing these into one `sealed interface DomainError` makes UI rendering and telemetry classification harder than it needs to be. Every consumer has to inspect the variant name to decide which category it belongs to. Adding a `sealed interface Technical` and `sealed interface Business` super-category inside `DomainError` makes the category explicit at the type level, so the UI can `when` over `Business`/`Technical` once and dispatch.

This change also lands the rest of the original `refactor-domain-errors-as-values` scope: drop the `DomainResultException` wrapper (every error site today does `as? DomainResultException` ceremony to recover the typed `DomainError`), and lift `PlaybackErrorClassifier` to `:core:domain` so the player ViewModel stops string-matching on `message.contains("401")`.

## What Changes

- **Add `Technical` and `Business` super-categories** to `DomainError`. `NetworkError`, `DecodeError`, and `SyncError` become children of `Technical`. `AuthError`, `PaymentLimitError`, `MediaError`, `DownloadError`, `QualityError`, and `LibraryError` become children of `Business`. `SyncError` is reshaped so that all variants are reachable from the I/O shell (`NoConnectivity`, `Timeout`, `ServerError`, `Unknown`).
- **Drop `DomainResultException`.** Every site currently does `Result.failure(DomainResultException(SyncError.Unknown))`; change to `Result.failure(SyncError.Unknown)`. The unwrap `error as? DomainResultException` everywhere becomes `error as? DomainError` (no wrapper).
- **Lift `PlaybackErrorClassifier` to `:core:domain/policy`.** Pure function `classify(error: DomainError): PlaybackError` that maps `NetworkError.HttpError(401)` → `PlaybackError.AuthFailure`, `NetworkError.HttpError(403)` → `PlaybackError.StreamUrlExpired`, everything else → `PlaybackError.Recoverable(cause)`. The `PlayerViewModel` `categorizePlaybackError` / `isLikelyAuthError` / `isLikelyStreamExpired` private string-matchers are deleted; the ViewModel calls the classifier.
- **`PlayerScreen` `error.cause` rendering:** forward `Recoverable.cause` to the UI message mapper.

## Capabilities

### Modified Capabilities

- `architecture`: extends the typed-error composition with a Technical/Business super-category and codifies the "errors as values" property (no exception wrapper).
- `playback`: requires the player VM to use a pure-domain classifier rather than string-matching on exception messages.

## Impact

- Affected modules: `:core:model`, `:core:domain`, `:core:network`, `:core:network` test, `:feature:player`, `:feature:player` test, `:androidApp` (one `DomainResultException` reference), `:core:ui` (already exhaustive over `UiError`).
- No new dependencies. No public API breakage for end users. The `DomainResultException` deletion is a `core:model` API removal that every site already explicitly tolerates.
- Risk: medium. The Business/Technical split changes the type hierarchy. Consumers that did `is DomainError` still compile; consumers that did `when (e: DomainError)` are forced to handle the new sub-hierarchy correctly.
