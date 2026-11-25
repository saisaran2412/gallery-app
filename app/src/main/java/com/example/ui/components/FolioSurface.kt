package com.example.ui.components

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFolioTokens
import kotlin.math.roundToInt

/**
 * Draws realistic Apple-style Liquid Glass optical sheen + diagonal specular rim refraction.
 */
fun Modifier.liquidGlassRim(
    cornerRadius: Dp = 24.dp,
    specularTopLeft: Color,
    specularBottomRight: Color,
    sheenColor: Color
): Modifier = this.drawWithCache {
    val radiusPx = cornerRadius.toPx()
    val corner = CornerRadius(radiusPx, radiusPx)
    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            sheenColor,
            Color.Transparent
        ),
        startY = 0f,
        endY = size.height * 0.55f
    )
    val rimBrush = Brush.linearGradient(
        colors = listOf(
            specularTopLeft,
            specularTopLeft.copy(alpha = specularTopLeft.alpha * 0.25f),
            specularBottomRight
        ),
        start = Offset.Zero,
        end = Offset(size.width, size.height)
    )
    onDrawWithContent {
        drawContent()
        drawRoundRect(
            brush = sheenBrush,
            cornerRadius = corner
        )
        drawRoundRect(
            brush = rimBrush,
            cornerRadius = corner,
            style = Stroke(width = 1.25.dp.toPx())
        )
    }
}

@Composable
fun FolioAtmosphereBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val tokens = LocalFolioTokens.current
    val cs = MaterialTheme.colorScheme
    val isDark = tokens.themeMode.isDark

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(cs.background)
    ) {
        // Radial gradients are already soft; skipping the full-screen RenderEffect blur saves GPU every frame.
        if (tokens.isLiquidGlass && !tokens.reducedMotion) {
            Canvas(
                modifier = Modifier.fillMaxSize()
            ) {
                val arcane = tokens.themeMode == com.example.ui.theme.ThemeMode.ARCANE_GLASS
                val primaryGlow = if (arcane) {
                    Color(0xFF7A4DFF).copy(alpha = 0.34f)
                } else if (isDark) {
                    Color(0xFF1D4E89).copy(alpha = 0.28f)
                } else {
                    Color(0xFF91C8FF).copy(alpha = 0.34f)
                }
                val warmCaustic = if (arcane) {
                    Color(0xFFE3B964).copy(alpha = 0.24f)
                } else if (isDark) {
                    Color(0xFFE59852).copy(alpha = 0.16f)
                } else {
                    Color(0xFFFFD8B0).copy(alpha = 0.38f)
                }
                val crystalHighlight = if (isDark) {
                    Color(0xFF283449).copy(alpha = 0.35f)
                } else {
                    Color.White.copy(alpha = 0.72f)
                }

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.18f, size.height * 0.14f),
                        radius = size.width * 0.65f
                    ),
                    center = Offset(size.width * 0.18f, size.height * 0.14f),
                    radius = size.width * 0.65f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(warmCaustic, Color.Transparent),
                        center = Offset(size.width * 0.86f, size.height * 0.28f),
                        radius = size.width * 0.55f
                    ),
                    center = Offset(size.width * 0.86f, size.height * 0.28f),
                    radius = size.width * 0.55f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(crystalHighlight, Color.Transparent),
                        center = Offset(size.width * 0.50f, size.height * 0.82f),
                        radius = size.width * 0.62f
                    ),
                    center = Offset(size.width * 0.50f, size.height * 0.82f),
                    radius = size.width * 0.62f
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

@Composable
fun FolioDepthCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    containerColor: Color? = null,
    elevation: Dp? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val tokens = LocalFolioTokens.current
    val cs = MaterialTheme.colorScheme
    val baseColor = containerColor ?: cs.surface
    val resolvedColor = if (tokens.isLiquidGlass && containerColor == null) {
        baseColor.copy(alpha = tokens.panelAlpha)
    } else {
        baseColor
    }
    val resolvedElevation = elevation ?: tokens.cardElevation
    val border = BorderStroke(1.dp, tokens.borderColor)
    val glassMod = if (tokens.isLiquidGlass) {
        Modifier.liquidGlassRim(
            cornerRadius = tokens.cardRadius,
            specularTopLeft = tokens.specularRimColor,
            specularBottomRight = tokens.borderColor.copy(alpha = 0.45f),
            sheenColor = tokens.glassHighlightColor.copy(alpha = 0.35f)
        )
    } else Modifier

    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.then(glassMod),
            shape = shape,
            color = resolvedColor,
            tonalElevation = resolvedElevation,
            shadowElevation = if (tokens.isLiquidGlass || tokens.isElevatedDark) resolvedElevation else 0.dp,
            border = if (tokens.isLiquidGlass) null else border,
            content = content
        )
    } else {
        Surface(
            modifier = modifier.then(glassMod),
            shape = shape,
            color = resolvedColor,
            tonalElevation = resolvedElevation,
            shadowElevation = if (tokens.isLiquidGlass || tokens.isElevatedDark) resolvedElevation else 0.dp,
            border = if (tokens.isLiquidGlass) null else border,
            content = content
        )
    }
}

/**
 * Liquid Glass pill search bar + circular favorites shortcut button.
 */
