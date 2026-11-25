package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val FolioShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun FolioAlbumTheme(
    themeMode: ThemeMode = ThemeMode.LIQUID_GLASS,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = colorSchemeForTheme(themeMode)
    val isLiquid = themeMode == ThemeMode.LIQUID_GLASS || themeMode == ThemeMode.GLASS_25D ||
        themeMode == ThemeMode.LIQUID_GLASS_NIGHT || themeMode == ThemeMode.ARCANE_GLASS

    val tokens = FolioVisualTokens(
        themeMode = themeMode,
        reducedMotion = reducedMotion,
        cardElevation = when (themeMode) {
            ThemeMode.MINIMAL -> 0.dp
            ThemeMode.LIQUID_GLASS, ThemeMode.GLASS_25D, ThemeMode.LIQUID_GLASS_NIGHT, ThemeMode.ARCANE_GLASS -> 4.dp
            else -> 1.dp
        },
        panelAlpha = when {
            themeMode == ThemeMode.LIQUID_GLASS -> 0.72f
            themeMode == ThemeMode.GLASS_25D -> 0.68f
            themeMode == ThemeMode.LIQUID_GLASS_NIGHT -> 0.62f
            themeMode == ThemeMode.ARCANE_GLASS -> 0.64f
            else -> 0.94f
        },
        borderColor = when {
            themeMode == ThemeMode.LIQUID_GLASS -> Color.White.copy(alpha = 0.78f)
            themeMode == ThemeMode.GLASS_25D -> Color.White.copy(alpha = 0.22f)
            themeMode == ThemeMode.LIQUID_GLASS_NIGHT -> Color.White.copy(alpha = 0.20f)
            themeMode == ThemeMode.ARCANE_GLASS -> Color(0xFFE3B964).copy(alpha = 0.38f)
            themeMode == ThemeMode.MINIMAL -> colorScheme.outline.copy(alpha = 0.55f)
            themeMode.isDark -> colorScheme.outline.copy(alpha = 0.6f)
            else -> colorScheme.outline.copy(alpha = 0.65f)
        },
        dockContainerColor = when {
            themeMode == ThemeMode.LIQUID_GLASS -> Color(0xFFFFFFFF).copy(alpha = 0.74f)
            themeMode == ThemeMode.GLASS_25D -> Color(0xFF191D26).copy(alpha = 0.74f)
            themeMode == ThemeMode.LIQUID_GLASS_NIGHT -> Color(0xFF0E131C).copy(alpha = 0.70f)
            themeMode == ThemeMode.ARCANE_GLASS -> Color(0xFF150F28).copy(alpha = 0.72f)
            themeMode.isDark -> colorScheme.surface.copy(alpha = 0.90f)
            else -> colorScheme.surface.copy(alpha = 0.92f)
        },
        isElevatedDark = (themeMode == ThemeMode.GLASS_25D || themeMode == ThemeMode.DARK || themeMode == ThemeMode.LIQUID_GLASS_NIGHT || themeMode == ThemeMode.ARCANE_GLASS) && !reducedMotion,
        isLiquidGlass = isLiquid,
        specularRimColor = if (themeMode.isDark) {
            Color.White.copy(alpha = 0.34f)
        } else {
            Color.White.copy(alpha = 0.92f)
        },
        glassHighlightColor = if (themeMode.isDark) {
            Color.White.copy(alpha = 0.14f)
        } else {
            Color.White.copy(alpha = 0.52f)
        },
        tileRadius = 15.dp,
        cardRadius = 20.dp
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !themeMode.isDark
                    isAppearanceLightNavigationBars = !themeMode.isDark
                }
            }
        }
    }

    CompositionLocalProvider(LocalFolioTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = FolioShapes,
            content = content
        )
    }
}
