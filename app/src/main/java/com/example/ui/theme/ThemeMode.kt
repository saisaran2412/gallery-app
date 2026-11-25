package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Curated visual themes for Folio Album:
 * - Liquid Glass & Studio Smoke bring Apple-inspired translucent glass optics and specular rims.
 * - Warm Studio Light & Obsidian Dark anchor the signature gallery aesthetic.
 */
enum class ThemeMode(
    val displayName: String,
    val isDark: Boolean,
    val previewPrimary: Color,
    val previewBackground: Color
) {
    LIQUID_GLASS("Liquid Glass", false, Color(0xFF0A84FF), Color(0xFFEEF3FA)),
    LIGHT("Warm Studio", false, Color(0xFFD97724), Color(0xFFFAF8F5)),
    DARK("Obsidian", true, Color(0xFFEA9445), Color(0xFF0A0A0C)),
    AMOLED_BLACK("AMOLED Black", true, Color(0xFFF09D4F), Color(0xFF000000)),
    WARM("Terracotta", false, Color(0xFFB85430), Color(0xFFFAF6F0)),
    COOL("Arctic", false, Color(0xFF2B6CB0), Color(0xFFF3F7FB)),
    BLUE("Midnight", true, Color(0xFF7CA7F8), Color(0xFF0F1724)),
    GREEN("Sage", false, Color(0xFF2D6A4F), Color(0xFFF4F8F5)),
    PURPLE("Plum", true, Color(0xFFC2A7EB), Color(0xFF15121B)),
    MINIMAL("Paper", false, Color(0xFF18181B), Color(0xFFFFFFFF)),
    GLASS_25D("Obsidian Glass", true, Color(0xFFE59852), Color(0xFF12151C)),
    LIQUID_GLASS_NIGHT("Liquid Glass Night", true, Color(0xFF64A8FF), Color(0xFF05070C)),
    ARCANE_GLASS("Arcane Glass", true, Color(0xFFE3B964), Color(0xFF0B0716))
}

@Immutable
data class FolioVisualTokens(
    val themeMode: ThemeMode = ThemeMode.LIQUID_GLASS,
    val reducedMotion: Boolean = false,
    val cardElevation: Dp = 1.dp,
    val panelAlpha: Float = 0.78f,
    val borderColor: Color = Color(0xFFDCE3EE),
    val dockContainerColor: Color = Color(0xCCFFFFFF),
    val isElevatedDark: Boolean = false,
    val isLiquidGlass: Boolean = true,
    val specularRimColor: Color = Color.White.copy(alpha = 0.85f),
    val glassHighlightColor: Color = Color.White.copy(alpha = 0.45f),
    val tileRadius: Dp = 16.dp,
    val cardRadius: Dp = 20.dp
)

val LocalFolioTokens = staticCompositionLocalOf { FolioVisualTokens() }
