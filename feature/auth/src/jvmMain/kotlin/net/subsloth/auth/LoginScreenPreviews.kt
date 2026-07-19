package net.subsloth.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

// Previews
@Preview
@Composable
private fun LoginFormPreview() {
    LoginFormContent(
        login = "",
        password = "",
        apiBaseUrl = "http://localhost:8080/api/v2/",
        isLoading = false,
        error = null,
        hasOfflineLibrary = false,
    )
}
