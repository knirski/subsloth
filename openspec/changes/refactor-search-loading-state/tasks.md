# Tasks

- [x] 1. Refactor SearchUiState to sealed `Idle | Loading | Results`
- [x] 2. Update SearchScreen to handle Loading variant
- [x] 3. Add yield() in searchInternal so StateFlow flushes Loading
- [x] 4. Update SearchViewModelTest: drop isLoading assertion, add
        positive Loading emission test
- [x] 5. Pre-commit suite (spotless, detekt, jvmTest, 3-platform builds)
- [x] 6. Open PR, address review, merge
- [x] 7. Archive OpenSpec change
