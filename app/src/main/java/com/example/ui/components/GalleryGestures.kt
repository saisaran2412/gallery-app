package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.share.QuickShareTarget
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.FolioImageLoader
import com.example.data.local.MediaItemEntity
import com.example.ui.viewmodel.TimelineSection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Pinch the timeline to move between day, month and year groupings. */
enum class TimelineZoom(val label: String, val extraColumns: Int) {
    DAY("Days", 0),
    MONTH("Months", 1),
    YEAR("Years", 2);

    fun zoomedOut(): TimelineZoom = entries[(ordinal + 1).coerceAtMost(entries.lastIndex)]
    fun zoomedIn(): TimelineZoom = entries[(ordinal - 1).coerceAtLeast(0)]
}

/** Regroups already-sorted day sections into month or year sections, keeping order. */
fun regroupSections(sections: List<TimelineSection>, zoom: TimelineZoom): List<TimelineSection> {
    if (zoom == TimelineZoom.DAY) return sections
    val fmt = SimpleDateFormat(if (zoom == TimelineZoom.MONTH) "MMMM yyyy" else "yyyy", Locale.getDefault())
    val out = LinkedHashMap<String, MutableList<MediaItemEntity>>()
    sections.forEach { s ->
        s.items.forEach { item ->
            out.getOrPut(fmt.format(Date(item.dateTakenMillis))) { mutableListOf() }.add(item)
        }
    }
    return out.map { (title, items) -> TimelineSection(title, items) }
}

/**
 * Two-finger pinch switches [TimelineZoom]. Observes in the Initial pass and never consumes,
 * so normal scrolling and taps keep working.
 */
fun Modifier.pinchToChangeZoom(
    onPinchIn: () -> Unit,
    onPinchOut: () -> Unit
): Modifier = this.pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var startSpan = 0f
        var fired = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty()) break
            if (pressed.size >= 2 && !fired) {
                val span = (pressed[0].position - pressed[1].position).getDistance()
                if (startSpan == 0f) startSpan = span
                else if (startSpan > 0f) {
                    val ratio = span / startSpan
                    if (ratio < 0.75f) { fired = true; onPinchIn() }
                    else if (ratio > 1.35f) { fired = true; onPinchOut() }
                }
            }
        }
    }
}

/** Live pinch state shared between the gesture and the grid tiles (scale 1 = idle). */
class LivePinchState {
    var scale by androidx.compose.runtime.mutableFloatStateOf(1f)
    var originX by androidx.compose.runtime.mutableFloatStateOf(0f)
    var originY by androidx.compose.runtime.mutableFloatStateOf(0f)
}

/**
 * Applies the live pinch to ONE grid cell: scales it about its own centre and shifts it so the whole
 * grid appears to zoom about the pinch point. Only photo tiles and section titles use this, so the
 * top bar, filter chips and bottom nav never move.
 */
fun Modifier.pinchCell(state: LivePinchState, gridState: LazyGridState, key: Any): Modifier = graphicsLayer {
    val s = state.scale
    if (s == 1f) return@graphicsLayer
    val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return@graphicsLayer
    val cx = info.offset.x + info.size.width / 2f
    val cy = info.offset.y + info.size.height / 2f
    scaleX = s
    scaleY = s
    translationX = (s - 1f) * (cx - state.originX)
    translationY = (s - 1f) * (cy - state.originY)
}

/**
 * iPhone Photos style live pinch detector. While two fingers are down [state] follows the pinch;
 * crossing a threshold steps the column count (via [onStep]) and the leftover scale keeps the
 * motion continuous. On release it springs back to 1x. Never consumes events, so scrolling,
 * taps and drag-to-share keep working. Put the returned Modifier on the grid, and give the tiles
 * [pinchCell].
 */
@Composable
fun rememberLivePinchZoom(
    state: LivePinchState,
    columnsNow: () -> Int,
    canZoomIn: () -> Boolean,
    canZoomOut: () -> Boolean,
    onStep: (zoomIn: Boolean) -> Unit
): Modifier {
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val cols by rememberUpdatedState(columnsNow)
    val canIn by rememberUpdatedState(canZoomIn)
    val canOut by rememberUpdatedState(canZoomOut)
    val step by rememberUpdatedState(onStep)
    val anim = androidx.compose.runtime.remember { arrayOfNulls<kotlinx.coroutines.Job>(1) }

    return Modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            anim[0]?.cancel()
            var startSpan = 0f
            var cols0 = 0
            var waitCols = -1
            var pinching = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isEmpty()) break
                if (pressed.size >= 2) {
                    val span = (pressed[0].position - pressed[1].position).getDistance()
                    if (startSpan == 0f) {
                        startSpan = span
                        cols0 = cols()
                        val mid = (pressed[0].position + pressed[1].position) / 2f
                        state.originX = mid.x
                        state.originY = mid.y
                    } else if (span > 0f) {
                        pinching = true
                        val now = cols().coerceAtLeast(1)
                        if (waitCols >= 0 && now != waitCols) waitCols = -1
                        val layoutFactor = cols0.toFloat() / now
                        val residual = (span / startSpan) / layoutFactor
                        if (waitCols < 0) {
                            if (residual > 1.28f && canIn()) {
                                waitCols = now; step(true)
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            } else if (residual < 0.78f && canOut()) {
                                waitCols = now; step(false)
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }
                        }
                        val blocked = (residual > 1f && !canIn()) || (residual < 1f && !canOut())
                        state.scale = if (blocked) 1f + (residual - 1f) * 0.25f else residual.coerceIn(0.7f, 1.45f)
                    }
                }
            }
            if (pinching || state.scale != 1f) {
                val from = state.scale
                anim[0] = scope.launch {
                    androidx.compose.animation.core.animate(
                        from, 1f,
                        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 380f)
                    ) { v, _ -> state.scale = v }
                }
            }
        }
    }
}

