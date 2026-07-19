package net.subsloth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.flowOf
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.ui.RootContainerViewModel
import net.subsloth.core.ui.SessionGate
import net.subsloth.preferences.UserPreferences
import net.subsloth.core.ui.SessionGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val root: RootContainerViewModel = viewModel()
                    val sessionPort = root.sessionPort
                    val app = LocalContext.current.applicationContext
                    val container = (app as? SubSlothApplication)?.container
                    val userPreferences = container?.userPreferences ?: run {
                        android.util.Log.e("MainActivity", "SubSlothApplication container not found")
                        null
                    }
                    SessionGate(
                        sessionPort = sessionPort,
                        login = {
                            val viewModel: LoginViewModel = viewModel {
                                LoginViewModel(
                                    sessionPort = sessionPort,
                                    readApiBaseUrl = {
                                        userPreferences?.apiBaseUrl() ?: flowOf(
                                            UserPreferences.DEFAULT_API_BASE_URL,
                                        )
                                    },
                                    saveApiBaseUrl = { url ->
                                        userPreferences?.setApiBaseUrl(url)
                                    },
                                )
                            }
                            LoginScreen(viewModel = viewModel, onNavigateToCatalog = {})
                        },
                        authenticated = { SubSlothNavHost() },
                    )
                }
            }
        }
    }
}
