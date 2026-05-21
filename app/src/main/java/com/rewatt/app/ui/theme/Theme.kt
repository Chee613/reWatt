package com.rewatt.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0E7C66),
    onPrimary = Color.White,
    secondary = Color(0xFF246A9B),
    onSecondary = Color.White,
    tertiary = Color(0xFFB35C23),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE1E8E5),
    error = Color(0xFFB3261E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF69D7BF),
    secondary = Color(0xFF8EC7F1),
    tertiary = Color(0xFFFFB782),
    background = Color(0xFF101413),
    surface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFF38433F),
    error = Color(0xFFFFB4AB),
)

@Composable
fun ReWattTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

