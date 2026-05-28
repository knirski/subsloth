package net.subsloth.auth

import android.content.res.Configuration
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import net.subsloth.core.model.error.UiError
import net.subsloth.core.ui.toDisplayStringRes
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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var login by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    val currentOnNavigateToCatalog by rememberUpdatedState(onNavigateToCatalog)
    val onSignIn = remember(viewModel) { { viewModel.login(login, password) } }

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
            LoginFormContent(
                login = login,
                password = password,
                isLoading = uiState is LoginUiState.Loading,
                error = formState.error,
                hasOfflineLibrary = formState.hasOfflineLibrary,
                modifier = modifier,
                onLoginChange = { login = it },
                onPasswordChange = { password = it },
                onSignIn = onSignIn,
                onNavigateToOfflineLibrary = onNavigateToOfflineLibrary,
            )
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

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Preview(showBackground = true, fontScale = 1.5f)
@Composable
private fun LoginFormPreview() {
    LoginFormContent(
        login = "",
        password = "",
        isLoading = false,
        error = null,
        hasOfflineLibrary = false,
        onLoginChange = {},
        onPasswordChange = {},
        onSignIn = {},
        onNavigateToOfflineLibrary = {},
    )
}

@Composable
internal fun LoginFormContent(
    login: String,
    password: String,
    isLoading: Boolean,
    error: UiError?,
    hasOfflineLibrary: Boolean,
    modifier: Modifier = Modifier,
    onLoginChange: (String) -> Unit = {},
    onPasswordChange: (String) -> Unit = {},
    onSignIn: () -> Unit = {},
    onNavigateToOfflineLibrary: () -> Unit = {},
) {
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
            onValueChange = onLoginChange,
            label = { Text(stringResource(id = R.string.login_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading,
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
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
                onClick = dropUnlessResumed { onSignIn() },
                modifier = Modifier.fillMaxWidth(),
                enabled = login.isNotBlank() && password.isNotBlank(),
            ) {
                Text(stringResource(id = R.string.sign_in))
            }
        }

        error?.let { err ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(err.toDisplayStringRes()),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        if (hasOfflineLibrary) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onNavigateToOfflineLibrary,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = R.string.offline_library))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AuthRepairPreview() {
    Column(
        modifier = Modifier
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
    }
}
