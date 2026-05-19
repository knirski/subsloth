package net.subsloth.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.dropUnlessResumed
import net.subsloth.feature.auth.R

/**
 * Login screen with standard Autofill/password-manager support.
 *
 * Supports phone, tablet, and TV via Material3 adaptive layout.
 * No custom clipboard behavior is implemented.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    modifier: Modifier = Modifier,
    onNavigateToOfflineLibrary: () -> Unit = {},
    onNavigateToCatalog: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val currentOnNavigateToCatalog by rememberUpdatedState(onNavigateToCatalog)
    LaunchedEffect(uiState) {
        if (uiState is LoginUiState.LoggedIn) {
            currentOnNavigateToCatalog()
        }
    }

    when (uiState) {
        is LoginUiState.AuthRepair -> {
            /* AuthRepairScreen is rendered by the navigation layer.
               LoginScreen shows nothing until the user returns to login. */
        }
        is LoginUiState.LoggedIn -> {
            /* Navigation effect above handles routing to catalog. */
        }
        else -> {
            val formState = when (val s = uiState) {
                is LoginUiState.LoginForm -> s
                else -> LoginUiState.LoginForm()
            }
            val isLoading = uiState is LoginUiState.Loading

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.app_title),
                    style = MaterialTheme.typography.headlineLarge,
                )

                Spacer(modifier = Modifier.height(32.dp))

                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text(stringResource(id = R.string.login_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(id = R.string.password_label)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading,
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = dropUnlessResumed { viewModel.login(login, password) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = login.isNotBlank() && password.isNotBlank(),
                    ) {
                        Text(stringResource(id = R.string.sign_in))
                    }
                }

                formState.error?.let { error ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (formState.hasOfflineLibrary) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = dropUnlessResumed { onNavigateToOfflineLibrary() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(id = R.string.offline_library))
                    }
                }
            }
        }
    }
}

/**
 * Auth repair screen shown when credentials expire or unexpected service
 * state is returned (redirect, HTML, non-JSON).
 */
@Composable
fun AuthRepairScreen(viewModel: LoginViewModel, modifier: Modifier = Modifier, onDismiss: () -> Unit = {}) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(id = R.string.session_expired),
            style = MaterialTheme.typography.headlineSmall,
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = stringResource(id = R.string.session_expired_message),
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { viewModel.dismissNeedsAuthRepair() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(id = R.string.sign_in_again))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(id = R.string.cancel))
        }
    }
}
