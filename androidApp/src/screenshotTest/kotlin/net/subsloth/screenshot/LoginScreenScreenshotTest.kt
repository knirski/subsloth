package net.subsloth.screenshot

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.subsloth.auth.LoginFormContent
import net.subsloth.core.ui.theme.SubSlothTheme

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Light", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Light", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Light", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun LoginScreenLightScreenshot() {
    SubSlothTheme(darkTheme = false) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://localhost:8080/api/v2/",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }
    }
}

@Suppress("ktlint:standard:max-line-length")
@PreviewTest
@Preview(name = "Phone Dark", device = "spec:width=411dp,height=731dp,dpi=420", showBackground = true)
@Preview(name = "Tablet Dark", device = "spec:width=800dp,height=1280dp,dpi=320", showBackground = true)
@Preview(name = "TV Dark", device = "spec:width=960dp,height=540dp,dpi=320", showBackground = true)
@Composable
fun LoginScreenDarkScreenshot() {
    SubSlothTheme(darkTheme = true) {
        Surface(modifier = Modifier.fillMaxSize()) {
            LoginFormContent(
                login = "",
                password = "",
                apiBaseUrl = "http://localhost:8080/api/v2/",
                isLoading = false,
                error = null,
                hasOfflineLibrary = false,
            )
        }
    }
}
