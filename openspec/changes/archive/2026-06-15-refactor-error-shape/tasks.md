## 1. Drop DomainResultException

- [ ] 1.1 Delete `core/model/.../error/DomainResultException.kt`.
- [ ] 1.2 `core/network/.../Mapper.kt:53, 156` — change `Result.failure(DomainResultException(DecodeError.MissingFields(...)))` to `Result.failure(DecodeError.MissingFields(...))`.
- [ ] 1.3 `core/network/.../CatalogRepository.kt:99-105` — change `Result.failure(DomainResultException(SyncError.Unknown))` to `Result.failure(SyncError.Unknown)`. Remove the now-redundant `else -> mapExceptionToSyncError(e)` branch.
- [ ] 1.4 `androidApp/.../SubSlothNavHost.kt:108` — same unwrap-ceremony removal.
- [ ] 1.5 Grep for any remaining `(error as? DomainResultException)` sites and convert to `(error as? DomainError)`.

## 2. Add Technical / Business super-categories

- [ ] 2.1 `core/model/.../error/DomainError.kt` — add `sealed interface Technical : DomainError` and `sealed interface Business : DomainError` as direct children of `DomainError`.
- [ ] 2.2 Make `NetworkError`, `DecodeError`, and `SyncError` extend `Technical`.
- [ ] 2.3 Make `AuthError`, `PaymentLimitError`, `MediaError`, `DownloadError`, `QualityError`, and `LibraryError` extend `Business`.
- [ ] 2.4 Confirm every `when` over a sub-hierarchy still compiles and remains exhaustive (compiler enforces).
- [ ] 2.5 Add `DomainErrorTest` asserting `is Technical` and `is Business` membership for every variant.

## 3. PlaybackErrorClassifier (pure-domain)

- [ ] 3.1 Create `core/domain/.../policy/PlaybackErrorClassifier.kt` with `object PlaybackErrorClassifier { fun classify(error: DomainError): PlaybackError = when (error) { ... } }`. Map `NetworkError.HttpError(401)` → `AuthFailure`, `NetworkError.HttpError(403)` → `StreamUrlExpired`, everything else → `Recoverable(error)`.
- [ ] 3.2 `feature/player/.../PlayerViewModel.kt` — delete the `categorizePlaybackError`, `isLikelyAuthError`, and `isLikelyStreamExpired` private methods. Replace the three call sites with `PlaybackErrorClassifier.classify(error)`.
- [ ] 3.3 `feature/player/.../PlayerScreen.kt:483-489` — when `playbackError is Recoverable`, forward `cause` to the UI message mapper.
- [ ] 3.4 Add `core/domain/.../policy/PlaybackErrorClassifierTest.kt` — parameterised over every `NetworkError` variant, every `SyncError` variant, and a sample of `Business` errors.

## 4. Verify

- [ ] 4.1 `./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :androidApp:assembleDebug test`
- [ ] 4.2 `openspec validate refactor-error-shape --strict`
