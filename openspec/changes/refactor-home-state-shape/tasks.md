# Tasks

- [x] 1. Add `isSyncing` to `HomeUiState.Content`
- [x] 2. Remove `HomeViewModel._isSyncing`; use `updateContent` instead
- [x] 3. Replace `replay = 1` with `extraBufferCapacity = 1` on syncErrors
- [x] 4. Update `HomeScreen` to read isSyncing from Content
- [x] 5. Update `HomeViewModelTest` isSyncing test to read from uiState
- [x] 6. Pre-commit suite (spotless, detekt, test, all 3 platform builds)
- [x] 7. Open PR, address review, merge
- [x] 8. Archive OpenSpec change
