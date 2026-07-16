package net.subsloth.catalog

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.testing.assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `default state is Idle`() = runTest(testDispatcher) {
        val vm = SearchViewModel()
        assertThat(vm.uiState.value).isInstanceOf(SearchUiState.Idle::class.java)
    }

    @Test
    fun `restores search query from saved state`() = runTest(testDispatcher) {
        val vm = SearchViewModel(savedState = mapOf("searchQuery" to "test movie"))
        val state = vm.uiState.value
        // With synchronous execution, the search completes immediately so the
        // state may be Loading or Results — both preserve the restored query.
        when (state) {
            is SearchUiState.Loading -> assertThat(state.query).isEqualTo("test movie")
            is SearchUiState.Results -> assertThat(state.query).isEqualTo("test movie")
            is SearchUiState.Idle -> throw AssertionError("Expected Loading or Results but got Idle")
        }
    }

    @Test
    fun `blank saved query stays idle`() = runTest(testDispatcher) {
        val vm = SearchViewModel(savedState = mapOf("searchQuery" to ""))
        assertThat(vm.uiState.value).isInstanceOf(SearchUiState.Idle::class.java)
    }

    @Test
    fun `default saved state stays idle`() = runTest(testDispatcher) {
        val vm = SearchViewModel()
        assertThat(vm.uiState.value).isInstanceOf(SearchUiState.Idle::class.java)
    }
}
