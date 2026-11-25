package com.example.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.example.core.util.FolioImageLoader
import com.example.core.util.Formatters
import com.example.data.local.MediaItemEntity
import com.example.data.local.TagEntity
import com.example.data.scanner.ExifMetadata
import com.example.share.QuickShareTarget
import com.example.share.ShareTargetsHelper
import com.example.ui.components.liquidGlassRim
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
internal fun ZoomablePhotoPage(
    item: MediaItemEntity,
    isGrabbed: Boolean,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: (Float) -> Unit,
    onSwipeUpInfo: () -> Unit,
    onGrabStart: (Offset) -> Unit,
    onGrabMove: (Offset) -> Unit,
    onGrabEnd: () -> Unit,
    onGrabCancel: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val coroutineScope = rememberCoroutineScope()

    val scaleAnim = remember(item.id) { Animatable(1f) }
    val offsetAnim = remember(item.id) { Animatable(Offset.Zero, Offset.VectorConverter) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    val grabDimScale by animateFloatAsState(
        targetValue = if (isGrabbed) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "grab_dim_scale"
    )

    LaunchedEffect(scaleAnim.value) {
        onZoomChanged(scaleAnim.value > 1.02f)
    }

    fun clampOffset(raw: Offset, scale: Float): Offset {
        if (scale <= 1.02f || boxSize.width == 0 || boxSize.height == 0) return Offset.Zero
        val maxX = ((boxSize.width * (scale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((boxSize.height * (scale - 1f)) / 2f).coerceAtLeast(0f)
        return Offset(
            x = raw.x.coerceIn(-maxX, maxX),
            y = raw.y.coerceIn(-maxY, maxY)
        )
    }

    val thumbKey = "thumb_${item.id}_${item.dateModifiedMillis}"
    val imageRequest = remember(item.id, item.dateModifiedMillis) {
        ImageRequest.Builder(context)
            .data(Uri.parse(item.uriString))
            .size(2048)
            .precision(Precision.INEXACT)
            .placeholderMemoryCacheKey(thumbKey)
            .memoryCacheKey("viewer_${item.id}_${item.dateModifiedMillis}")
            .crossfade(false)
            .build()
    }

    // True from the moment a long-press drag-to-share starts until it ends: the swipe-up-info and
    // swipe-down-dismiss handler must stay out of the way so they never steal the drag.
    val grabbing = remember { BooleanArray(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = it }
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tapOffset ->
                        coroutineScope.launch {
                            val zoomAnimSpec = spring<Float>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            val panAnimSpec = spring<Offset>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            if (scaleAnim.value > 1.2f) {
                                launch { scaleAnim.animateTo(1f, zoomAnimSpec) }
                                launch { offsetAnim.animateTo(Offset.Zero, panAnimSpec) }
                            } else {
                                val targetScale = 2.5f
                                val center = Offset(boxSize.width / 2f, boxSize.height / 2f)
                                val deltaFromCenter = tapOffset - center
                                val targetOffset = clampOffset(
                                    Offset(-deltaFromCenter.x * (targetScale - 1f), -deltaFromCenter.y * (targetScale - 1f)),
                                    targetScale
                                )
                                launch { scaleAnim.animateTo(targetScale, zoomAnimSpec) }
                                launch { offsetAnim.animateTo(targetOffset, panAnimSpec) }
                            }
                        }
                    }
                )
            }
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        if (scaleAnim.value <= 1.05f) {
                            grabbing[0] = true
                            onGrabStart(originInRoot + startOffset)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (grabbing[0]) onGrabMove(dragAmount)
                    },
                    onDragEnd = { grabbing[0] = false; onGrabEnd() },
                    onDragCancel = { grabbing[0] = false; onGrabCancel() }
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val touchSlop = viewConfiguration.touchSlop
                    val velocityTracker = VelocityTracker()
                    var mode = ViewerGestureMode.NONE
                    var totalDx = 0f
                    var totalDy = 0f
                    var triggeredInfoSwipeUp = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressedChanges = event.changes.filter { it.pressed }
                        if (pressedChanges.isEmpty()) break
                        if (grabbing[0]) { mode = ViewerGestureMode.NONE; totalDx = 0f; totalDy = 0f; continue }

                        if (pressedChanges.size >= 2) {
                            if (mode == ViewerGestureMode.DISMISS_VERTICAL) {
                                onDismissRelease(0f)
                            }
                            mode = ViewerGestureMode.PINCH_OR_PAN
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val nextScale = (scaleAnim.value * zoom).coerceIn(1f, 5f)
                            val nextOffset = clampOffset(offsetAnim.value + pan, nextScale)
                            coroutineScope.launch {
                                scaleAnim.snapTo(nextScale)
                                offsetAnim.snapTo(nextOffset)
                            }
                            event.changes.forEach { it.consume() }
                        } else {
                            val change = pressedChanges.first()
                            val delta = change.positionChange()

                            if (scaleAnim.value > 1.02f) {
                                mode = ViewerGestureMode.PINCH_OR_PAN
                                val nextOffset = clampOffset(offsetAnim.value + delta, scaleAnim.value)
                                coroutineScope.launch { offsetAnim.snapTo(nextOffset) }
                                change.consume()
                            } else {
                                totalDx += delta.x
                                totalDy += delta.y
                                if (mode == ViewerGestureMode.NONE) {
                                    if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                        mode = when {
                                            totalDy > 0f && abs(totalDy) > abs(totalDx) -> ViewerGestureMode.DISMISS_VERTICAL
                                            totalDy < 0f && abs(totalDy) > abs(totalDx) * 1.2f -> ViewerGestureMode.INFO_SWIPE_UP
                                            else -> ViewerGestureMode.PASS_TO_PAGER
                                        }
                                    }
                                }

                                when (mode) {
                                    ViewerGestureMode.DISMISS_VERTICAL -> {
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        onDismissDrag(delta.y)
                                        change.consume()
                                    }
                                    ViewerGestureMode.INFO_SWIPE_UP -> {
                                        if (!triggeredInfoSwipeUp && totalDy < -110f) {
                                            triggeredInfoSwipeUp = true
                                            onSwipeUpInfo()
                                        }
                                        change.consume()
                                    }
                                    ViewerGestureMode.PINCH_OR_PAN -> change.consume()
                                    ViewerGestureMode.PASS_TO_PAGER, ViewerGestureMode.NONE -> {
                                        // Leave unconsumed so HorizontalPager smoothly handles left/right swipes
                                    }
                                }
                            }
                        }
                    }

                    if (mode == ViewerGestureMode.DISMISS_VERTICAL) {
                        val velY = runCatching { velocityTracker.calculateVelocity().y }.getOrDefault(0f)
                        onDismissRelease(velY)
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = imageRequest,
            imageLoader = imageLoader,
            contentDescription = item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val effectiveScale = scaleAnim.value * grabDimScale
                    scaleX = effectiveScale
                    scaleY = effectiveScale
                    translationX = offsetAnim.value.x
                    translationY = offsetAnim.value.y
                    alpha = if (isGrabbed) 0.50f else 1f
                }
        )
    }
}

