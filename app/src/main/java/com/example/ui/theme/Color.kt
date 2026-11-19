package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Aurora Glass — the app's design palette.
 *
 * A deep midnight canvas lit by slow aurora blobs behind frosted glass panels.
 * Everything is fully opaque at the bottom layer and semi-transparent white on
 * top, so cards let the aurora breathe through them.
 */

// ---- Canvas -----------------------------------------------------------------
val AuroraVoid = Color(0xFF05070F)
val AuroraNebula = Color(0xFF0B1224)
val AuroraHorizon = Color(0xFF111A33)

// ---- Aurora lights ----------------------------------------------------------
val AuroraViolet = Color(0xFF8B5CF6)
val AuroraIndigo = Color(0xFF6366F1)
val AuroraCyan = Color(0xFF22D3EE)
val AuroraTeal = Color(0xFF2DD4BF)
val AuroraEmerald = Color(0xFF34D399)
val AuroraRose = Color(0xFFFB7185)
val AuroraAmber = Color(0xFFFBBF24)
val AuroraSky = Color(0xFF38BDF8)

// ---- Frosted layers ---------------------------------------------------------
val GlassSurface = Color(0x14FFFFFF)
val GlassSurfaceMid = Color(0x1FFFFFFF)
val GlassSurfaceHigh = Color(0x2BFFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val GlassBorderSoft = Color(0x1AFFFFFF)

// ---- Ink --------------------------------------------------------------------
val InkPrimary = Color(0xFFF5F8FF)
val InkSecondary = Color(0xFFA9B7D8)
val InkMuted = Color(0xFF7C89AB)

// Backwards-compatible names still referenced by older screens.
val NavyDarkBg = AuroraVoid
val NavyCardBg = GlassSurface
val CyanAccent = AuroraCyan
val EmeraldSuccess = AuroraEmerald
val AmberWarning = AuroraAmber
val CrimsonError = AuroraRose
