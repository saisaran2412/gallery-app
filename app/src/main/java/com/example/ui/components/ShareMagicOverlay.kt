package com.example.ui.components

import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.storage.ShareAnimStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

enum class ShareRole { SEND, RECEIVE }

data class ShareAnimEvent(
    val id: Long,
    val role: ShareRole,
    val imageUri: String?,
    val label: String
)

private fun seg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun easeOut(t: Float) = 1f - (1f - t).pow(3)
private fun easeIn(t: Float) = t * t * t
private fun easeBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2)
}

private class Spark(val angle: Float, val speed: Float, val size: Float, val phase: Float)

/**
 * Full-screen, iPhone-like send / receive moment. One composable, four looks ([ShareAnimStyle]).
 * Shows in its own transparent dialog window so it sits above everything without restructuring screens.
 */
@Composable
fun ShareMagicOverlay(
    event: ShareAnimEvent?,
    style: ShareAnimStyle,
    reducedMotion: Boolean,
    onFinished: () -> Unit
) {
    if (event == null) return
    val effective = if (reducedMotion || style.isJourney) ShareAnimStyle.MINIMAL else style
    val duration = when {
        reducedMotion -> 1000
        event.role == ShareRole.SEND -> 1900
        else -> 2400
    }
    val p = remember(event.id) { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(event.id) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        p.animateTo(1f, tween(duration, easing = LinearEasing))
        onFinished()
    }

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val sparks = remember {
            val r = Random(5)
            List(40) { Spark(r.nextFloat() * 2f * PI.toFloat(), 0.35f + r.nextFloat() * 0.65f, 1.2f + r.nextFloat() * 2.6f, r.nextFloat()) }
        }
        val density = LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onFinished() } }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawBackdrop(effective, event.role, p.value, sparks)
            }
            TransferCard(event, effective) { p.value }
            val labelAlpha = { seg(p.value, 0.25f, 0.4f) * (1f - seg(p.value, 0.85f, 1f)) }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 190.dp)
                    .graphicsLayer {
                        alpha = labelAlpha()
                        translationY = (1f - easeOut(seg(p.value, 0.25f, 0.45f))) * 14f * density.density
                    }
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.40f))
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Text(
                    text = (if (event.role == ShareRole.SEND) "Sent  " else "Received  ") + event.label,
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TransferCard(event: ShareAnimEvent, style: ShareAnimStyle, progress: () -> Float) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 210.dp, height = 270.dp)
                .graphicsLayer {
                    val p = progress()
                    val h = size.height * 3.2f
                    var sc: Float
                    var ty = 0f
                    var a: Float
                    if (style == ShareAnimStyle.MINIMAL) {
                        if (event.role == ShareRole.SEND) {
                            sc = 0.92f + 0.08f * easeOut(seg(p, 0f, 0.3f)) - 0.1f * seg(p, 0.55f, 1f)
                            a = seg(p, 0f, 0.2f) * (1f - seg(p, 0.7f, 1f))
                        } else {
                            sc = 0.94f + 0.06f * easeOut(seg(p, 0f, 0.35f))
                            a = seg(p, 0f, 0.25f) * (1f - seg(p, 0.85f, 1f))
                        }
                    } else if (event.role == ShareRole.SEND) {
                        val lift = easeBack(seg(p, 0f, 0.32f))
                        val fly = easeIn(seg(p, 0.34f, 0.88f))
                        sc = (0.78f + 0.24f * lift) * (1f - 0.68f * fly)
                        ty = -h * fly
                        a = seg(p, 0f, 0.12f) * (1f - seg(p, 0.78f, 0.9f))
                    } else {
                        val drop = easeOut(seg(p, 0.12f, 0.62f))
                        val settle = easeBack(seg(p, 0.5f, 0.75f))
                        sc = 0.35f + 0.65f * drop + 0.04f * sin(settle * PI.toFloat())
                        ty = -h * 0.55f * (1f - drop)
                        a = seg(p, 0.12f, 0.3f) * (1f - seg(p, 0.86f, 1f))
                    }
                    scaleX = sc; scaleY = sc
                    translationY = ty
                    alpha = a.coerceIn(0f, 1f)
                }
                .clip(shape)
                .border(BorderStroke(1.5.dp, Color.White.copy(alpha = 0.75f)), shape)
                .background(Color(0xFF1B1B26)),
            contentAlignment = Alignment.Center
        ) {
            if (event.imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx).data(event.imageUri).crossfade(false).build(),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Outlined.Image, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(56.dp))
            }
            // glass sheen
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.22f), Color.Transparent, Color.White.copy(alpha = 0.06f))
                        )
                    )
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Backdrops
// ---------------------------------------------------------------------------------------------

