package org.nmapremake.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val lightColors = lightColorScheme(
    primary = Color(0xFF00639B),
    secondary = Color(0xFF51606F),
    tertiary = Color(0xFF655D5F),
)

private val darkColors = darkColorScheme(
    primary = Color(0xFF9ACBFF),
    secondary = Color(0xFFBAC8D6),
    tertiary = Color(0xFFE4BCC1),
)

@Composable
fun NmapRemakeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColors else lightColors,
        content = content,
    )
}
