package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkPalette = darkColorScheme(
    primary = CyanAccent,
    secondary = EmeraldSuccess,
    tertiary = AmberWarning,
    background = NavyDarkBg,
    surface = NavyCardBg,
    onPrimary = Color.Black,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

private val AmoledPalette = darkColorScheme(
    primary = CyanAccent,
    secondary = EmeraldSuccess,
    tertiary = AmberWarning,
    background = Color.Black,
    surface = Color(0xFF0A0F1A),
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightPalette = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF059669),
    background = Color(0xFFF8FAFC),
    surface = Color.White
)

@Composable
fun KrotyNetTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val scheme = when (themeMode) {
        "AMOLED" -> AmoledPalette
        "LIGHT" -> LightPalette
        else -> DarkPalette
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