private fun DrawScope.drawBackdrop(style: ShareAnimStyle, role: ShareRole, p: Float, sparks: List<Spark>) {
    val env = min(seg(p, 0f, 0.14f), 1f - seg(p, 0.86f, 1f))
    val c = Offset(size.width / 2f, size.height / 2f)
    val dim = min(size.width, size.height)
    when (style) {
        ShareAnimStyle.AURORA -> {
            drawRect(Color(0xFF05060F).copy(alpha = 0.58f * env))
            // soft nebula behind the card
            val r = dim * (0.55f + 0.1f * sin(p * 2f * PI.toFloat()))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFB57BFF).copy(alpha = 0.40f * env), Color(0xFF5AA8FF).copy(alpha = 0.15f * env), Color.Transparent),
                    center = c, radius = r
                ),
                radius = r, center = c
            )
            // rotating aurora edge
            val brush = Brush.sweepGradient(ring(p * MagicBase.size * 1.6f), size.center)
            val inset = 4.dp.toPx()
            listOf(44f to 0.10f, 24f to 0.20f, 10f to 0.45f, 3.5f to 1f).forEach { (w, a) ->
                drawRoundRect(
                    brush, Offset(inset, inset),
                    androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
                    androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                    alpha = a * env, style = Stroke(width = w * density / 2.75f)
                )
            }
            // travelling light wave (up when sending, down when receiving)
            val t = if (role == ShareRole.SEND) 1f - seg(p, 0.3f, 0.92f) else seg(p, 0f, 0.6f)
            val y = size.height * t
            val band = 150.dp.toPx()
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.30f * env), Color.Transparent),
                    startY = y - band, endY = y + band
                ),
                topLeft = Offset(0f, y - band), size = androidx.compose.ui.geometry.Size(size.width, band * 2)
            )
        }

        ShareAnimStyle.LIQUID -> {
            drawRect(Color(0xFF07152B).copy(alpha = 0.55f * env))
            val blob = dim * (0.42f + 0.10f * sin(p * 5f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.26f * env), Color(0xFF7FB8FF).copy(alpha = 0.16f * env), Color.Transparent),
                    center = c, radius = blob * 1.5f
                ),
                radius = blob * 1.5f, center = c
            )
            val origin = if (role == ShareRole.RECEIVE) Offset(size.width / 2f, 0f) else c
            val maxR = max(size.width, size.height) * 0.9f
            for (i in 0 until 4) {
                val t = seg(p, 0.05f + i * 0.09f, 0.75f + i * 0.06f)
                if (t <= 0f || t >= 1f) continue
                val r = easeOut(t) * maxR
                val a = (1f - t) * 0.5f * env
                drawCircle(Color(0xFFBFDDFF).copy(alpha = a * 0.5f), r, origin, style = Stroke(width = 18.dp.toPx() * (1f - t * 0.7f)))
                drawCircle(Color.White.copy(alpha = a), r, origin, style = Stroke(width = 2.dp.toPx()))
            }
            if (role == ShareRole.RECEIVE) {
                // second wave converges on the card as it lands
                val t = seg(p, 0.45f, 0.8f)
                if (t > 0f && t < 1f) {
                    drawCircle(Color.White.copy(alpha = (1f - t) * 0.5f), 40.dp.toPx() + easeOut(t) * dim * 0.5f, c, style = Stroke(width = 3.dp.toPx()))
                }
            }
        }

        ShareAnimStyle.ARCANE -> {
            val gold = Color(0xFFFFD27A)
            val violet = Color(0xFFB57BFF)
            drawRect(Color(0xFF0E0820).copy(alpha = 0.66f * env))
            drawCircle(
                Brush.radialGradient(
                    listOf(gold.copy(alpha = 0.34f * env), violet.copy(alpha = 0.14f * env), Color.Transparent),
                    center = c, radius = dim * 0.7f
                ),
                radius = dim * 0.7f, center = c
            )
            val ringR = dim * (0.30f + 0.22f * easeOut(seg(p, 0f, 0.5f)))
            val rot = p * 140f
            rotate(rot, c) {
                drawCircle(
                    gold.copy(alpha = 0.85f * env), ringR, c,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                )
                for (i in 0 until 12) {
                    val a = i * (2f * PI.toFloat() / 12f)
                    val r1 = ringR - 10.dp.toPx()
                    val r2 = ringR + 10.dp.toPx()
                    drawLine(
                        gold.copy(alpha = 0.8f * env),
                        Offset(c.x + cos(a) * r1, c.y + sin(a) * r1),
                        Offset(c.x + cos(a) * r2, c.y + sin(a) * r2),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            rotate(-rot * 1.4f, c) {
                drawCircle(
                    violet.copy(alpha = 0.75f * env), ringR * 0.78f, c,
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 9f)))
                )
            }
            // sparkle burst
            val burstStart = if (role == ShareRole.SEND) 0.34f else 0.5f
            val bt = seg(p, burstStart, min(burstStart + 0.55f, 1f))
            if (bt > 0f) {
                sparks.forEach { s ->
                    val d = easeOut(bt) * dim * 0.55f * s.speed
                    val pos = Offset(c.x + cos(s.angle) * d, c.y + sin(s.angle) * d)
                    val tw = 0.55f + 0.45f * sin(2f * PI.toFloat() * (p * 3f + s.phase))
                    val a = (1f - bt) * tw * env
                    val rad = s.size * density
                    drawCircle(gold.copy(alpha = 0.25f * a), rad * 3.4f, pos)
                    drawCircle(Color.White.copy(alpha = 0.9f * a), rad * 0.7f, pos)
                    drawLine(Color.White.copy(alpha = 0.7f * a), Offset(pos.x - rad * 2f, pos.y), Offset(pos.x + rad * 2f, pos.y), strokeWidth = 1f * density)
                    drawLine(Color.White.copy(alpha = 0.7f * a), Offset(pos.x, pos.y - rad * 2f), Offset(pos.x, pos.y + rad * 2f), strokeWidth = 1f * density)
                }
            }
        }

        else -> {
            drawRect(Color.Black.copy(alpha = 0.42f * env))
            val t = seg(p, 0.1f, 0.9f)
            drawCircle(
                Color.White.copy(alpha = (1f - t) * 0.5f * env),
                dim * 0.18f + easeOut(t) * dim * 0.34f, c,
                style = Stroke(width = 2.dp.toPx())
            )
        }
    }
}
