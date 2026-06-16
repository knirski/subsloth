package net.subsloth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import net.subsloth.auth.LoginScreen
import net.subsloth.auth.LoginViewModel
import net.subsloth.core.domain.port.InMemorySessionState
import net.subsloth.core.domain.port.SessionPort
import net.subsloth.core.ui.SessionGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val sessionPort: SessionPort = remember { InMemorySessionState() }
                    SessionGate(
                        sessionPort = sessionPort,
                        login = {
                            val viewModel: LoginViewModel = viewModel {
                                LoginViewModel(sessionPort = sessionPort)
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
