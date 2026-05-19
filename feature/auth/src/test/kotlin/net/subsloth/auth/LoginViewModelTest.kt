package net.subsloth.auth

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
class LoginViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Initial state ─────────────────────────────────────────────────────

    @Test
    fun `no credentials routes to login`() = runTest(testDispatcher) {
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
            )
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoginForm::class.java)
    }

    @Test
    fun `stored credentials routes to catalog`() = runTest(testDispatcher) {
        var navigated = false
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { true },
                onLoginSuccess = { navigated = true },
            )
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
        assertThat(navigated).isTrue()
    }

    @Test
    fun `offline library shown when playable downloads exist`() = runTest(testDispatcher) {
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
                hasPlayableDownloads = { true },
            )
        val state = viewModel.uiState.value as LoginUiState.LoginForm
        assertThat(state.hasOfflineLibrary).isTrue()
    }

    @Test
    fun `offline library hidden when no playable downloads`() = runTest(testDispatcher) {
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
                hasPlayableDownloads = { false },
            )
        val state = viewModel.uiState.value as LoginUiState.LoginForm
        assertThat(state.hasOfflineLibrary).isFalse()
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Test
    fun `valid credentials navigate to catalog`() = runTest(testDispatcher) {
        var navigated = false
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
                onLoginSuccess = { navigated = true },
                validateCredentials = { _, _ -> Result.success(Unit) },
            )
        viewModel.login("user@test.com", "password")
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
        assertThat(navigated).isTrue()
    }

    @Test
    fun `invalid credentials show auth error`() = runTest(testDispatcher) {
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
                validateCredentials = { _, _ ->
                    Result.failure(Exception("Invalid credentials"))
                },
            )
        viewModel.login("user@test.com", "wrong")
        val state = viewModel.uiState.value as LoginUiState.LoginForm
        assertThat(state.error).isNotNull()
    }

    @Test
    fun `login shows loading indicator`() = runTest(testDispatcher) {
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
                validateCredentials = { _, _ ->
                    kotlinx.coroutines.delay(100)
                    Result.success(Unit)
                },
            )
        viewModel.login("user@test.com", "password")
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.Loading::class.java)
    }

    // ── Logout ────────────────────────────────────────────────────────────

    @Test
    fun `logout clears credentials and routes to login`() = runTest(testDispatcher) {
        var credentialsCleared = false
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { true },
                onLogout = { credentialsCleared = true },
            )
        viewModel.logout()
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoginForm::class.java)
        assertThat(credentialsCleared).isTrue()
    }

    @Test
    fun `logout does not trigger validation`() = runTest(testDispatcher) {
        var validationCalled = false
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { true },
                validateCredentials = { _, _ ->
                    validationCalled = true
                    Result.success(Unit)
                },
            )
        viewModel.logout()
        assertThat(validationCalled).isFalse()
    }

    // ── Auth repair ───────────────────────────────────────────────────────

    @Test
    fun `auth repair sets AuthRepair state`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel()
        viewModel.retryAuth()
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.AuthRepair::class.java)
    }

    @Test
    fun `dismissNeedsAuthRepair returns to login form`() = runTest(testDispatcher) {
        val viewModel = LoginViewModel()
        viewModel.retryAuth()
        viewModel.dismissNeedsAuthRepair()
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoginForm::class.java)
    }

    // ── Offline Library ───────────────────────────────────────────────────

    @Test
    fun `login screen does not show offline library when logged in`() = runTest(testDispatcher) {
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { true },
                hasPlayableDownloads = { true },
            )
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
    }

    @Test
    fun `logged out offline library does not trigger validation`() = runTest(testDispatcher) {
        var validationCalled = false
        val viewModel =
            LoginViewModel(
                hasStoredCredentials = { false },
                hasPlayableDownloads = { true },
                validateCredentials = { _, _ ->
                    validationCalled = true
                    Result.success(Unit)
                },
            )
        assertThat(validationCalled).isFalse()
    }
}
