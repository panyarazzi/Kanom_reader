package com.nextz.kanomtts.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// --- Anchan & Coconut raw palette (spec §6.1) ---
val Anchan = Color(0xFF5D63D6)            // primary (butterfly-pea blue)
val AnchanContainer = Color(0xFFEBECFB)   // primary container / light tint
val AnchanGradientEnd = Color(0xFF7B82F0)
val RoseHighlight = Color(0xFFFF5E9C)     // karaoke highlight — RESERVED
val RoseHighlightBg = Color(0xFFFFE0EC)
val RoseHighlightInk = Color(0xFF9E2B5E)
val CoconutCream = Color(0xFFFBF7EF)      // background
val SurfaceWhite = Color(0xFFFFFFFF)
val Ink = Color(0xFF2B2A33)               // on-surface
val Muted = Color(0xFFA79E8E)
// reader surfaces
val SepiaBg = Color(0xFFF4ECD8)
val SepiaInk = Color(0xFF40382A)
val TrueBlack = Color(0xFF000000)
val DarkBg = Color(0xFF15141A)
val DarkInk = Color(0xFFEAE7F0)

val LightColors = lightColorScheme(
    primary = Anchan,
    onPrimary = Color.White,
    primaryContainer = AnchanContainer,
    onPrimaryContainer = Color(0xFF1B1B4D),
    secondary = Color(0xFF8A8FF0),
    onSecondary = Color.White,
    background = CoconutCream,
    onBackground = Ink,
    surface = SurfaceWhite,
    onSurface = Ink,
    surfaceVariant = AnchanContainer,
    onSurfaceVariant = Color(0xFF565260),
    outline = Color(0xFFCFC8BB),
)

val DarkColors = darkColorScheme(
    primary = Color(0xFFB9BCFF),
    onPrimary = Color(0xFF222657),
    primaryContainer = Color(0xFF3D4290),
    onPrimaryContainer = AnchanContainer,
    secondary = Color(0xFFB9BCFF),
    onSecondary = Color(0xFF222657),
    background = DarkBg,
    onBackground = DarkInk,
    surface = Color(0xFF1E1D26),
    onSurface = DarkInk,
    surfaceVariant = Color(0xFF45424F),
    onSurfaceVariant = Color(0xFFC9C5D4),
    outline = Color(0xFF938F9E),
)
