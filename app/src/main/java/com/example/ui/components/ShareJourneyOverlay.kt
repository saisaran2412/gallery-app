package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.core.storage.ShareAnimStyle
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Live state of a transfer "trip". [target] is real progress 0..1; [done] when every file finished. */
data class ShareJourney(
    val id: Long,
    val role: ShareRole,
    val peer: String,
    val target: Float,
    val done: Boolean,
    val success: Boolean,
    val doneCount: Int,
    val count: Int
)

private fun jseg(p: Float, a: Float, b: Float) = ((p - a) / (b - a)).coerceIn(0f, 1f)
private fun jOut(t: Float) = 1f - (1f - t).pow(3)
private fun jInOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
private fun jBack(t: Float): Float { val c1 = 1.70158f; val c3 = c1 + 1f; return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2) }
private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun hash(i: Int): Float {
    var x = i * 374761393 + 668265263
    x = (x xor (x ushr 13)) * 1274126177.toInt()
    x = x xor (x ushr 16)
    return (x and 0xFFFF) / 65535f
}

/**
 * Parallax delivery scene that plays for the whole real transfer. Sender watches the courier leave
 * "You" and arrive at the other phone; receiver watches it arrive at "You". Tap or Back hides it
 * (the transfer keeps going).
 */
@Composable
fun ShareJourneyOverlay(
    journey: ShareJourney,
    style: ShareAnimStyle,
    onHide: () -> Unit,
    onFinished: () -> Unit
) {
    val displayed = remember(journey.id) { Animatable(0f) }
    val finale = remember(journey.id) { Animatable(0f) }
    val clock = remember(journey.id) { mutableFloatStateOf(0f) }
    val scroll = remember(journey.id) { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(journey.id) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
    LaunchedEffect(journey.id) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (last != 0L) {
                    val dt = ((n - last) / 1e9f).coerceAtMost(0.05f)
                    clock.floatValue += dt
                    val speed = 1f - jOut(jseg(displayed.value, 0.9f, 1f))
                    scroll.floatValue += dt * speed * 170f
                }
                last = n
            }
        }
    }
    val tgt = if (journey.done) 1f else min(journey.target, 0.9f)
    LaunchedEffect(journey.id, tgt, journey.done, journey.success) {
        if (journey.done && !journey.success) {
            delay(250); onFinished(); return@LaunchedEffect
        }
        val dist = abs(tgt - displayed.value)
        val ms = max(300, (dist * (if (journey.done) 1800 else 5200)).toInt())
        displayed.animateTo(tgt, tween(ms, easing = LinearOutSlowInEasing))
        if (journey.done) {
            finale.animateTo(1f, tween(1400, easing = LinearEasing))
            delay(450)
            onFinished()
        }
    }

    Dialog(
        onDismissRequest = onHide,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setDimAmount(0f)
            window?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        val shape = RoundedCornerShape(28.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.66f))
                .pointerInput(Unit) { detectTapGestures { onHide() } },
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (journey.role == ShareRole.SEND) "Delivering to ${journey.peer}" else "Incoming from ${journey.peer}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(14.dp))
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(300.dp)
                        .clip(shape)
                        .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
                ) {
                    drawJourney(style, clock.floatValue, scroll.floatValue, displayed.value, finale.value)
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (journey.role == ShareRole.SEND) "You" else journey.peer, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                    Text(if (journey.role == ShareRole.SEND) journey.peer else "You", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(6.dp))
                MagicProgressBar(
                    progress = displayed.value,
                    active = true,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    if (finale.value > 0.2f) "Delivered" else
                        "${(displayed.value * 100).toInt()}%  -  ${journey.doneCount} of ${journey.count}",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text("Tap to hide - transfer keeps running", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Scene drawing (design units: scene is 300 high, width = W; everything scaled by size.height/300)
// ---------------------------------------------------------------------------------------------

private class Sc(val t: Float, val scroll: Float, val prog: Float, val finale: Float, val W: Float) {
    val H = 300f
    val recvFinal = W * 0.80f
    val arrive get() = jInOut(jseg(prog, 0.9f, 1f))
    val moving get() = 1f - jseg(prog, 0.93f, 1f)
    fun recvX() = mix(W + 70f, recvFinal, jOut(jseg(prog, 0.78f, 1f)))
    fun senderX() = 14f - scroll
}

private fun DrawScope.drawJourney(style: ShareAnimStyle, t: Float, scroll: Float, prog: Float, finale: Float) {
    val u = size.height / 300f
    val sc = Sc(t, scroll, prog, finale, size.width / u)
    clipRect(0f, 0f, size.width, size.height) {
        scale(u, u, Offset.Zero) {
            when (style) {
                ShareAnimStyle.PAPER_PLANE -> scenePlane(sc)
                ShareAnimStyle.ROCKET -> sceneRocket(sc)
                ShareAnimStyle.DRONE -> sceneDrone(sc)
                ShareAnimStyle.STREAM -> sceneGlow(sc, orb = false)
                ShareAnimStyle.ORB -> sceneGlow(sc, orb = true)
                else -> sceneScooter(sc)
            }
        }
    }
}

private fun DrawScope.tiled(offset: Float, spacing: Float, width: Float, draw: DrawScope.(Float, Int) -> Unit) {
    val k0 = floor(offset / spacing).toInt()
    val n = (width / spacing).toInt() + 3
    for (i in -1..n) {
        val idx = k0 + i
        draw(idx * spacing - offset, idx)
    }
}

private fun DrawScope.cloud(x: Float, y: Float, s: Float, a: Float) {
    val c = Color.White.copy(alpha = a)
    drawCircle(c, 14f * s, Offset(x + 14f * s, y))
    drawCircle(c, 18f * s, Offset(x + 34f * s, y - 6f * s))
    drawCircle(c, 13f * s, Offset(x + 54f * s, y))
    drawRoundRect(c, Offset(x + 2f * s, y - 2f * s), Size(62f * s, 16f * s), CornerRadius(8f * s))
}

private fun DrawScope.phone(x: Float, baseY: Float, glow: Float) {
    val w = 34f
    val h = 62f
    val top = baseY - h
    val g = glow.coerceIn(0f, 1f)
    if (g > 0f) drawCircle(Color(0xFF7CFFB2).copy(alpha = 0.35f * g), 58f, Offset(x + w / 2f, top + h / 2f))
    drawRoundRect(Color(0xFF14161E), Offset(x, top), Size(w, h), CornerRadius(7f))
    drawRoundRect(lerp(Color(0xFF2B3A67), Color(0xFF9BFFC8), g), Offset(x + 2.5f, top + 2.5f), Size(w - 5f, h - 5f), CornerRadius(5f))
    drawRoundRect(Color(0xFF14161E), Offset(x + w / 2f - 6f, top + 3f), Size(12f, 3f), CornerRadius(1.5f))
}

/** Check badge, pulse ring and confetti over the receiving phone. */
private fun DrawScope.finaleBurst(c: Sc, cx: Float, cy: Float) {
    val f = c.finale
    if (f <= 0f) return
    val ringT = jOut(jseg(f, 0f, 0.8f))
    drawCircle(Color.White.copy(alpha = (1f - ringT) * 0.7f), 18f + ringT * 90f, Offset(cx, cy), style = Stroke(width = 3f))
    val badge = 15f * jBack(jseg(f, 0f, 0.35f))
    val bc = Offset(cx, cy - 58f - 6f * jOut(jseg(f, 0f, 0.5f)))
    if (badge > 0f) {
        drawCircle(Color(0xFF34C759), badge, bc)
        val k = badge / 15f
        drawLine(Color.White, Offset(bc.x - 6f * k, bc.y), Offset(bc.x - 1.5f * k, bc.y + 5f * k), strokeWidth = 3f, cap = StrokeCap.Round)
        drawLine(Color.White, Offset(bc.x - 1.5f * k, bc.y + 5f * k), Offset(bc.x + 7f * k, bc.y - 6f * k), strokeWidth = 3f, cap = StrokeCap.Round)
    }
    val palette = listOf(Color(0xFFFF6BA8), Color(0xFFFFD54F), Color(0xFF5AA8FF), Color(0xFF4FE3D1), Color(0xFFB57BFF))
    val e = jOut(jseg(f, 0f, 1f))
    for (i in 0 until 26) {
        val a = hash(i) * 2f * PI.toFloat()
        val d = e * (30f + hash(i + 50) * 80f)
        val pos = Offset(cx + cos(a) * d, cy - 20f + sin(a) * d + f * f * 45f)
        drawCircle(palette[i % palette.size].copy(alpha = 1f - jseg(f, 0.55f, 1f)), 2.4f + hash(i + 9) * 2f, pos)
    }
}

// ----------------------------------------- SCOOTER -----------------------------------------

private fun DrawScope.sceneScooter(c: Sc) {
    val W = c.W
    val H = c.H
    val roadTop = 214f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF7EC8F8), Color(0xFFFFE9C9)), 0f, H * 0.8f), Offset.Zero, Size(W, H))
    drawCircle(Color(0xFFFFE08A).copy(alpha = 0.35f), 46f, Offset(W * 0.78f, 62f))
    drawCircle(Color(0xFFFFE08A), 26f, Offset(W * 0.78f, 62f))
    tiled(c.scroll * 0.06f, 230f, W) { x, i -> cloud(x, 34f + hash(i) * 46f, 0.8f + hash(i + 3) * 0.6f, 0.9f) }
    val hills = Path()
    hills.moveTo(0f, H)
    var px = 0f
    while (px <= W + 12f) {
        val off = c.scroll * 0.15f
        hills.lineTo(px, 190f - sin((px + off) / 70f) * 14f - sin((px + off) / 29f) * 5f)
        px += 12f
    }
    hills.lineTo(W, H); hills.close()
    drawPath(hills, Color(0xFF8FD0A8))
    tiled(c.scroll * 0.4f, 64f, W) { x, i ->
        val bw = 38f + hash(i) * 22f
        val bh = 56f + hash(i + 7) * 74f
        drawRect(Color(0xFF6F86B5), Offset(x, roadTop - bh), Size(bw, bh))
        for (r in 0 until (bh / 16f).toInt()) for (cc in 0..1) {
            if (hash(i * 31 + r * 7 + cc) > 0.45f) drawRect(Color(0xFFFFE9A8), Offset(x + 6f + cc * 16f, roadTop - bh + 8f + r * 16f), Size(8f, 8f))
        }
    }
    drawRect(Color(0xFFD9D2C5), Offset(0f, roadTop), Size(W, 10f))
    drawRect(Color(0xFF3A3F4B), Offset(0f, roadTop + 10f), Size(W, H - roadTop - 10f))
    tiled(c.scroll, 54f, W) { x, _ -> drawRect(Color.White.copy(alpha = 0.8f), Offset(x, 262f), Size(28f, 3f)) }
    tiled(c.scroll, 280f, W) { x, _ ->
        drawRect(Color(0xFF51586B), Offset(x, roadTop - 78f), Size(3f, 78f))
        drawRect(Color(0xFF51586B), Offset(x - 8f, roadTop - 80f), Size(14f, 3f))
        drawCircle(Color(0xFFFFF3B0), 3.5f, Offset(x - 6f, roadTop - 76f))
    }
    val baseY = roadTop + 8f
    if (c.senderX() > -60f) phone(c.senderX(), baseY, 0f)
    phone(c.recvX(), baseY, jOut(jseg(c.finale, 0f, 0.3f)))
    val rx = mix(W * 0.30f, c.recvFinal - 66f, c.arrive)
    rider(rx, roadTop + 34f, c)
    finaleBurst(c, c.recvFinal + 17f, baseY - 31f)
}

