package net.subsloth.settings

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
class DiagnosticsViewModelTest {
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
    fun `emits redacted state on init`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel()
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.installedAppVersion).isNotEmpty()
            assertThat(state.buildType).isEqualTo("debug")
            assertThat(state.apiBaseUrl).isEqualTo("redacted")
            assertThat(state.gitSha).isNull()
        }
    }

    @Test
    fun `state is immutable`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel()
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.installedAppVersion).isEqualTo("1.0.0")
            assertThat(state.versionCode).isEqualTo("1")
            assertThat(state.releaseChannel).isEqualTo("debug-sideload")
        }
    }
}
