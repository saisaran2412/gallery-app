package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalFolioTokens
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

internal val MagicBase = listOf(
    Color(0xFFFF6BA8), Color(0xFFFF9F5A), Color(0xFFB57BFF), Color(0xFF5AA8FF), Color(0xFF4FE3D1)
)

/** Samples the colour ring starting at [phase] (0..5) so the gradient appears to rotate smoothly. */
internal fun ring(phase: Float, steps: Int = 12): List<Color> {
    val n = MagicBase.size
    return List(steps + 1) { j ->
        val pos = ((j.toFloat() / steps) * n + phase) % n
        val i = floor(pos).toInt() % n
        val f = pos - floor(pos)
        lerp(MagicBase[i], MagicBase[(i + 1) % n], f)
    }
}

/**
 * Apple-Intelligence style processing layer: a rotating aurora glow around the edge, a soft
 * shimmer sweep, drifting sparkles and a cycling status pill. Blocks touches while visible.
 */
@Composable
fun MagicProcessingOverlay(
    visible: Boolean,
    labels: List<String>,
    modifier: Modifier = Modifier
) {
    val reduced = LocalFolioTokens.current.reducedMotion
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(350)),
        exit = fadeOut(tween(450)),
        modifier = modifier
    ) {
        val infinite = rememberInfiniteTransition(label = "magic")
        val phase by infinite.animateFloat(
            0f, MagicBase.size.toFloat(),
            infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "phase"
        )
        val t by infinite.animateFloat(
            0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "t"
        )
        val sweep by infinite.animateFloat(
            -0.6f, 1.6f,
            infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "sweep"
        )
        val pulse by infinite.animateFloat(
            0.88f, 1.12f,
            infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulse"
        )
        val sparkles = remember {
            val r = Random(11)
            List(18) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) }
        }
        var idx by remember { mutableIntStateOf(0) }
        LaunchedEffect(labels) {
            idx = 0
            while (labels.size > 1) {
                delay(1700)
                idx = (idx + 1) % labels.size
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                // calm scrim
                drawRect(Color.Black.copy(alpha = 0.22f))

                // aurora edge glow
                val brush = Brush.sweepGradient(ring(if (reduced) 0f else phase), center)
                val inset = 4.dp.toPx()
                val corner = CornerRadius(22.dp.toPx())
                val tl = Offset(inset, inset)
                val sz = Size(size.width - inset * 2, size.height - inset * 2)
                listOf(44f to 0.10f, 24f to 0.20f, 10f to 0.45f, 3.5f to 1f).forEach { (w, a) ->
                    drawRoundRect(brush, tl, sz, corner, alpha = a, style = Stroke(width = w * density / 2.75f))
                }

                if (!reduced) {
                    // shimmer sweep
                    val x = size.width * sweep
                    val band = size.width * 0.35f
                    drawRect(
                        Brush.linearGradient(
                            colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.20f), Color.Transparent),
                            start = Offset(x - band, 0f),
                            end = Offset(x + band, size.height * 0.6f)
                        )
                    )
                    // sparkles
                    sparkles.forEachIndexed { i, (sx, sy, sp) ->
                        val yy = ((sy - t * (0.10f + 0.05f * (i % 3))) % 1f + 1f) % 1f
                        val tw = 0.5f + 0.5f * sin(2f * PI.toFloat() * (t * 3f + sp))
                        val c = Offset(sx * size.width, yy * size.height)
                        val rad = (1.6f + 2.6f * sp) * density
                        drawCircle(MagicBase[i % MagicBase.size].copy(alpha = 0.22f * tw), rad * 3.2f, c)
                        drawCircle(Color.White.copy(alpha = 0.85f * tw), rad * 0.55f, c)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.42f))
                    .padding(horizontal = 18.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            val s = if (reduced) 1f else pulse
                            scaleX = s; scaleY = s
                        }
                )
                Box(Modifier.width(10.dp))
                Crossfade(targetState = idx, animationSpec = tween(450), label = "label") { i ->
                    Text(
                        labels.getOrElse(i) { "" },
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** Slim glowing progress bar with a travelling shine; calm when [active] is false (paused). */
@Composable
fun MagicProgressBar(progress: Float, active: Boolean, modifier: Modifier = Modifier) {
    val reduced = LocalFolioTokens.current.reducedMotion
    val infinite = rememberInfiniteTransition(label = "mpb")
    val phase by infinite.animateFloat(
        0f, MagicBase.size.toFloat(),
        infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "p"
    )
    val shine by infinite.animateFloat(
        -0.3f, 1.3f,
        infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing)), label = "s"
    )
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val h = size.height
        drawRoundRect(track, size = size, cornerRadius = CornerRadius(h / 2))
        val w = size.width * progress.coerceIn(0f, 1f)
        if (w > 1f) {
            val animate = active && !reduced
            val colors = ring(if (animate) phase else 0f, 8)
            val brush = Brush.horizontalGradient(colors, startX = 0f, endX = size.width.coerceAtLeast(1f))
            // soft glow underneath
            drawRoundRect(brush, size = Size(w, h), cornerRadius = CornerRadius(h / 2), alpha = if (active) 0.35f else 0.15f,
                style = Stroke(width = h * 1.8f))
            drawRoundRect(brush, size = Size(w, h), cornerRadius = CornerRadius(h / 2), alpha = if (active) 1f else 0.55f)
            if (animate) {
                val sx = w * shine
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startX = sx - w * 0.2f, endX = sx + w * 0.2f
                    ),
                    size = Size(w, h), cornerRadius = CornerRadius(h / 2)
                )
            }
        }
    }
}