private fun DrawScope.rider(cx: Float, gy: Float, c: Sc) {
    val mv = c.moving
    val bob = sin(c.t * 14f) * 1.2f * mv
    val spin = c.scroll * 4.4f
    val teal = Color(0xFF2BB5A8)
    // speed lines
    for (i in 0 until 3) {
        val ly = gy - 20f - i * 14f
        val lx = cx - 70f - ((c.t * 120f + i * 30f) % 40f)
        drawLine(Color.White.copy(alpha = 0.55f * mv), Offset(lx, ly), Offset(lx - 26f, ly), strokeWidth = 2f, cap = StrokeCap.Round)
    }
    // wheels
    listOf(cx - 30f, cx + 30f).forEach { wx ->
        val wy = gy - 13f
        drawCircle(Color(0xFF22252E), 13f, Offset(wx, wy))
        drawCircle(Color(0xFFBFC5D2), 5f, Offset(wx, wy))
        rotate(spin, Offset(wx, wy)) {
            for (k in 0 until 3) {
                val a = k * (PI.toFloat() / 3f)
                drawLine(Color(0xFF8B91A3), Offset(wx - cos(a) * 11f, wy - sin(a) * 11f), Offset(wx + cos(a) * 11f, wy + sin(a) * 11f), strokeWidth = 1.5f)
            }
        }
    }
    // body
    drawRoundRect(teal, Offset(cx - 44f, gy - 44f + bob), Size(38f, 20f), CornerRadius(9f))
    drawRoundRect(Color(0xFF1F8F85), Offset(cx - 36f, gy - 28f + bob), Size(66f, 6f), CornerRadius(3f))
    drawLine(teal, Offset(cx + 24f, gy - 26f + bob), Offset(cx + 20f, gy - 62f + bob), strokeWidth = 5f, cap = StrokeCap.Round)
    drawLine(Color(0xFF22252E), Offset(cx + 12f, gy - 64f + bob), Offset(cx + 28f, gy - 64f + bob), strokeWidth = 4f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFFFF3B0), 3.5f, Offset(cx + 31f, gy - 52f + bob))
    if (mv > 0.1f) {
        val beam = Path()
        beam.moveTo(cx + 33f, gy - 52f + bob); beam.lineTo(cx + 78f, gy - 62f + bob); beam.lineTo(cx + 78f, gy - 40f + bob); beam.close()
        drawPath(beam, Color(0xFFFFF3B0).copy(alpha = 0.18f * mv))
    }
    // delivery box with a little photo icon
    drawRoundRect(Color(0xFFFF8A3D), Offset(cx - 56f, gy - 76f + bob), Size(30f, 30f), CornerRadius(5f))
    drawRect(Color(0xFFE5701F), Offset(cx - 56f, gy - 76f + bob), Size(30f, 5f))
    drawRoundRect(Color.White, Offset(cx - 50f, gy - 66f + bob), Size(18f, 13f), CornerRadius(2f))
    drawCircle(Color(0xFFFFB84D), 2f, Offset(cx - 45f, gy - 62f + bob))
    val m = Path()
    m.moveTo(cx - 50f, gy - 53f + bob); m.lineTo(cx - 43f, gy - 60f + bob); m.lineTo(cx - 38f, gy - 55f + bob); m.lineTo(cx - 35f, gy - 58f + bob); m.lineTo(cx - 32f, gy - 53f + bob); m.close()
    drawPath(m, Color(0xFF5EC27A))
    // rider
    drawLine(Color(0xFF2B2F4A), Offset(cx - 8f, gy - 44f + bob), Offset(cx + 10f, gy - 40f + bob), strokeWidth = 6f, cap = StrokeCap.Round)
    drawLine(Color(0xFF2B2F4A), Offset(cx + 10f, gy - 40f + bob), Offset(cx + 12f, gy - 26f + bob), strokeWidth = 6f, cap = StrokeCap.Round)
    drawRoundRect(Color(0xFF3D5AFE), Offset(cx - 16f, gy - 76f + bob), Size(20f, 32f), CornerRadius(8f))
    drawLine(Color(0xFF6C84FF), Offset(cx - 2f, gy - 70f + bob), Offset(cx + 18f, gy - 64f + bob), strokeWidth = 5f, cap = StrokeCap.Round)
    drawCircle(Color(0xFFFFC8A2), 3f, Offset(cx + 19f, gy - 64f + bob))
    val flap = sin(c.t * 20f) * 3f * mv
    val scarf = Path()
    scarf.moveTo(cx - 8f, gy - 74f + bob); scarf.quadraticBezierTo(cx - 20f, gy - 78f + bob + flap, cx - 32f, gy - 74f + bob - flap)
    drawPath(scarf, Color(0xFFFFD54F), style = Stroke(width = 4f, cap = StrokeCap.Round))
    drawCircle(Color(0xFFFFC8A2), 9f, Offset(cx - 4f, gy - 87f + bob))
    val helmet = Path()
    helmet.moveTo(cx - 14f, gy - 87f + bob); helmet.quadraticBezierTo(cx - 4f, gy - 104f + bob, cx + 6f, gy - 87f + bob); helmet.close()
    drawPath(helmet, Color(0xFFE53935))
    drawRect(Color(0xFFB71C1C), Offset(cx - 2f, gy - 90f + bob), Size(10f, 2.5f))
    drawCircle(Color(0xFF2B2F4A), 1.4f, Offset(cx + 1f, gy - 86f + bob))
}

