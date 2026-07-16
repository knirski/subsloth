package net.subsloth.catalog

import app.cash.turbine.test
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
class HomeViewModelTest {
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
    fun `default saved state selects MOVIES tab`() = runTest(testDispatcher) {
        val vm = HomeViewModel()
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.MOVIES)
        }
    }

    @Test
    fun `saved MOVIES tab is restored`() = runTest(testDispatcher) {
        val vm = HomeViewModel(savedState = mapOf("selectedTab" to "MOVIES"))
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.MOVIES)
        }
    }

    @Test
    fun `saved SHOWS tab is restored`() = runTest(testDispatcher) {
        val vm = HomeViewModel(savedState = mapOf("selectedTab" to "SHOWS"))
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.SHOWS)
        }
    }

    @Test
    fun `saved SEARCH tab is restored`() = runTest(testDispatcher) {
        val vm = HomeViewModel(savedState = mapOf("selectedTab" to "SEARCH"))
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.SEARCH)
        }
    }

    @Test
    fun `invalid saved tab defaults to MOVIES`() = runTest(testDispatcher) {
        val vm = HomeViewModel(savedState = mapOf("selectedTab" to "INVALID"))
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.MOVIES)
        }
    }

    @Test
    fun `empty saved tab defaults to MOVIES`() = runTest(testDispatcher) {
        val vm = HomeViewModel(savedState = mapOf("selectedTab" to ""))
        vm.uiState.test {
            val content = awaitItem() as HomeUiState.Content
            assertThat(content.selectedTab).isEqualTo(HomeTab.MOVIES)
        }
    }
}
