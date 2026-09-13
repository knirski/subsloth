package net.subsloth

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import net.subsloth.core.ui.theme.SubSlothTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.domain.policy.ApiBaseUrlPolicy
import net.subsloth.core.media.download.DownloadForegroundService
import net.subsloth.core.ui.AppNavKey
import net.subsloth.core.ui.DownloadsKey
import net.subsloth.core.ui.LocalIsTelevision
import net.subsloth.core.ui.OfflineLibraryKey
import net.subsloth.core.ui.RootContainerViewModel
import net.subsloth.core.ui.SessionGate

class MainActivity : ComponentActivity() {
    private val deepLinkDestination = MutableStateFlow<AppNavKey?>(null)

    private val isTelevision: Boolean by lazy {
        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        uiModeManager.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val destination by deepLinkDestination.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalIsTelevision provides isTelevision) {
                SubSlothTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                    val app = LocalContext.current.applicationContext
                    val container = (app as? SubSlothApplication)?.container
                    val userPreferences = container?.userPreferences ?: run {
                        Logger.withTag("MainActivity").e { "SubSlothApplication container not found" }
                        null
                    }
                    val root: RootContainerViewModel = viewModel(
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                                requireNotNull(modelClass.cast(RootContainerViewModel(container?.sessionPort)))
                        },
                    )
                    val sessionPort = root.sessionPort
                    SessionGate(
                        sessionPort = sessionPort,
                        login = {
                            var showOfflineLibrary by rememberSaveable { mutableStateOf(false) }
                            if (showOfflineLibrary) {
                                SubSlothNavHost(
                                    startDestination = OfflineLibraryKey,
                                    onExitOfflineLibrary = { showOfflineLibrary = false },
                                )
                            } else {
                                val viewModel: LoginViewModel = viewModel {
                                    LoginViewModel(
                                        sessionPort = sessionPort,
                                        readApiBaseUrl = {
                                            container?.apiBaseUrlFlow() ?: flowOf(
                                                ApiBaseUrlPolicy.resolve(
                                                    stored = null,
                                                    configured = BuildConfig.SUBSLOTH_API_BASE_URL,
                                                ),
                                            )
                                        },
                                        saveApiBaseUrl = { url ->
                                            userPreferences?.setApiBaseUrl(url)
                                        },
                                    )
                                }
                                LoginScreen(
                                    viewModel = viewModel,
                                    onNavigateToOfflineLibrary = { showOfflineLibrary = true },
                                    onNavigateToCatalog = {},
                                )
                            }
                        },
                        authenticated = {
                            SubSlothNavHost(
                                deepLinkDestination = destination,
                                onConsumeDeepLink = { deepLinkDestination.value = null },
                            )
                        },
                    )
                }
            }
        }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        destinationForAction(intent?.action)?.let { destination ->
            deepLinkDestination.value = destination
        }
    }
}

/**
 * Maps a launch intent action to the navigation destination it should open.
 * Pure so it is unit-testable without an Activity.
 */
internal fun destinationForAction(action: String?): AppNavKey? = when (action) {
    DownloadForegroundService.ACTION_OPEN_DOWNLOADS -> DownloadsKey
    else -> null
}