@Composable
internal fun LocalVideoPlayerPage(
    item: MediaItemEntity,
    isActive: Boolean,
    controlsVisible: Boolean,
    isLandscape: Boolean,
    onToggleControls: () -> Unit,
    onToggleLandscape: () -> Unit,
    onSavePosition: (Long) -> Unit,
    onOpenExternal: () -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissRelease: (Float) -> Unit,
    onSwipeUpInfo: () -> Unit,
    onGrabStart: (Offset) -> Unit,
    onGrabMove: (Offset) -> Unit,
    onGrabEnd: () -> Unit,
    onGrabCancel: () -> Unit
) {
    val context = LocalContext.current
    var isPlaying by remember(item.id) { mutableStateOf(false) }
    var isMuted by remember(item.id) { mutableStateOf(false) }
    var currentPosMs by remember(item.id) { mutableLongStateOf(item.playbackPositionMs) }
    var durationMs by remember(item.id) { mutableLongStateOf(item.durationMillis.coerceAtLeast(1L)) }
    var playerError by remember(item.id) { mutableStateOf<String?>(null) }
    var originInRoot by remember { mutableStateOf(Offset.Zero) }

    val exoPlayer = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(item.uriString)))
            prepare()
            if (item.playbackPositionMs > 0L) {
                seekTo(item.playbackPositionMs)
            }
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && exoPlayer.duration > 0L) {
                    durationMs = exoPlayer.duration
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playerError = error.localizedMessage ?: "Unsupported video codec or container"
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            onSavePosition(exoPlayer.currentPosition)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.pause()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        exoPlayer.pause()
        onSavePosition(exoPlayer.currentPosition)
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            currentPosMs = exoPlayer.currentPosition
            if (exoPlayer.duration > 0L) durationMs = exoPlayer.duration
            delay(250L)
        }
    }

    val grabbing = remember { BooleanArray(1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { originInRoot = it.positionInRoot() }
            .pointerInput(item.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        grabbing[0] = true
                        onGrabStart(originInRoot + startOffset)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onGrabMove(dragAmount)
                    },
                    onDragEnd = { grabbing[0] = false; onGrabEnd() },
                    onDragCancel = { grabbing[0] = false; onGrabCancel() }
                )
            }
            .pointerInput(item.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    val touchSlop = viewConfiguration.touchSlop
                    val velocityTracker = VelocityTracker()
                    var mode = ViewerGestureMode.NONE
                    var totalDx = 0f
                    var totalDy = 0f
                    var triggeredInfoSwipeUp = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (grabbing[0]) { mode = ViewerGestureMode.NONE; totalDx = 0f; totalDy = 0f; continue }
                        val change = pressed.first()
                        val delta = change.positionChange()
                        totalDx += delta.x
                        totalDy += delta.y
                        if (mode == ViewerGestureMode.NONE) {
                            if (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop) {
                                mode = when {
                                    totalDy > 0f && abs(totalDy) > abs(totalDx) -> ViewerGestureMode.DISMISS_VERTICAL
                                    totalDy < 0f && abs(totalDy) > abs(totalDx) * 1.2f -> ViewerGestureMode.INFO_SWIPE_UP
                                    else -> ViewerGestureMode.PASS_TO_PAGER
                                }
                            }
                        }
                        when (mode) {
                            ViewerGestureMode.DISMISS_VERTICAL -> {
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                onDismissDrag(delta.y)
                                change.consume()
                            }
                            ViewerGestureMode.INFO_SWIPE_UP -> {
                                if (!triggeredInfoSwipeUp && totalDy < -110f) {
                                    triggeredInfoSwipeUp = true
                                    onSwipeUpInfo()
                                }
                                change.consume()
                            }
                            else -> Unit
                        }
                    }

                    if (mode == ViewerGestureMode.DISMISS_VERTICAL) {
                        val velY = runCatching { velocityTracker.calculateVelocity().y }.getOrDefault(0f)
                        onDismissRelease(velY)
                    }
                }
            }
            .clickable(onClick = onToggleControls),
        contentAlignment = Alignment.Center
    ) {
        if (playerError != null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Cannot Play Locally", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = playerError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onOpenExternal,
                        modifier = Modifier.testTag("open_external_player_button")
                    ) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open with External Player")
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = exoPlayer
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { view -> view.player = exoPlayer },
                modifier = Modifier.fillMaxSize()
            )

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.58f))
                            .testTag("video_play_pause_button")
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = if (isLandscape) 20.dp else 92.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF161A22).copy(alpha = 0.72f))
                            .liquidGlassRim(
                                cornerRadius = 18.dp,
                                specularTopLeft = Color.White.copy(alpha = 0.35f),
                                specularBottomRight = Color.White.copy(alpha = 0.12f),
                                sheenColor = Color.White.copy(alpha = 0.12f)
                            )
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Slider(
                            value = currentPosMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                            onValueChange = { newPos ->
                                currentPosMs = newPos.toLong()
                                exoPlayer.seekTo(currentPosMs)
                            },
                            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${Formatters.formatDuration(currentPosMs)} / ${Formatters.formatDuration(durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White
                                    )
                                }
                                IconButton(
                                    onClick = onToggleLandscape,
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(
                                        imageVector = if (isLandscape) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

