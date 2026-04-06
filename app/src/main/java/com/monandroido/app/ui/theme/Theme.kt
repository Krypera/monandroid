package com.monandroido.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF9A3412),
    onPrimary = Color.White,
    secondary = Color(0xFF14532D),
    onSecondary = Color.White,
    tertiary = Color(0xFF0F766E),
    background = Color(0xFFF6F1E8),
    surface = Color(0xFFFFFBF5),
    surfaceVariant = Color(0xFFE9DDC9),
    onSurface = Color(0xFF1F2937),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF97316),
    secondary = Color(0xFF4ADE80),
    tertiary = Color(0xFF5EEAD4),
    background = Color(0xFF161616),
    surface = Color(0xFF1F1B16),
    surfaceVariant = Color(0xFF2E261F),
    onSurface = Color(0xFFF6F1E8),
)

@Composable
fun MonandroidoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
