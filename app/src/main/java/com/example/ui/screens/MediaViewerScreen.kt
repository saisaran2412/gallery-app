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
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.drawBehind
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

internal enum class ViewerGestureMode {
    NONE,
    PINCH_OR_PAN,
    DISMISS_VERTICAL,
    INFO_SWIPE_UP,
    PASS_TO_PAGER
}

internal data class MagicalShareBurst(
    val item: MediaItemEntity,
    val startCenter: Offset,
    val endCenter: Offset,
    val targetLabel: String
)

@Composable
fun MediaViewerScreen(
    items: List<MediaItemEntity>,
    initialMediaId: String,
    mediaTagMap: Map<String, List<TagEntity>>,
    exifMap: Map<String, ExifMetadata>,
    showLocationMetadata: Boolean,
    onBack: () -> Unit,
    onRequestExif: (MediaItemEntity) -> Unit,
    onToggleFavorite: (MediaItemEntity) -> Unit,
    onOpenEditor: (MediaItemEntity) -> Unit,
    onOpenCompress: (MediaItemEntity) -> Unit,
    onOpenSlideshow: (MediaItemEntity) -> Unit,
    onShare: (MediaItemEntity) -> Unit,
    onMoveToVault: (MediaItemEntity) -> Unit,
    onDelete: (MediaItemEntity) -> Unit,
    onUpdateNote: (String, String) -> Unit,
    onAddTag: (String, String) -> Unit,
    onRemoveTag: (String, Long) -> Unit,
    onSavePlaybackPosition: (String, Long) -> Unit,
    onOpenExternalPlayer: (MediaItemEntity) -> Unit,
    onAirShare: (MediaItemEntity) -> Unit = {},
    launchOriginRect: Rect? = null,
    onTargetedShare: (MediaItemEntity, QuickShareTarget, Boolean) -> Unit = { item, target, _ ->
        if (target.isAirShare) onAirShare(item) else onShare(item)
    }
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    val initialIndex = remember(items, initialMediaId) {
        items.indexOfFirst { it.id == initialMediaId }.coerceAtLeast(0)
    }

    if (items.isEmpty()) {
        BackHandler { onBack() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Text("Media item unavailable", color = Color.White)
        }
        return
    }

    val pagerState = rememberPagerState(initialPage = initialIndex, pageCount = { items.size })
    val coroutineScope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var isZoomed by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var showTagsNotesSheet by remember { mutableStateOf(false) }
    var isLandscapeFullscreen by remember { mutableStateOf(false) }

    // Fluid Hero Open & Close Animation (0f = thumbnail origin, 1f = full screen)
    val openProgress = remember { Animatable(0f) }
    var isClosing by remember { mutableStateOf(false) }
    var containerSize by remember { mutableStateOf(IntSize(1080, 1920)) }

    // Smooth swipe-down dismiss state
    val dismissOffsetY = remember { Animatable(0f) }

    // Long-press grab & drop top-right Smart Share Pill state
    var grabbedItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    var grabPointerRoot by remember { mutableStateOf(Offset.Zero) }
    var hoveredTargetId by remember { mutableStateOf<String?>(null) }
    var isLiteShareMode by remember { mutableStateOf(false) }
    var sharePillRefreshKey by remember { mutableIntStateOf(0) }
    var magicalBurst by remember { mutableStateOf<MagicalShareBurst?>(null) }
    val burstProgress = remember { Animatable(0f) }

    val targetBoundsMap = remember { mutableStateMapOf<String, Rect>() }
    val dropTolerancePx = with(density) { 20.dp.toPx() }
    val previewHalfPx = with(density) { 34.dp.toPx() }

    val currentItem = items.getOrNull(pagerState.currentPage) ?: items.first()
    val quickShareTargets = remember(currentItem.id, currentItem.mimeType, sharePillRefreshKey) {
        ShareTargetsHelper.resolveQuickShareTargets(context, currentItem.mimeType)
    }

    LaunchedEffect(Unit) {
        openProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.84f,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    // Favorite heart pop animation
    val favScale by animateFloatAsState(
        targetValue = if (currentItem.isFavorite) 1.2f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "fav_heart_pop"
    )

    fun triggerAnimatedClose() {
        if (isClosing) return
        isClosing = true
        coroutineScope.launch {
            openProgress.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
            )
            onBack()
        }
    }

    fun updateGrabHover(pointerRoot: Offset) {
        grabPointerRoot = pointerRoot
        val matched = quickShareTargets.firstOrNull { target ->
            val bounds = targetBoundsMap[target.id] ?: return@firstOrNull false
            bounds.inflate(dropTolerancePx).contains(pointerRoot)
        }?.id
        if (matched != null && matched != hoveredTargetId) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        hoveredTargetId = matched
    }

    fun launchMagicalShare(item: MediaItemEntity, target: QuickShareTarget, startPos: Offset) {
        val endCenter = targetBoundsMap[target.id]?.center ?: startPos
        magicalBurst = MagicalShareBurst(
            item = item,
            startCenter = startPos,
            endCenter = endCenter,
            targetLabel = target.label
        )
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        coroutineScope.launch {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
            magicalBurst = null
            sharePillRefreshKey++
            onTargetedShare(item, target, isLiteShareMode)
        }
    }

    fun finishGrab(executeDrop: Boolean) {
        val item = grabbedItem
        val targetId = hoveredTargetId
        val releasePos = grabPointerRoot
        grabbedItem = null
        hoveredTargetId = null
        if (executeDrop && item != null && targetId != null) {
            quickShareTargets.firstOrNull { it.id == targetId }?.let { target ->
                launchMagicalShare(item, target, releasePos)
            }
        }
    }

    fun handleDismissDrag(deltaY: Float) {
        if (isClosing) return
        coroutineScope.launch {
            val next = (dismissOffsetY.value + deltaY).coerceAtLeast(0f)
            dismissOffsetY.snapTo(next)
        }
    }

    fun handleDismissRelease(velocityY: Float) {
        if (isClosing) return
        coroutineScope.launch {
            if (dismissOffsetY.value > 165f || velocityY > 850f) {
                isClosing = true
                launch {
                    dismissOffsetY.animateTo(
                        targetValue = (containerSize.height * 0.65f).coerceAtLeast(640f),
                        animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                    )
                }
                openProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)
                )
                onBack()
            } else {
                dismissOffsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
        }
    }

    LaunchedEffect(currentItem.id) {
        onRequestExif(currentItem)
        // Prefetch immediate left/right neighbors for instant swipe transitions
        val neighbors = listOfNotNull(
            items.getOrNull(pagerState.currentPage - 1),
            items.getOrNull(pagerState.currentPage + 1)
        )
        if (neighbors.isNotEmpty()) {
            FolioImageLoader.prefetchThumbnails(context, neighbors)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    BackHandler {
        if (grabbedItem != null) {
            finishGrab(executeDrop = false)
        } else if (isLandscapeFullscreen) {
            isLandscapeFullscreen = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        } else {
            triggerAnimatedClose()
        }
    }

    // Animated values are read inside draw/graphics lambdas so the viewer does not
    // recompose every frame while opening, closing or dragging.
    val openOver07 by remember { derivedStateOf { openProgress.value > 0.7f } }
    val isDraggingDown by remember { derivedStateOf { dismissOffsetY.value > 6f } }

    // Compute smooth hero interpolation from origin thumbnail Rect -> full-screen container
    val heroStartScale = remember(launchOriginRect, containerSize) {
        if (launchOriginRect != null && containerSize.width > 0) {
            (launchOriginRect.width / containerSize.width.toFloat()).coerceIn(0.18f, 0.92f)
        } else 0.88f
    }
    val heroOffsetX = remember(launchOriginRect, containerSize) {
        if (launchOriginRect != null && containerSize.width > 0) {
            launchOriginRect.center.x - containerSize.width / 2f
        } else 0f
    }
    val heroOffsetY = remember(launchOriginRect, containerSize) {
        if (launchOriginRect != null && containerSize.height > 0) {
            launchOriginRect.center.y - containerSize.height / 2f
        } else 40f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it }
            .drawBehind {
                val open = openProgress.value.coerceIn(0f, 1f)
                val drag = (1f - (dismissOffsetY.value / 720f)).coerceIn(0.14f, 1f)
                drawRect(Color.Black.copy(alpha = (open * drag).coerceIn(0f, 1f)))
            }
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isZoomed && grabbedItem == null && !isDraggingDown && !isClosing,
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            key = { items[it].id },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val openFrac = openProgress.value.coerceIn(0f, 1f)
                    val dy = dismissOffsetY.value
                    val heroScale = heroStartScale + (1f - heroStartScale) * openFrac
                    val s = heroScale * (1f - (dy / 2200f)).coerceIn(0.80f, 1f)
                    translationX = heroOffsetX * (1f - openFrac)
                    translationY = heroOffsetY * (1f - openFrac) + dy
                    scaleX = s
                    scaleY = s
                    alpha = openFrac.coerceIn(0.15f, 1f)
                    shape = RoundedCornerShape(((1f - openFrac) * 24f + (dy / 14f).coerceAtMost(28f)).dp)
                    clip = true
                }
        ) { page ->
            val media = items[page]
            val isActivePage = pagerState.currentPage == page
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            val parallaxScale = (1f - abs(pageOffset).coerceIn(0f, 1f) * 0.07f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = parallaxScale
                        scaleY = parallaxScale
                    }
            ) {
                if (media.isVideo) {
                    LocalVideoPlayerPage(
                        item = media,
                        isActive = isActivePage,
                        controlsVisible = controlsVisible && !isDraggingDown && grabbedItem == null && !isClosing,
                        isLandscape = isLandscapeFullscreen,
                        onToggleControls = { controlsVisible = !controlsVisible },
                        onToggleLandscape = {
                            isLandscapeFullscreen = !isLandscapeFullscreen
                            activity?.requestedOrientation = if (isLandscapeFullscreen) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        },
                        onSavePosition = { pos -> onSavePlaybackPosition(media.id, pos) },
                        onOpenExternal = { onOpenExternalPlayer(media) },
                        onDismissDrag = ::handleDismissDrag,
                        onDismissRelease = ::handleDismissRelease,
                        onSwipeUpInfo = { showInfoSheet = true },
                        onGrabStart = { rootPos ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            grabbedItem = media
                            updateGrabHover(rootPos)
                        },
                        onGrabMove = { delta -> updateGrabHover(grabPointerRoot + delta) },
                        onGrabEnd = { finishGrab(executeDrop = true) },
                        onGrabCancel = { finishGrab(executeDrop = false) }
                    )
                } else {
                    ZoomablePhotoPage(
                        item = media,
                        isGrabbed = grabbedItem?.id == media.id,
                        onTap = { controlsVisible = !controlsVisible },
                        onZoomChanged = { zoomed -> isZoomed = zoomed },
                        onDismissDrag = ::handleDismissDrag,
                        onDismissRelease = ::handleDismissRelease,
                        onSwipeUpInfo = { showInfoSheet = true },
                        onGrabStart = { rootPos ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            grabbedItem = media
                            updateGrabHover(rootPos)
                        },
                        onGrabMove = { delta -> updateGrabHover(grabPointerRoot + delta) },
                        onGrabEnd = { finishGrab(executeDrop = true) },
                        onGrabCancel = { finishGrab(executeDrop = false) }
                    )
                }
            }
        }

        // Top Liquid Glass HUD Bar
        AnimatedVisibility(
            visible = controlsVisible && !isDraggingDown && grabbedItem == null && !isClosing && openOver07,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    onClick = ::triggerAnimatedClose,
                    shape = CircleShape,
                    color = Color(0xFF1A1D24).copy(alpha = 0.62f),
                    modifier = Modifier
                        .size(44.dp)
                        .minimumInteractiveComponentSize()
                        .liquidGlassRim(
                            cornerRadius = 22.dp,
                            specularTopLeft = Color.White.copy(alpha = 0.42f),
                            specularBottomRight = Color.White.copy(alpha = 0.12f),
                            sheenColor = Color.White.copy(alpha = 0.16f)
                        )
                        .testTag("viewer_back_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF1A1D24).copy(alpha = 0.56f),
                    modifier = Modifier
                        .weight(1f)
                        .liquidGlassRim(
                            cornerRadius = 24.dp,
                            specularTopLeft = Color.White.copy(alpha = 0.35f),
                            specularBottomRight = Color.White.copy(alpha = 0.10f),
                            sheenColor = Color.White.copy(alpha = 0.12f)
                        )
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)) {
                        Text(
                            text = currentItem.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${pagerState.currentPage + 1}/${items.size} • ${Formatters.formatFullDate(currentItem.dateTakenMillis)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1A1D24).copy(alpha = 0.62f),
                    modifier = Modifier.liquidGlassRim(
                        cornerRadius = 24.dp,
                        specularTopLeft = Color.White.copy(alpha = 0.42f),
                        specularBottomRight = Color.White.copy(alpha = 0.12f),
                        sheenColor = Color.White.copy(alpha = 0.16f)
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleFavorite(currentItem)
                            },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("viewer_favorite_button")
                        ) {
                            Icon(
                                imageVector = if (currentItem.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (currentItem.isFavorite) Color(0xFFFF6B8B) else Color.White,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = favScale
                                    scaleY = favScale
                                }
                            )
                        }
                        IconButton(
                            onClick = { showInfoSheet = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("viewer_info_button")
                        ) {
                            Icon(Icons.Outlined.Info, contentDescription = "Info", tint = Color.White)
                        }
                    }
                }
            }
        }

        // Top-right Liquid Glass Smart Share Pill triggered on long-press grab
        AnimatedVisibility(
            visible = grabbedItem != null,
            enter = scaleIn(
                initialScale = 0.72f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessMedium)
            ) + fadeIn(tween(140)),
            exit = scaleOut(
                targetScale = 0.78f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(120)
            ) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 12.dp, end = 12.dp)
                .zIndex(30f)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF161A22).copy(alpha = 0.78f),
                    shadowElevation = 18.dp,
                    modifier = Modifier
                        .liquidGlassRim(
                            cornerRadius = 30.dp,
                            specularTopLeft = Color.White.copy(alpha = 0.55f),
                            specularBottomRight = Color(0xFF0A84FF).copy(alpha = 0.35f),
                            sheenColor = Color.White.copy(alpha = 0.18f)
                        )
                        .testTag("viewer_quick_share_pill")
                ) {
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        quickShareTargets.forEach { target ->
                            val isHovered = hoveredTargetId == target.id
                            val itemScale by animateFloatAsState(
                                targetValue = if (isHovered) 1.24f else 1f,
                                animationSpec = spring(dampingRatio = 0.58f, stiffness = Spring.StiffnessMedium),
                                label = "share_target_scale"
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier
                                    .width(48.dp)
                                    .graphicsLayer {
                                        scaleX = itemScale
                                        scaleY = itemScale
                                    }
                                    .onGloballyPositioned { coords ->
                                        targetBoundsMap[target.id] = coords.boundsInRoot()
                                    }
                                    .clickable {
                                        grabbedItem?.let { item ->
                                            grabbedItem = null
                                            hoveredTargetId = null
                                            launchMagicalShare(
                                                item = item,
                                                target = target,
                                                startPos = targetBoundsMap[target.id]?.center ?: grabPointerRoot
                                            )
                                        }
                                    }
                                    .pointerInput(target.id) {
                                        androidx.compose.foundation.gestures.detectTapGestures(
                                            onLongPress = {
                                                if (grabbedItem == null && target.packageName != null) {
                                                    ShareTargetsHelper.togglePin(context, target.packageName)
                                                    sharePillRefreshKey++
                                                }
                                            }
                                        )
                                    }
                                    .testTag("share_target_${target.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isHovered -> Color(0xFF0A84FF)
                                                target.isAirShare -> Color(0xFFEA9445).copy(alpha = 0.28f)
                                                target.isCopyClipboard -> Color(0xFF34C759).copy(alpha = 0.24f)
                                                else -> Color.White.copy(alpha = 0.14f)
                                            }
                                        )
                                        .border(
                                            width = if (isHovered) 2.dp else 0.8.dp,
                                            color = if (isHovered) Color.White else Color.White.copy(alpha = 0.28f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        target.iconBitmap != null -> {
                                            Image(
                                                bitmap = target.iconBitmap,
                                                contentDescription = target.label,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                            )
                                        }
                                        target.isAirShare -> {
                                            Icon(
                                                imageVector = Icons.Outlined.NearMe,
                                                contentDescription = target.label,
                                                tint = if (isHovered) Color.White else Color(0xFFEA9445),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        target.isCopyClipboard -> {
                                            Icon(
                                                imageVector = Icons.Outlined.ContentCopy,
                                                contentDescription = target.label,
                                                tint = if (isHovered) Color.White else Color(0xFF6EE7B7),
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        else -> {
                                            Icon(
                                                imageVector = Icons.Outlined.Share,
                                                contentDescription = target.label,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = target.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isHovered) Color(0xFF7CC4FF) else Color.White.copy(alpha = 0.9f),
                                    fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Smart Share Controls Row: HD vs Lite + Drag Hint
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = {
                            isLiteShareMode = !isLiteShareMode
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        shape = RoundedCornerShape(50),
                        color = if (isLiteShareMode) {
                            Color(0xFF0A84FF).copy(alpha = 0.88f)
                        } else {
                            Color(0xFF161A22).copy(alpha = 0.78f)
                        },
                        modifier = Modifier
                            .liquidGlassRim(
                                cornerRadius = 20.dp,
                                specularTopLeft = Color.White.copy(alpha = 0.45f),
                                specularBottomRight = Color.White.copy(alpha = 0.14f),
                                sheenColor = Color.White.copy(alpha = 0.14f)
                            )
                            .testTag("share_mode_toggle")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isLiteShareMode) Icons.Outlined.Bolt else Icons.Outlined.HighQuality,
                                contentDescription = "Share Quality Mode",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isLiteShareMode) "Lite • Fast" else "HD • Original",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.72f)
                    ) {
                        val hoveredLabel = quickShareTargets.firstOrNull { it.id == hoveredTargetId }?.label
                        Text(
                            text = if (hoveredLabel != null) "Drop to send via $hoveredLabel" else "Drag onto an app",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hoveredLabel != null) Color(0xFF7CC4FF) else Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Floating dragged thumbnail following user's finger
        val activeGrab = grabbedItem
        if (activeGrab != null) {
            val loader = remember(context) { FolioImageLoader.get(context) }
            Box(
                modifier = Modifier
                    .zIndex(40f)
                    .offset {
                        IntOffset(
                            (grabPointerRoot.x - previewHalfPx).roundToInt(),
                            (grabPointerRoot.y - previewHalfPx).roundToInt()
                        )
                    }
                    .size(68.dp)
                    .graphicsLayer {
                        rotationZ = if (hoveredTargetId != null) 0f else -5f
                        scaleX = if (hoveredTargetId != null) 0.84f else 1.04f
                        scaleY = if (hoveredTargetId != null) 0.84f else 1.04f
                    }
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 2.dp,
                        color = if (hoveredTargetId != null) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .testTag("viewer_grabbed_thumbnail")
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(activeGrab.uriString))
                        .size(180)
                        .build(),
                    imageLoader = loader,
                    contentDescription = activeGrab.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Magical Share Launch Shockwave & Flying Particle Burst
        val burst = magicalBurst
        if (burst != null) {
            val p = burstProgress.value
            val loader = remember(context) { FolioImageLoader.get(context) }
            val currentPos = Offset(
                x = burst.startCenter.x + (burst.endCenter.x - burst.startCenter.x) * p,
                y = burst.startCenter.y + (burst.endCenter.y - burst.startCenter.y) * p
            )
            val flyScale = (1f - p * 0.85f).coerceAtLeast(0.12f)

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(45f)
            ) {
                val ringRadius = (24.dp.toPx() + 92.dp.toPx() * p)
                val ringAlpha = (1f - p).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFF7CC4FF).copy(alpha = ringAlpha * 0.85f),
                    radius = ringRadius,
                    center = burst.endCenter,
                    style = Stroke(width = (4.dp.toPx() * (1f - p * 0.6f)))
                )
                drawCircle(
                    color = Color.White.copy(alpha = ringAlpha * 0.45f),
                    radius = ringRadius * 0.68f,
                    center = burst.endCenter,
                    style = Stroke(width = 2.dp.toPx())
                )
                // 10 radial optical sparkles
                for (i in 0 until 10) {
                    val angle = (i * 36.0) * PI / 180.0
                    val dist = (16.dp.toPx() + 74.dp.toPx() * p)
                    val px = burst.endCenter.x + (cos(angle) * dist).toFloat()
                    val py = burst.endCenter.y + (sin(angle) * dist).toFloat()
                    drawCircle(
                        color = if (i % 2 == 0) Color(0xFF7CC4FF) else Color(0xFFFFD180),
                        radius = (4.5.dp.toPx() * (1f - p)).coerceAtLeast(1f),
                        center = Offset(px, py),
                        alpha = ringAlpha
                    )
                }
            }

            Box(
                modifier = Modifier
                    .zIndex(46f)
                    .offset {
                        IntOffset(
                            (currentPos.x - previewHalfPx).roundToInt(),
                            (currentPos.y - previewHalfPx).roundToInt()
                        )
                    }
                    .size(68.dp)
                    .graphicsLayer {
                        scaleX = flyScale
                        scaleY = flyScale
                        alpha = (1f - p * 0.7f).coerceIn(0f, 1f)
                    }
                    .clip(RoundedCornerShape(16.dp))
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(burst.item.uriString))
                        .size(160)
                        .build(),
                    imageLoader = loader,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Bottom Liquid Glass Action Dock + Filmstrip Scrubber
        AnimatedVisibility(
            visible = controlsVisible && !isLandscapeFullscreen && !isDraggingDown && grabbedItem == null && !isClosing && openOver07,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (items.size > 1 && !currentItem.isVideo) {
                    val loader = remember(context) { FolioImageLoader.get(context) }
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(items, key = { _, it -> "strip_${it.id}" }) { index, media ->
                            val isActive = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(width = if (isActive) 50.dp else 44.dp, height = 46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = if (isActive) Color(0xFF0A84FF) else Color.White.copy(alpha = 0.28f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        coroutineScope.launch { pagerState.animateScrollToPage(index) }
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(Uri.parse(media.uriString))
                                        .size(120)
                                        .precision(Precision.INEXACT)
                                        .memoryCacheKey("thumb_${media.id}_${media.dateModifiedMillis}")
                                        .crossfade(false)
                                        .build(),
                                    imageLoader = loader,
                                    contentDescription = media.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = Color(0xFF171A21).copy(alpha = 0.68f),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .liquidGlassRim(
                            cornerRadius = 30.dp,
                            specularTopLeft = Color.White.copy(alpha = 0.40f),
                            specularBottomRight = Color.White.copy(alpha = 0.12f),
                            sheenColor = Color.White.copy(alpha = 0.14f)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ViewerDockButton(Icons.Outlined.NearMe, "AirShare", { onAirShare(currentItem) }, "viewer_airshare")
                        ViewerDockButton(Icons.Outlined.Share, "Share", { onShare(currentItem) }, "viewer_share")
                        if (!currentItem.isVideo) {
                            ViewerDockButton(Icons.Outlined.Edit, "Edit", { onOpenEditor(currentItem) }, "viewer_edit")
                            ViewerDockButton(Icons.Outlined.Compress, "Compress", { onOpenCompress(currentItem) }, "viewer_compress")
                        } else {
                            ViewerDockButton(Icons.Outlined.Slideshow, "Slide", { onOpenSlideshow(currentItem) }, "viewer_slide")
                        }
                        ViewerDockButton(Icons.Outlined.Label, "Tags", { showTagsNotesSheet = true }, "viewer_tags")
                        ViewerDockButton(Icons.Outlined.Lock, "Vault", { onMoveToVault(currentItem) }, "viewer_vault")
                        ViewerDockButton(Icons.Outlined.Delete, "Trash", { onDelete(currentItem) }, "viewer_delete")
                    }
                }
            }
        }
    }

    if (showInfoSheet) {
        MediaInfoBottomSheet(
            item = currentItem,
            exif = exifMap[currentItem.id],
            showLocation = showLocationMetadata,
            onDismiss = { showInfoSheet = false }
        )
    }

    if (showTagsNotesSheet) {
        TagsAndNoteBottomSheet(
            item = currentItem,
            tags = mediaTagMap[currentItem.id].orEmpty(),
            onDismiss = { showTagsNotesSheet = false },
            onSaveNote = { note -> onUpdateNote(currentItem.id, note) },
            onAddTag = { tagName -> onAddTag(currentItem.id, tagName) },
            onRemoveTag = { tagId -> onRemoveTag(currentItem.id, tagId) }
        )
    }
}
