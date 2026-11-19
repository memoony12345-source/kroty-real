package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val GlassDark = darkColorScheme(
    primary = AuroraCyan,
    onPrimary = Color(0xFF05121A),
    primaryContainer = Color(0x3322D3EE),
    onPrimaryContainer = Color(0xFFBDF3FF),
    secondary = AuroraViolet,
    onSecondary = Color(0xFF130A24),
    secondaryContainer = Color(0x338B5CF6),
    onSecondaryContainer = Color(0xFFE7DBFF),
    tertiary = AuroraEmerald,
    onTertiary = Color(0xFF04130D),
    tertiaryContainer = Color(0x3334D399),
    onTertiaryContainer = Color(0xFFC7FBE3),
    error = AuroraRose,
    onError = Color(0xFF1B0509),
    background = AuroraVoid,
    onBackground = InkPrimary,
    // Frosted panels: one layer of white over the living background.
    surface = GlassSurface,
    onSurface = InkPrimary,
    surfaceVariant = GlassSurfaceMid,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color(0x0DFFFFFF),
    surfaceContainerLow = Color(0x14FFFFFF),
    surfaceContainer = Color(0x1AFFFFFF),
    surfaceContainerHigh = Color(0x24FFFFFF),
    surfaceContainerHighest = Color(0x2EFFFFFF),
    outline = GlassBorder,
    outlineVariant = GlassBorderSoft,
    scrim = Color(0xCC02040A),
    inverseSurface = Color(0xFFEDF1FA),
    inverseOnSurface = Color(0xFF10182B)
)

private val GlassAmoled = darkColorScheme(
    primary = AuroraCyan,
    onPrimary = Color(0xFF00121A),
    primaryContainer = Color(0x3322D3EE),
    onPrimaryContainer = Color(0xFFBDF3FF),
    secondary = AuroraViolet,
    onSecondary = Color(0xFF130A24),
    secondaryContainer = Color(0x2E8B5CF6),
    onSecondaryContainer = Color(0xFFE7DBFF),
    tertiary = AuroraEmerald,
    onTertiary = Color(0xFF04130D),
    error = AuroraRose,
    onError = Color(0xFF1B0509),
    background = Color.Black,
    onBackground = Color(0xFFF7FAFF),
    surface = Color(0x12FFFFFF),
    onSurface = Color(0xFFF7FAFF),
    surfaceVariant = Color(0x1CFFFFFF),
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = Color(0x0AFFFFFF),
    surfaceContainerLow = Color(0x12FFFFFF),
    surfaceContainer = Color(0x18FFFFFF),
    surfaceContainerHigh = Color(0x22FFFFFF),
    surfaceContainerHighest = Color(0x2CFFFFFF),
    outline = GlassBorder,
    outlineVariant = GlassBorderSoft,
    scrim = Color(0xE6000000)
)

private val GlassLight = lightColorScheme(
    primary = Color(0xFF0E7490),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6F4FB),
    onPrimaryContainer = Color(0xFF053845),
    secondary = Color(0xFF6D28D9),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDE4FF),
    onSecondaryContainer = Color(0xFF2A1060),
    tertiary = Color(0xFF047857),
    onTertiary = Color.White,
    error = Color(0xFFDC2626),
    onError = Color.White,
    background = Color(0xFFF3F6FD),
    onBackground = Color(0xFF101828),
    surface = Color(0xB3FFFFFF),
    onSurface = Color(0xFF101828),
    surfaceVariant = Color(0x99FFFFFF),
    onSurfaceVariant = Color(0xFF4A5872),
    surfaceContainerLowest = Color(0xCCFFFFFF),
    surfaceContainerLow = Color(0x99FFFFFF),
    surfaceContainer = Color(0x8AFFFFFF),
    surfaceContainerHigh = Color(0xB3FFFFFF),
    surfaceContainerHighest = Color(0xCCFFFFFF),
    outline = Color(0x33000000),
    outlineVariant = Color(0x14000000)
)

private val KrotyShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

private val KrotyTypography = Typography(
    headlineSmall = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp)
)

@Composable
fun KrotyNetTheme(
    themeMode: String = "DARK",
    content: @Composable () -> Unit
) {
    val scheme = when (themeMode) {
        "AMOLED" -> GlassAmoled
        "LIGHT" -> GlassLight
        else -> GlassDark
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = KrotyShapes,
        typography = KrotyTypography,
        content = content
    )
}
