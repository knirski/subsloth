## 1. DomainError root is usable

- [ ] 1.1 Add direct `data object` variants (or nested `sealed interface` per category) under `DomainError` in `core/model/.../DomainError.kt` so `when (e: DomainError)` is exhaustive.
- [ ] 1.2 Update all existing `when (e: DomainError)` sites (catalog, network, library, player) to handle the new direct variants.
- [ ] 1.3 Add a unit test asserting all sub-hierarchies (`AuthError`, `NetworkError`, …) still match the root.

## 2. Single NetworkErrorClassifier

- [ ] 2.1 Create `core/domain/.../policy/NetworkErrorClassifier.kt` with a pure function `classify(throwable: Throwable): DomainError` that maps a sealed `KtorCallError` hierarchy to `DomainError` (no string matching).
- [ ] 2.2 Delete the two `isIoError` private helpers in `core/network/.../CatalogRepository.kt:275` and `core/network/.../UiErrorMapping.kt:32`. Inject `NetworkErrorClassifier` (or call the domain function) at both sites.
- [ ] 2.3 Add `NetworkErrorClassifierTest` covering: connect timeout, DNS failure, TLS failure, HTTP 5xx, HTTP 429, malformed response, unexpected redirect.

## 3. PlayerViewModel session state in UiState

- [ ] 3.1 Add `session: PlayerSession?` and `snapshotCountSinceSave: Int = 0` to `PlayerUiState.Content` in `feature/player/.../PlayerViewModel.kt`.
- [ ] 3.2 Remove the two standalone `var` fields at line 102-103. Replace every read with `_uiState.value.session` / `snapshotCountSinceSave`.
- [ ] 3.3 Confirm `PlayerViewModelTest` still passes (no behavior change).

## 4. HomeViewModel sync via Channel + flatMapLatest

- [ ] 4.1 Add `private val syncChannel = Channel<Unit>(Channel.CONFLATED)` in `HomeViewModel`.
- [ ] 4.2 Replace `private var syncJob: Job?` (line 99) and `syncJob?.cancel()` (line 123) with a `consumeAsFlow().flatMapLatest { syncInternal() }` collector launched in `init`.
- [ ] 4.3 `fun sync()` becomes `syncChannel.trySend(Unit)`.
- [ ] 4.4 Remove the `syncChannel.close()` in `onCleared` (or keep — confirm with the SearchViewModel pattern).

## 5. WATCHED_THRESHOLD constant

- [ ] 5.1 Add `const val WATCHED_THRESHOLD: Double = 0.9` to `core/domain/.../policy/CompletionPolicy.kt` (sibling to existing `COMPLETION_THRESHOLD`).
- [ ] 5.2 Replace `progress.fraction > 0.9` in `DownloadsViewModel.kt:84` and `LibraryViewModel.kt:110` with `progress.fraction > CompletionPolicy.WATCHED_THRESHOLD`.

## Verification

- [ ] 6.1 `./gradlew spotlessApply spotlessCheck detekt :core:model:compileKotlinJvm :core:domain:compileKotlinJvm :androidApp:assembleDebug test`
- [ ] 6.2 `openspec validate refactor-fc-is-harden --strict`
