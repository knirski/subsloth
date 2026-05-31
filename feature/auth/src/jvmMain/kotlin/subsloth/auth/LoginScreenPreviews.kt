package subsloth.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Previews
@Preview
@Composable
private fun LoginFormPreview() {
    LoginFormContent(
        login = "",
        password = "",
        isLoading = false,
        error = null,
        hasOfflineLibrary = false,
    )
}