/**
 * Drag horizontally across thumbnails while in selection mode to select many at once.
 * Vertical drags still scroll. Edges auto-scroll the grid.
 */
fun Modifier.swipeToSelect(
    enabled: Boolean,
    gridState: LazyGridState,
    scope: CoroutineScope,
    itemForKey: (Any) -> MediaItemEntity?,
    onSelect: (MediaItemEntity) -> Unit
): Modifier = if (!enabled) this else this.pointerInput(gridState, enabled) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var active = false
        val seen = HashSet<String>()
        fun hit(p: Offset) {
            val info = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
                p.x >= it.offset.x && p.x < it.offset.x + it.size.width &&
                    p.y >= it.offset.y && p.y < it.offset.y + it.size.height
            } ?: return
            val media = itemForKey(info.key) ?: return
            if (seen.add(media.id)) onSelect(media)
        }
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            if (event.changes.size > 1) break
            val dx = change.position.x - down.position.x
            val dy = change.position.y - down.position.y
            if (!active && abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.4f) {
                active = true
                hit(down.position)
            }
            if (active) {
                change.consume()
                hit(change.position)
                val h = size.height
                val edge = 96f
                if (change.position.y < edge) scope.launch { gridState.scrollBy(-28f) }
                else if (change.position.y > h - edge) scope.launch { gridState.scrollBy(28f) }
            }
        }
    }
}

/**
 * Hold a thumbnail longer than a normal long-press (700 ms) to peek at it. Releasing dismisses.
 * Observes in the Initial pass and does not consume.
 */
fun Modifier.peekOnHold(enabledAtDown: () -> Boolean, onPeek: (Boolean) -> Unit): Modifier =
    this.peekImpl(enabledAtDown, onPeek)

private fun Modifier.peekImpl(enabledAtDown: () -> Boolean, onPeek: (Boolean) -> Unit): Modifier = this.pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop * 2
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!enabledAtDown()) return@awaitEachGesture
        val r = withTimeoutOrNull(700L) {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || (c.position - down.position).getDistance() > slop) break
            }
        }
        if (r == null) {
            onPeek(true)
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                if (e.changes.none { it.pressed }) break
            }
            onPeek(false)
        }
    }
}

@Composable
fun PeekPreviewOverlay(item: MediaItemEntity?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = item != null,
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(120))
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)))
        }
        AnimatedVisibility(
            visible = item != null,
            enter = scaleIn(tween(220), initialScale = 0.8f) + fadeIn(tween(160)),
            exit = scaleOut(tween(140), targetScale = 0.9f) + fadeOut(tween(120))
        ) {
            if (item != null) {
                Box(
                    modifier = Modifier
                        .padding(28.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(android.net.Uri.parse(item.uriString)).size(1080).build(),
                        imageLoader = FolioImageLoader.get(context),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = item.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}


/**
 * Press and hold an already-selected thumbnail (450 ms) to start dragging the whole selection.
 * [armedAtDown] is read once at touch-down so selecting on first long-press doesn't arm it.
 * Positions are reported in root coordinates.
 */
fun Modifier.holdToDragShare(
    armedAtDown: () -> Boolean,
    tileTopLeft: () -> Offset,
    onStart: (Offset) -> Unit,
    onMove: (Offset) -> Unit,
    onEnd: (Offset) -> Unit
): Modifier = this.pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop * 1.5f
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (!armedAtDown()) return@awaitEachGesture
        val held = withTimeoutOrNull(450L) {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Initial)
                val c = e.changes.firstOrNull { it.id == down.id } ?: break
                if (!c.pressed || (c.position - down.position).getDistance() > slop) break
            }
        }
        if (held != null) return@awaitEachGesture
        var last = tileTopLeft() + down.position
        onStart(last)
        while (true) {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val c = e.changes.firstOrNull { it.id == down.id } ?: break
            c.consume()
            last = tileTopLeft() + c.position
            if (!c.pressed) break
            onMove(last)
        }
        onEnd(last)
    }
}

/** Dock of share targets shown while a multi-item drag is in progress. */
@Composable
fun MultiShareDock(
    targets: List<QuickShareTarget>,
    hoveredId: String?,
    onTargetBounds: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(30.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
    ) {
        targets.forEach { t ->
            val hot = hoveredId == t.id
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(56.dp)
                    .onGloballyPositioned { onTargetBounds(t.id, it.boundsInRoot()) }
            ) {
                Box(
                    modifier = Modifier
                        .size(if (hot) 52.dp else 44.dp)
                        .clip(CircleShape)
                        .background(if (hot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (t.iconBitmap != null) {
                        Image(bitmap = t.iconBitmap, contentDescription = t.label, modifier = Modifier.size(30.dp))
                    } else {
                        Text(t.label.take(1), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    t.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.sp
                )
            }
        }
    }
}