// --------------------------------------- PAPER PLANE ---------------------------------------

private fun DrawScope.scenePlane(c: Sc) {
    val W = c.W
    val H = c.H
    drawRect(Brush.verticalGradient(listOf(Color(0xFF5B6CFF), Color(0xFFFFA8C5), Color(0xFFFFE3B8)), 0f, H), Offset.Zero, Size(W, H))
    drawCircle(Color(0xFFFFF1C9).copy(alpha = 0.85f), 34f, Offset(W * 0.8f, 200f))
    tiled(c.scroll * 0.1f, 190f, W) { x, i -> cloud(x, 60f + hash(i) * 60f, 1.0f, 0.5f) }
    tiled(c.scroll * 0.5f, 150f, W) { x, i ->
        if (hash(i) > 0.5f) {
            val by = 40f + hash(i + 5) * 60f
            val fl = sin(c.t * 9f + i) * 3f
            drawLine(Color(0xFF3B3F7A), Offset(x, by), Offset(x + 6f, by - 4f + fl), strokeWidth = 1.6f)
            drawLine(Color(0xFF3B3F7A), Offset(x + 6f, by - 4f + fl), Offset(x + 12f, by), strokeWidth = 1.6f)
        }
    }
    tiled(c.scroll * 0.3f, 240f, W) { x, i -> cloud(x, 120f + hash(i) * 70f, 1.5f, 0.75f) }
    val baseY = 262f
    if (c.senderX() > -90f) { cloud(c.senderX() - 24f, baseY - 10f, 2.2f, 0.95f); phone(c.senderX(), baseY, 0f) }
    cloud(c.recvX() - 30f, baseY - 10f, 2.2f, 0.95f)
    phone(c.recvX(), baseY, jOut(jseg(c.finale, 0f, 0.3f)))
    val a = c.arrive
    val px = mix(W * 0.30f, c.recvFinal - 54f, a)
    val py = mix(130f + sin(c.t * 1.8f) * 16f, 216f, jInOut(jseg(c.prog, 0.88f, 1f)))
    for (k in 0..22) {
        val past = sin((c.t - k * 0.08f) * 1.8f) * 16f - sin(c.t * 1.8f) * 16f
        drawCircle(Color.White.copy(alpha = (1f - k / 23f) * 0.8f * c.moving), 2.2f - k * 0.05f, Offset(px - 20f - k * 10f, py + past * (1f - a)))
    }
    planeShape(px, py, cos(c.t * 1.8f) * 9f * (1f - a))
    tiled(c.scroll * 0.9f, 200f, W) { x, i -> cloud(x, 268f + hash(i) * 14f, 2.6f, 0.4f) }
    finaleBurst(c, c.recvFinal + 17f, baseY - 31f)
}

