package net.subsloth.settings

import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
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
    fun `shows the effective api base url`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel(
            apiBaseUrl = flowOf("https://api.example.test/api/v2/"),
            session = flowOf(Session.Anonymous),
        )
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.apiBaseUrl).isEqualTo("https://api.example.test/api/v2/")
        }
    }

    @Test
    fun `reports an authenticated session`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel(
            apiBaseUrl = flowOf("https://api.example.test"),
            session =
            flowOf(
                Session.Authenticated(
                    userId = "user-1",
                    openedAtEpochSeconds = 1_700_000_000,
                    credentials = Credentials(login = "user", password = "secret"),
                ),
            ),
        )
        viewModel.state.test {
            assertThat(awaitItem().authStateCategory).isEqualTo("authenticated")
        }
    }

    @Test
    fun `reports an anonymous session`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel(
            apiBaseUrl = flowOf("https://api.example.test"),
            session = flowOf(Session.Anonymous),
        )
        viewModel.state.test {
            assertThat(awaitItem().authStateCategory).isEqualTo("anonymous")
        }
    }

    @Test
    fun `does not expose secrets or unavailable fields`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel(
            apiBaseUrl = flowOf("https://api.example.test"),
            session =
            flowOf(
                Session.Authenticated(
                    userId = "user-1",
                    openedAtEpochSeconds = 1_700_000_000,
                    credentials = Credentials(login = "user", password = "secret"),
                ),
            ),
        )
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.gitSha).isNull()
            assertThat(state.deviceApiLevel).isNull()
            assertThat(state.cacheAge).isNull()
            assertThat(state.downloadQueueCounts).isNull()
            assertThat(state.storageUsage).isNull()
        }
    }

    @Test
    fun `emits immutable version metadata`() = runTest(testDispatcher) {
        val viewModel = DiagnosticsViewModel(
            apiBaseUrl = flowOf("https://api.example.test"),
            session = flowOf(Session.Anonymous),
        )
        viewModel.state.test {
            val state = awaitItem()
            assertThat(state.installedAppVersion).isNotEmpty()
            assertThat(state.buildType).isEqualTo("debug")
            assertThat(state.versionCode).isEqualTo("1")
            assertThat(state.releaseChannel).isEqualTo("debug-sideload")
        }
    }
}