@Composable
fun FolioSearchPillRow(
    placeholder: String,
    onSearchClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    modifier: Modifier = Modifier,
    searchTag: String = "albums_search_pill",
    favoritesTag: String = "albums_favorites_shortcut"
) {
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current
    val pillColor = if (tokens.isLiquidGlass) tokens.dockContainerColor else cs.surface

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            onClick = onSearchClick,
            shape = RoundedCornerShape(50),
            color = pillColor,
            border = if (tokens.isLiquidGlass) null else BorderStroke(1.dp, tokens.borderColor),
            tonalElevation = 2.dp,
            shadowElevation = if (tokens.isLiquidGlass) 6.dp else 0.dp,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .then(
                    if (tokens.isLiquidGlass) {
                        Modifier.liquidGlassRim(
                            cornerRadius = 24.dp,
                            specularTopLeft = tokens.specularRimColor,
                            specularBottomRight = tokens.borderColor.copy(alpha = 0.5f),
                            sheenColor = tokens.glassHighlightColor
                        )
                    } else Modifier
                )
                .testTag(searchTag)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Surface(
            onClick = onFavoritesClick,
            shape = CircleShape,
            color = cs.primaryContainer.copy(alpha = if (tokens.isLiquidGlass) 0.84f else 1f),
            shadowElevation = if (tokens.isLiquidGlass) 6.dp else 0.dp,
            border = if (tokens.isLiquidGlass) null else BorderStroke(1.dp, cs.primary.copy(alpha = 0.25f)),
            modifier = Modifier
                .size(48.dp)
                .minimumInteractiveComponentSize()
                .then(
                    if (tokens.isLiquidGlass) {
                        Modifier.liquidGlassRim(
                            cornerRadius = 24.dp,
                            specularTopLeft = tokens.specularRimColor,
                            specularBottomRight = cs.primary.copy(alpha = 0.35f),
                            sheenColor = tokens.glassHighlightColor
                        )
                    } else Modifier
                )
                .testTag(favoritesTag)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
                    contentDescription = "Favorites",
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Tactile Liquid Glass pill segmented switch with spring transitions.
 */
@Composable
fun FolioSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (tokens.isLiquidGlass) tokens.dockContainerColor.copy(alpha = 0.65f)
                else cs.surfaceVariant
            )
            .then(
                if (tokens.isLiquidGlass) {
                    Modifier.liquidGlassRim(
                        cornerRadius = 24.dp,
                        specularTopLeft = tokens.specularRimColor,
                        specularBottomRight = tokens.borderColor.copy(alpha = 0.4f),
                        sheenColor = tokens.glassHighlightColor.copy(alpha = 0.35f)
                    )
                } else {
                    Modifier.border(1.dp, tokens.borderColor.copy(alpha = 0.7f), RoundedCornerShape(50))
                }
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val containerColor by animateColorAsState(
                targetValue = if (selected) {
                    if (tokens.isLiquidGlass) cs.surface.copy(alpha = 0.92f) else cs.surface
                } else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segment_bg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) cs.onSurface else cs.onSurfaceVariant,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segment_fg"
            )
            val itemScale by animateFloatAsState(
                targetValue = if (selected) 1f else 0.96f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                label = "segment_scale"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .graphicsLayer {
                        scaleX = itemScale
                        scaleY = itemScale
                    }
                    .clip(RoundedCornerShape(50))
                    .background(containerColor)
                    .then(
                        if (selected) {
                            Modifier.border(
                                1.dp,
                                tokens.specularRimColor.copy(alpha = 0.8f),
                                RoundedCornerShape(50)
                            )
                        } else Modifier
                    )
                    .clickable { onSelect(index) }
                    .testTag("${testTagPrefix}_$index"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

/**
 * Precision tick-mark dial slider inspired by the photo editor UI.
 */
@Composable
fun FolioRulerSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    formatValue: (Float) -> String = { v -> "${(v * 100f).roundToInt()}%" }
) {
    val cs = MaterialTheme.colorScheme
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val normalized = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    var currentNorm by remember(value, valueRange) { mutableFloatStateOf(normalized) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurface
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(cs.primaryContainer.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 2.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surfaceVariant.copy(alpha = 0.55f))
                .semantics {
                    contentDescription = "$label slider"
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = value,
                        range = valueRange
                    )
                }
                .pointerInput(valueRange) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            currentNorm = frac
                            onValueChange(valueRange.start + frac * span)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                            currentNorm = frac
                            onValueChange(valueRange.start + frac * span)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val activeColor = cs.primary
            val tickColor = cs.onSurfaceVariant.copy(alpha = 0.4f)
            val majorTickColor = cs.onSurfaceVariant.copy(alpha = 0.75f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp)
            ) {
                val tickCount = 29
                val stepX = size.width / (tickCount - 1).coerceAtLeast(1)
                val centerY = size.height * 0.62f

                for (i in 0 until tickCount) {
                    val x = i * stepX
                    val isMajor = i % 7 == 0
                    val tickHeight = if (isMajor) 14.dp.toPx() else 8.dp.toPx()
                    val tFrac = i.toFloat() / (tickCount - 1).toFloat()
                    val nearCursor = kotlin.math.abs(tFrac - currentNorm) < 0.045f
                    drawLine(
                        color = when {
                            nearCursor -> activeColor
                            isMajor -> majorTickColor
                            else -> tickColor
                        },
                        start = Offset(x, centerY - tickHeight / 2f),
                        end = Offset(x, centerY + tickHeight / 2f),
                        strokeWidth = if (isMajor || nearCursor) 2.dp.toPx() else 1.2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                val cursorX = currentNorm * size.width
                val triSize = 5.dp.toPx()
                val triangle = Path().apply {
                    moveTo(cursorX, 9.dp.toPx())
                    lineTo(cursorX - triSize, 3.dp.toPx())
                    lineTo(cursorX + triSize, 3.dp.toPx())
                    close()
                }
                drawPath(triangle, color = activeColor)
                drawLine(
                    color = activeColor,
                    start = Offset(cursorX, 11.dp.toPx()),
                    end = Offset(cursorX, size.height - 5.dp.toPx()),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