private fun DrawScope.planeShape(x: Float, y: Float, deg: Float) {
    translate(x, y) {
        rotate(deg, Offset.Zero) {
            drawCircle(Color.White.copy(alpha = 0.18f), 38f, Offset.Zero)
            val top = Path()
            top.moveTo(46f, 0f); top.lineTo(-30f, -22f); top.lineTo(-12f, 2f); top.close()
            drawPath(top, Color.White)
            val bot = Path()
            bot.moveTo(46f, 0f); bot.lineTo(-12f, 2f); bot.lineTo(-30f, 18f); bot.close()
            drawPath(bot, Color(0xFFD3DEFF))
            drawLine(Color(0xFF9AA8E6), Offset(46f, 0f), Offset(-12f, 2f), strokeWidth = 1.5f)
            val keel = Path()
            keel.moveTo(-12f, 2f); keel.lineTo(-30f, 18f); keel.lineTo(-22f, 6f); keel.close()
            drawPath(keel, Color(0xFFB4C2F5))
        }
    }
}

// ----------------------------------------- ROCKET ------------------------------------------

private fun DrawScope.sceneRocket(c: Sc) {
    val W = c.W
    val H = c.H
    drawRect(Brush.verticalGradient(listOf(Color(0xFF04061A), Color(0xFF1A1F5C)), 0f, H), Offset.Zero, Size(W, H))
    listOf(Triple(0.1f, 90f, 1.1f), Triple(0.3f, 70f, 1.6f), Triple(0.7f, 55f, 2.2f)).forEachIndexed { li, (f, sp, r) ->
        tiled(c.scroll * f, sp, W) { x, i ->
            for (j in 0..2) {
                val sy = hash(i * 5 + j + li * 100) * H * 0.85f
                val sx = x + hash(i * 9 + j + li * 77) * sp
                val tw = 0.6f + 0.4f * sin(c.t * 3f + i + j)
                drawCircle(Color.White.copy(alpha = 0.8f * tw), r, Offset(sx, sy))
            }
        }
    }
    tiled(c.scroll * 0.12f, 700f, W) { x, i ->
        if (i % 2 == 0) {
            val cy = 90f + hash(i) * 60f
            drawCircle(Color(0xFF6C4DFF), 46f, Offset(x + 200f, cy))
            drawCircle(Color(0xFF8E75FF).copy(alpha = 0.6f), 30f, Offset(x + 190f, cy - 10f))
            drawOval(Color(0xFFFFD27A).copy(alpha = 0.7f), Offset(x + 140f, cy - 8f), Size(120f, 16f), style = Stroke(width = 2.5f))
        }
    }
    val baseY = 250f
    if (c.senderX() > -90f) { drawCircle(Color(0xFF9AA3C4), 70f, Offset(c.senderX() + 17f, baseY + 58f)); phone(c.senderX(), baseY, 0f) }
    drawCircle(Color(0xFFB8BFD6), 70f, Offset(c.recvX() + 17f, baseY + 58f))
    drawCircle(Color(0xFF9AA3C4), 9f, Offset(c.recvX() - 20f, baseY + 20f))
    phone(c.recvX(), baseY, jOut(jseg(c.finale, 0f, 0.3f)))
    val px = mix(c.W * 0.32f, c.recvFinal - 74f, c.arrive)
    val py = mix(140f + sin(c.t * 2f) * 8f, 205f, jInOut(jseg(c.prog, 0.9f, 1f)))
    val flame = (24f + sin(c.t * 40f) * 6f) * c.moving + 4f
    translate(px, py) {
        rotate(sin(c.t * 2f) * 4f, Offset.Zero) {
            val fl = Path()
            fl.moveTo(-26f, -6f); fl.lineTo(-26f - flame, 0f); fl.lineTo(-26f, 6f); fl.close()
            drawPath(fl, Color(0xFFFF9F43))
            val fl2 = Path()
            fl2.moveTo(-26f, -3f); fl2.lineTo(-26f - flame * 0.6f, 0f); fl2.lineTo(-26f, 3f); fl2.close()
            drawPath(fl2, Color(0xFFFFE680))
            drawRoundRect(Color.White, Offset(-26f, -10f), Size(40f, 20f), CornerRadius(9f))
            val nose = Path()
            nose.moveTo(12f, -10f); nose.quadraticBezierTo(34f, -8f, 42f, 0f); nose.quadraticBezierTo(34f, 8f, 12f, 10f); nose.close()
            drawPath(nose, Color(0xFFE53935))
            val f1 = Path()
            f1.moveTo(-24f, -10f); f1.lineTo(-38f, -22f); f1.lineTo(-12f, -10f); f1.close()
            val f2 = Path()
            f2.moveTo(-24f, 10f); f2.lineTo(-38f, 22f); f2.lineTo(-12f, 10f); f2.close()
            drawPath(f1, Color(0xFFE53935)); drawPath(f2, Color(0xFFE53935))
            drawCircle(Color(0xFF7FD6FF), 5f, Offset(0f, 0f))
            drawCircle(Color.White.copy(alpha = 0.6f), 1.8f, Offset(-1.5f, -1.5f))
        }
    }
    finaleBurst(c, c.recvFinal + 17f, baseY - 31f)
}

