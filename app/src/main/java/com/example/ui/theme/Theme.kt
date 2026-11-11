package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val KrotyColorScheme = darkColorScheme(
    primary = KrotyCyan,
    secondary = KrotyEmerald,
    tertiary = KrotyAmber,
    background = KrotyDarkBg,
    surface = KrotyCardBg,
    onPrimary = KrotyDarkBg,
    onSecondary = KrotyDarkBg,
    onBackground = KrotyTextPrimary,
    onSurface = KrotyTextPrimary,
    error = KrotyCoralRed
)

@Composable
fun KrotyNetTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = KrotyColorScheme,
            content = content
        )
    }
}
