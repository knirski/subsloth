package net.subsloth.auth

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable

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