// ------------------------------------------ DRONE ------------------------------------------

private fun DrawScope.sceneDrone(c: Sc) {
    val W = c.W
    val H = c.H
    val ground = 246f
    drawRect(Brush.verticalGradient(listOf(Color(0xFF86C8FF), Color(0xFFEAF6FF)), 0f, H * 0.85f), Offset.Zero, Size(W, H))
    tiled(c.scroll * 0.08f, 220f, W) { x, i -> cloud(x, 30f + hash(i) * 40f, 1.0f, 0.85f) }
    tiled(c.scroll * 0.2f, 90f, W) { x, i ->
        val bh = 50f + hash(i) * 70f
        drawRect(Color(0xFFA9C3E8), Offset(x, ground - bh), Size(46f, bh))
    }
    tiled(c.scroll * 0.5f, 74f, W) { x, i ->
        val bh = 40f + hash(i + 11) * 80f
        drawRect(Color(0xFF7F9CCB), Offset(x, ground - bh), Size(40f, bh))
        if (hash(i + 3) > 0.7f) drawLine(Color(0xFF7F9CCB), Offset(x + 20f, ground - bh), Offset(x + 20f, ground - bh - 18f), strokeWidth = 2f)
        for (r in 0 until (bh / 16f).toInt()) if (hash(i * 13 + r) > 0.5f) drawRect(Color(0xFFEAF6FF).copy(alpha = 0.8f), Offset(x + 8f + (r % 2) * 16f, ground - bh + 8f + r * 16f), Size(8f, 7f))
    }
    drawRect(Color(0xFF9ED8A0), Offset(0f, ground), Size(W, H - ground))
    tiled(c.scroll, 150f, W) { x, i ->
        drawRect(Color(0xFF8A6B4E), Offset(x + 12f, ground - 20f), Size(5f, 22f))
        drawCircle(Color(0xFF4FA56A), 15f + hash(i) * 4f, Offset(x + 14f, ground - 28f))
    }
    val baseY = ground + 6f
    if (c.senderX() > -60f) phone(c.senderX(), baseY, 0f)
    phone(c.recvX(), baseY, jOut(jseg(c.finale, 0f, 0.3f)))
    val px = mix(c.W * 0.30f, c.recvFinal + 17f, c.arrive)
    val py = mix(96f + sin(c.t * 2.4f) * 5f, 92f, c.arrive)
    val lower = jInOut(jseg(c.prog, 0.92f, 1f))
    val rope = 22f + 70f * lower
    translate(px, py) {
        rotate(6f * c.moving, Offset.Zero) {
            drawLine(Color(0xFF2D3142), Offset(-26f, -12f), Offset(-12f, -2f), strokeWidth = 3f, cap = StrokeCap.Round)
            drawLine(Color(0xFF2D3142), Offset(26f, -12f), Offset(12f, -2f), strokeWidth = 3f, cap = StrokeCap.Round)
            drawRoundRect(Color(0xFF2D3142), Offset(-17f, -7f), Size(34f, 13f), CornerRadius(6f))
            drawCircle(Color(0xFF7FD6FF), 3f, Offset(10f, 1f))
            listOf(-26f, 26f).forEach { rx ->
                val bl = 3f + 2f * abs(sin(c.t * 60f))
                drawOval(Color(0xFF2D3142).copy(alpha = 0.35f), Offset(rx - 15f, -14f - bl / 2f), Size(30f, bl))
                drawCircle(Color(0xFF2D3142), 2.4f, Offset(rx, -12f))
            }
        }
        val sway = sin(c.t * 5f) * 3.5f * c.moving
        drawLine(Color(0xFF2D3142), Offset(0f, 6f), Offset(sway, rope), strokeWidth = 1.6f)
        if (c.finale < 0.25f) {
            drawRoundRect(Color(0xFFFF8A3D), Offset(sway - 11f, rope), Size(22f, 20f), CornerRadius(4f))
            drawRect(Color(0xFFFFD1A6), Offset(sway - 2f, rope), Size(4f, 20f))
        }
    }
    finaleBurst(c, c.recvFinal + 17f, baseY - 31f)
}


