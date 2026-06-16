package net.subsloth.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.subsloth.core.domain.port.Credentials
import net.subsloth.core.domain.port.Session
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.model.error.Outcome
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
    fun `no session routes to login`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel = LoginViewModel(sessionPort = session)
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoginForm::class.java)
    }

    @Test
    fun `authenticated session routes to catalog`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = true)
        val viewModel = LoginViewModel(sessionPort = session)
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
    }

    @Test
    fun `offline library shown when playable downloads exist`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel =
            LoginViewModel(
                sessionPort = session,
                hasPlayableDownloads = { true },
            )
        val state = viewModel.uiState.value as LoginUiState.LoginForm
        assertThat(state.hasOfflineLibrary).isTrue()
    }

    @Test
    fun `offline library hidden when no playable downloads`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel =
            LoginViewModel(
                sessionPort = session,
                hasPlayableDownloads = { false },
            )
        val state = viewModel.uiState.value as LoginUiState.LoginForm
        assertThat(state.hasOfflineLibrary).isFalse()
    }

    // ── Login ─────────────────────────────────────────────────────────────

    @Test
    fun `valid credentials transition session to authenticated`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel = LoginViewModel(sessionPort = session)
        viewModel.login("user@test.com", "password")
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
        assertThat(session.current()).isInstanceOf(Session.Authenticated::class.java)
    }

    @Test
    fun `invalid credentials show auth error`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false, rejectLogin = true)
        val viewModel = LoginViewModel(sessionPort = session)
        viewModel.login("user@test.com", "wrong")
        val state = viewModel.uiState.value as LoginUiState.LoginForm
        assertThat(state.error).isNotNull()
    }

    @Test
    fun `login transitions to LoggedIn on success`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel = LoginViewModel(sessionPort = session)
        viewModel.login("user@test.com", "password")
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
    }

    // ── Logout ────────────────────────────────────────────────────────────

    @Test
    fun `logout clears session and routes to login`() = runTest(testDispatcher) {
        var credentialsCleared = false
        val session = FakeSessionPort(startAuthenticated = true)
        val viewModel =
            LoginViewModel(
                sessionPort = session,
                onLogout = { credentialsCleared = true },
            )
        viewModel.logout()
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoginForm::class.java)
        assertThat(credentialsCleared).isTrue()
        assertThat(session.current()).isInstanceOf(Session.Anonymous::class.java)
    }

    @Test
    fun `logout does not attempt re-login`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = true, trackOpen = true)
        val viewModel = LoginViewModel(sessionPort = session)
        viewModel.logout()
        assertThat(session.openCalls).isEqualTo(0)
    }

    // ── Auth repair ───────────────────────────────────────────────────────

    @Test
    fun `auth repair sets AuthRepair state`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel = LoginViewModel(sessionPort = session)
        viewModel.retryAuth()
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.AuthRepair::class.java)
    }

    @Test
    fun `dismissNeedsAuthRepair returns to login form`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false)
        val viewModel = LoginViewModel(sessionPort = session)
        viewModel.retryAuth()
        viewModel.dismissNeedsAuthRepair()
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoginForm::class.java)
    }

    // ── Offline Library ───────────────────────────────────────────────────

    @Test
    fun `login screen does not show offline library when logged in`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = true)
        val viewModel =
            LoginViewModel(
                sessionPort = session,
                hasPlayableDownloads = { true },
            )
        assertThat(viewModel.uiState.value).isInstanceOf(LoginUiState.LoggedIn::class.java)
    }

    @Test
    fun `logged out offline library does not trigger login`() = runTest(testDispatcher) {
        val session = FakeSessionPort(startAuthenticated = false, trackOpen = true)
        LoginViewModel(
            sessionPort = session,
            hasPlayableDownloads = { true },
        )
        assertThat(session.openCalls).isEqualTo(0)
    }
}

private class FakeSessionPort(
    startAuthenticated: Boolean,
    private val rejectLogin: Boolean = false,
    private val loginDelayMillis: Long = 0L,
    private val trackOpen: Boolean = false,
) : SessionPort {
    private val _state = MutableStateFlow<Session>(
        if (startAuthenticated) {
            Session.Authenticated(
                userId = "user",
                openedAtEpochSeconds = 0L,
                credentials = Credentials("user", "pw"),
            )
        } else {
            Session.Anonymous
        },
    )
    override val state: StateFlow<Session> = _state
    var openCalls: Int = 0
        private set
    override fun current(): Session = _state.value
    override fun open(credentials: Credentials): Outcome<Unit> {
        if (trackOpen) openCalls += 1
        if (rejectLogin) {
            return Outcome.Failure(net.subsloth.core.model.error.AuthError.InvalidCredentials)
        }
        _state.value = Session.Authenticated(
            userId = credentials.login.substringBefore('@').ifBlank { "user" },
            openedAtEpochSeconds = 1_700_000_000L,
            credentials = credentials,
        )
        return Outcome.Success(Unit)
    }
    override fun close(): Outcome<Unit> {
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }
    override fun invalidate(): Outcome<Unit> {
        _state.value = Session.Anonymous
        return Outcome.Success(Unit)
    }
}
