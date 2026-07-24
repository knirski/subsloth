package net.subsloth.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import subsloth.feature.auth.generated.resources.Res
import subsloth.feature.auth.generated.resources.session_expired
import subsloth.feature.auth.generated.resources.session_expired_message
import subsloth.feature.auth.generated.resources.sign_in_again

/**
 * Auth repair screen shown when a session has expired or the service
 * returned an unexpected response (redirect, HTML, non-JSON) that the
 * [LoginViewModel] surfaces as [LoginUiState.AuthRepair].
 *
 * Only renders meaningfully while [LoginViewModel.uiState] is
 * [LoginUiState.AuthRepair]; any other state renders nothing, since
 * this screen's only job is the repair prompt, not the ordinary login
 * form (that's [LoginScreen]/[LoginFormContent]).
 *
 * Design choice: rather than re-collecting fresh credentials inline,
 * this screen offers the simpler "you've been signed out, sign in
 * again" prompt. [LoginViewModel] has no "retry with the previously
 * stored credentials" action — [LoginViewModel.login] always needs a
 * fresh login/password pair — so a lightweight re-entry form here would
 * just duplicate [LoginFormContent] for no benefit. Clicking "Sign In
 * Again" calls [LoginViewModel.dismissNeedsAuthRepair] (returning the
 * view model to [LoginUiState.LoginForm]) and then [onRepaired], which
 * lets the caller decide what "repaired" means for its navigation
 * context (e.g. popping this screen off the back stack).
 */
@Composable
fun AuthRepairScreen(viewModel: LoginViewModel, onRepaired: () -> Unit, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (uiState) {
        is LoginUiState.AuthRepair -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(Res.string.session_expired),
                    style = MaterialTheme.typography.headlineSmall,
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = stringResource(Res.string.session_expired_message),
                    style = MaterialTheme.typography.bodyMedium,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        viewModel.dismissNeedsAuthRepair()
                        onRepaired()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.sign_in_again))
                }
            }
        }

        else -> {
            // Nothing to repair (or the view model hasn't reached
            // AuthRepair yet) — render nothing rather than a login form
            // that duplicates LoginScreen's responsibility.
        }
    }
}