// ------------------------------------ PARTICLE / ORB GLOW ------------------------------------

private fun DrawScope.glowDot(x: Float, y: Float, r: Float, hue: Float, a: Float, v: Float = 1f, s: Float = 0.75f) {
    if (a <= 0.01f || r <= 0.5f) return
    val col = Color.hsv(((hue % 360f) + 360f) % 360f, s, v)
    val center = Offset(x, y)
    drawCircle(
        brush = Brush.radialGradient(listOf(col.copy(alpha = a.coerceIn(0f, 1f)), col.copy(alpha = 0f)), center = center, radius = r),
        radius = r, center = center, blendMode = BlendMode.Plus
    )
}

private fun DrawScope.glassPhone(x: Float, y: Float, w: Float, h: Float) {
    drawRoundRect(
        Brush.linearGradient(listOf(Color.White.copy(alpha = 0.16f), Color.White.copy(alpha = 0.04f)), Offset(x, y), Offset(x + w, y + h)),
        Offset(x, y), Size(w, h), CornerRadius(22f)
    )
    drawRoundRect(Color.White.copy(alpha = 0.28f), Offset(x, y), Size(w, h), CornerRadius(22f), style = Stroke(1.2f))
}

/** Modern gradient + particle glow. Phones touch, light blooms, particles (or an orb) carry the real transfer. */
private fun DrawScope.sceneGlow(c: Sc, orb: Boolean) {
    val W = c.W
    val cy = 150f
    val pw = 96f
    val ph = 176f
    val top = cy - ph / 2f
    val prog = c.prog
    val t = c.t
    val done = c.finale
    drawRect(Color(0xFF05060C), Offset.Zero, Size(W, 300f))

    val touch = jOut(jseg(prog, 0f, 0.12f))
    val gap = mix(130f, 10f, touch)
    val lx = W / 2f - gap / 2f - pw
    val rx = W / 2f + gap / 2f
    val cx = W / 2f
    val hue = (210f + t * 40f) % 360f
    val amb = jOut(jseg(prog, 0.05f, 0.2f)) * (1f - 0.5f * jseg(done, 0.5f, 1f))

    glowDot(lx + pw / 2f, cy, 150f, hue, 0.30f * amb, 1f, 0.7f)
    glowDot(rx + pw / 2f, cy, 150f, hue + 70f, 0.30f * amb, 1f, 0.7f)
    glowDot(cx, cy, 110f, hue + 140f, 0.28f * amb, 1f, 0.7f)

    glassPhone(lx, top, pw, ph)
    glassPhone(rx, top, pw, ph)
    glowDot(lx + pw - 14f, cy, 70f, hue, 0.35f * amb, 0.95f, 0.8f)
    glowDot(rx + 14f, cy, 70f, hue + 70f, 0.35f * amb, 0.95f, 0.8f)

    val active = jseg(prog, 0.1f, 0.2f) * (1f - jseg(done, 0f, 0.4f))
    val sp = jseg(prog, 0.12f, 1f)

    if (!orb) {
        val n = 100
        for (i in 0 until n) {
            val q = (t * 0.55f + hash(i)) % 1f
            val spread = (hash(i + 9) - 0.5f) * ph * 0.7f
            val sx = lx + pw - 6f
            val ex = rx + 6f
            val x = mix(sx, ex, jInOut(q))
            val y = cy + spread * (1f - sin(q * PI.toFloat()) * 0.75f) + sin(q * 6.28f + i) * 8f * sin(q * PI.toFloat())
            val a = sin(q * PI.toFloat()) * 0.9f * active
            val r = 1.2f + hash(i + 4) * 2.4f
            glowDot(x, y, r * 5f, hue + q * 120f + hash(i) * 40f, a * 0.5f)
            glowDot(x, y, r * 1.6f, hue + q * 120f, a, 1f, 0.15f)
        }
    } else {
        val ox = mix(lx + pw / 2f, rx + pw / 2f, jInOut(jseg(sp, 0.2f, 0.9f)))
        val grow = jOut(jseg(sp, 0f, 0.2f)) * (1f - jOut(jseg(done, 0f, 0.35f)))
        val orbR = 34f * grow + sin(t * 6f) * 2f * grow
        if (grow > 0f) {
            for (i in 0 until 90) {
                val rot = hash(i) * 6.2832f + t * (1.5f + hash(i + 7))
                val rad = 14f + hash(i + 3) * 42f
                glowDot(ox + cos(rot) * (orbR + rad * 0.4f), cy + sin(rot) * (orbR * 0.9f + rad * 0.35f), 3f + hash(i + 2) * 3f * 3f, hue + hash(i) * 120f, 0.6f * grow, 1f, 0.15f)
            }
            glowDot(ox, cy, orbR * 2.6f, hue, 0.45f * grow)
            glowDot(ox, cy, orbR * 1.2f, hue + 60f, 0.8f * grow, 1f, 0.7f)
            glowDot(ox, cy, orbR * 0.5f, hue + 120f, 1f * grow, 1f, 0.1f)
        }
        val bq = jseg(done, 0f, 0.8f)
        if (bq > 0f && bq < 1f) {
            for (i in 0 until 70) {
                val a = hash(i + 300) * 6.2832f
                val d = jOut(bq) * (30f + hash(i + 320) * 90f)
                glowDot(rx + pw / 2f + cos(a) * d, cy + sin(a) * d, 12f + hash(i) * 10f, hue + hash(i) * 160f, (1f - bq) * 0.9f, 1f, 0.3f)
            }
        }
    }

    val fl = sin(jseg(prog, 0.06f, 0.14f) * PI.toFloat())
    if (fl > 0f) glowDot(cx, cy, 100f, hue, 0.8f * fl, 1f, 0.3f)

    // progress hairline
    if (done <= 0f && prog > 0.1f) {
        drawRect(Color.White.copy(alpha = 0.15f), Offset(lx, top + ph + 16f), Size(rx + pw - lx, 3f))
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF5AC8FA), Color(0xFFA78BFA), Color(0xFFF472B6)), lx, rx + pw),
            Offset(lx, top + ph + 16f), Size((rx + pw - lx) * sp, 3f)
        )
    }

    if (done > 0f) {
        val b = jOut(done)
        val fade = 1f - jseg(done, 0.95f, 1f)
        glowDot(cx, cy, 110f * b, 150f, 0.55f * fade, 1f, 0.8f)
        glowDot(rx + pw / 2f, cy, 90f * b, 150f, 0.4f * fade, 1f, 0.8f)
        val r = 24f * min(1.1f, b * 1.1f)
        drawCircle(Color.White.copy(alpha = 0.92f), r, Offset(cx, cy))
        val k = r / 24f
        val green = Color(0xFF14B867)
        drawLine(green, Offset(cx - 9f * k, cy), Offset(cx - 2f * k, cy + 8f * k), 4f, StrokeCap.Round)
        drawLine(green, Offset(cx - 2f * k, cy + 8f * k), Offset(cx + 10f * k, cy - 8f * k), 4f, StrokeCap.Round)
    }
}
