package net.subsloth.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * SubSloth theme that automatically selects dark or light mode
 * based on the system-wide theme setting.
 *
 * This composable works on all KMP targets (Android, Desktop, Web).
 * On platforms that support it, [isSystemInDarkTheme] follows
 * the OS-level dark/light preference.
 *
 * @param darkTheme Whether to use the dark color scheme.
 *   Defaults to the system dark/light preference.
 * @param content The composable content to theme.
 */
@Composable
fun SubSlothTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
