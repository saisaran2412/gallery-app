package com.example.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FolderCopy
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import coil.size.Precision
import coil.size.Scale
import com.example.core.util.FolioImageLoader
import com.example.core.util.Formatters
import com.example.data.local.AlbumSummary
import com.example.data.local.MediaItemEntity
import com.example.ui.theme.LocalFolioTokens
import com.example.ui.viewmodel.TimelineSection
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun MixedMediaTimelineGrid(
    sections: List<TimelineSection>,
    columns: Int,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    onItemClick: (MediaItemEntity) -> Unit,
    onItemLongPress: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState(),
    headerContent: (@Composable () -> Unit)? = null,
    tileAspectRatio: Float = 0.84f,
    contentPadding: PaddingValues = PaddingValues(start = 12.dp, end = 12.dp, bottom = 104.dp),
    onItemClickWithBounds: ((MediaItemEntity, Rect) -> Unit)? = null,
    onPeek: ((MediaItemEntity?) -> Unit)? = null,
    swipeSelectEnabled: Boolean = true,
    onShareDragStart: ((Offset) -> Unit)? = null,
    onShareDragMove: ((Offset) -> Unit)? = null,
    onShareDragEnd: ((Offset) -> Unit)? = null,
    pinch: LivePinchState? = null
) {
    val context = LocalContext.current
    val flatItems = remember(sections) { sections.flatMap { it.items } }
    val itemsById = remember(flatItems) { flatItems.associateBy { it.id } }
    val gridScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(gridState.firstVisibleItemIndex, flatItems) {
        if (flatItems.isEmpty()) return@LaunchedEffect
        val start = gridState.firstVisibleItemIndex.coerceIn(0, flatItems.lastIndex)
        val end = (start + columns * 6).coerceAtMost(flatItems.size)
        if (start < end) {
            FolioImageLoader.prefetchThumbnails(context, flatItems.subList(start, end))
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = modifier
            .fillMaxSize()
            .swipeToSelect(
                enabled = isSelectionMode && swipeSelectEnabled,
                gridState = gridState,
                scope = gridScope,
                itemForKey = { key -> (key as? String)?.let { itemsById[it] } },
                onSelect = { media -> if (media.id !in selectedIds) onItemLongPress(media) }
            )
            .testTag("mixed_media_lazy_grid"),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (headerContent != null) {
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "timeline_header",
                contentType = "header"
            ) {
                headerContent()
            }
        }

        sections.forEach { section ->
            item(
                span = { GridItemSpan(maxLineSpan) },
                key = "sec_${section.title}",
                contentType = "section_header"
            ) {
                Row(
                    modifier = Modifier
                        .then(if (pinch != null) Modifier.pinchCell(pinch, gridState, "sec_${section.title}") else Modifier)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${section.items.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(
                items = section.items,
                key = { it.id },
                contentType = { it.mediaType }
            ) { media ->
                MediaThumbnailTile(
                    item = media,
                    modifier = if (pinch != null) Modifier.pinchCell(pinch, gridState, media.id) else Modifier,
                    isSelected = media.id in selectedIds,
                    isSelectionMode = isSelectionMode,
                    onClick = { onItemClick(media) },
                    onClickWithBounds = onItemClickWithBounds?.let { cb ->
                        { bounds -> cb(media, bounds) }
                    },
                    onLongPress = { onItemLongPress(media) },
                    aspectRatio = tileAspectRatio,
                    onPeek = onPeek?.let { cb -> { showing: Boolean -> cb(if (showing) media else null) } },
                    onShareDragStart = onShareDragStart,
                    onShareDragMove = onShareDragMove,
                    onShareDragEnd = onShareDragEnd
                )
            }
        }
    }
}

private data class CalendarMonthBlock(
    val key: String,
    val title: String,
    val year: Int,
    val month: Int,
    val leadingEmptySlots: Int,
    val daysInMonth: Int,
    val mediaByDay: Map<Int, List<MediaItemEntity>>
)

/**
 * Month-by-month 7-column rounded photo calendar grid inspired by the Obsidian Calendar gallery.
 */
@Composable
fun MediaCalendarGrid(
    mediaItems: List<MediaItemEntity>,
    onDayClick: (Int, String, List<MediaItemEntity>) -> Unit,
    modifier: Modifier = Modifier,
    headerContent: (@Composable () -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(start = 14.dp, end = 14.dp, bottom = 104.dp),
    birthdays: List<com.example.data.local.BirthdayEntity> = emptyList()
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current

    val monthBlocks = remember(mediaItems) {
        val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val cal = Calendar.getInstance()
        mediaItems
            .groupBy { item ->
                cal.timeInMillis = item.dateTakenMillis
                cal.get(Calendar.YEAR) * 100 + cal.get(Calendar.MONTH)
            }
            .entries
            .sortedByDescending { it.key }
            .map { (monthKey, items) ->
                val year = monthKey / 100
                val month = monthKey % 100
                cal.set(year, month, 1, 0, 0, 0)
                val leadingOffset = (cal.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
                val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                val byDay = items.groupBy { item ->
                    cal.timeInMillis = item.dateTakenMillis
                    cal.get(Calendar.DAY_OF_MONTH)
                }
                CalendarMonthBlock(
                    key = "cal_$monthKey",
                    title = monthFormatter.format(Date(items.first().dateTakenMillis)),
                    year = year,
                    month = month,
                    leadingEmptySlots = leadingOffset,
                    daysInMonth = maxDay,
                    mediaByDay = byDay
                )
            }
    }

    val highlightedItemId = mediaItems.firstOrNull()?.id

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("media_calendar_grid"),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        if (headerContent != null) {
            item(key = "cal_header") {
                headerContent()
            }
        }

        items(monthBlocks, key = { it.key }) { block ->
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = block.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                val totalCells = block.leadingEmptySlots + block.daysInMonth
                val rows = (totalCells + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (rowIndex in 0 until rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (colIndex in 0 until 7) {
                                val cellIndex = rowIndex * 7 + colIndex
                                val dayNum = cellIndex - block.leadingEmptySlots + 1
                                if (dayNum !in 1..block.daysInMonth) {
                                    Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                } else {
                                    val dayMedia = block.mediaByDay[dayNum].orEmpty()
                                    val cover = dayMedia.firstOrNull()
                                    val isHighlight = cover != null && cover.id == highlightedItemId
                                    val cellShape = RoundedCornerShape(10.dp)
                                    val dayKey = block.year * 10_000 + (block.month + 1) * 100 + dayNum
                                    val dayTitle = "$dayNum ${block.title}"
                                    val birthdayNames = remember(birthdays, block.year, block.month, dayNum) {
                                        if (birthdays.isEmpty()) emptyList() else {
                                            val c = Calendar.getInstance().apply { set(block.year, block.month, dayNum, 12, 0, 0) }
                                            birthdays.filter { com.example.core.birthday.BirthdayScheduler.occursOn(it, c) }.map { it.name }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .clip(cellShape)
                                            .background(
                                                if (cover != null) cs.surfaceVariant
                                                else cs.surfaceVariant.copy(alpha = 0.28f)
                                            )
                                            .border(
                                                width = if (isHighlight) 2.dp else 1.dp,
                                                color = when {
                                                    isHighlight -> cs.primary
                                                    cover != null -> Color.Transparent
                                                    else -> tokens.borderColor.copy(alpha = 0.45f)
                                                },
                                                shape = cellShape
                                            )
                                            .then(
                                                if (dayMedia.isNotEmpty()) {
                                                    Modifier
                                                        .clickable {
                                                            onDayClick(dayKey, dayTitle, dayMedia)
                                                        }
                                                        .testTag("calendar_day_${block.key}_$dayNum")
                                                } else if (birthdayNames.isNotEmpty()) {
                                                    Modifier.clickable {
                                                        android.widget.Toast.makeText(context, "Birthday: " + birthdayNames.joinToString(", "), android.widget.Toast.LENGTH_SHORT).show()
                                                    }
                                                } else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (cover != null) {
                                            val calReq = remember(cover.id, cover.dateModifiedMillis) {
                                                val calKey = "thumb_${cover.id}_${cover.dateModifiedMillis}"
                                                ImageRequest.Builder(context)
                                                    .data(Uri.parse(cover.uriString))
                                                    .size(220, 220)
                                                    .scale(Scale.FILL)
                                                    .precision(Precision.INEXACT)
                                                    .memoryCacheKey(calKey)
                                                    .diskCacheKey(calKey)
                                                    .apply {
                                                        if (cover.isVideo) videoFrameMillis(1_000L)
                                                    }
                                                    .crossfade(false)
                                                    .build()
                                            }
                                            AsyncImage(
                                                model = calReq,
                                                imageLoader = imageLoader,
                                                contentDescription = "$dayTitle, ${dayMedia.size} item(s)",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.verticalGradient(
                                                            listOf(
                                                                Color.Transparent,
                                                                Color.Black.copy(alpha = 0.62f)
                                                            )
                                                        )
                                                    )
                                            )
                                            // Item count pill badge showing how many photos/videos were taken that day
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(3.dp)
                                                    .testTag("calendar_count_${block.key}_$dayNum")
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color.Black.copy(alpha = 0.74f))
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${dayMedia.size}",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 9.sp,
                                                        lineHeight = 11.sp
                                                    ),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                            }
                                            Text(
                                                text = "$dayNum",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color.White,
                                                modifier = Modifier
                                                    .align(Alignment.BottomCenter)
                                                    .padding(bottom = 3.dp)
                                            )
                                        } else {
                                            Text(
                                                text = "$dayNum",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = cs.onSurfaceVariant.copy(alpha = 0.4f)
                                            )
                                        }
                                        if (birthdayNames.isNotEmpty()) {
                                            Text(
                                                text = "\uD83C\uDF82",
                                                fontSize = 12.sp,
                                                modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
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
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaThumbnailTile(
    item: MediaItemEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    overrideUri: Uri? = null,
    aspectRatio: Float = 0.84f,
    modifier: Modifier = Modifier,
    onClickWithBounds: ((Rect) -> Unit)? = null,
    onPeek: ((Boolean) -> Unit)? = null,
    onShareDragStart: ((Offset) -> Unit)? = null,
    onShareDragMove: ((Offset) -> Unit)? = null,
    onShareDragEnd: ((Offset) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentOnPeek = androidx.compose.runtime.rememberUpdatedState(onPeek)
    val armedNow = androidx.compose.runtime.rememberUpdatedState(isSelected && isSelectionMode)
    val dragStart = androidx.compose.runtime.rememberUpdatedState(onShareDragStart)
    val dragMove = androidx.compose.runtime.rememberUpdatedState(onShareDragMove)
    val dragEnd = androidx.compose.runtime.rememberUpdatedState(onShareDragEnd)
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val uriToLoad = overrideUri ?: Uri.parse(item.uriString)
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current
    val cacheKey = "thumb_${item.id}_${item.dateModifiedMillis}"
    val tileShape = RoundedCornerShape(tokens.tileRadius)
    var tileBounds by remember { mutableStateOf(Rect.Zero) }
    val tileBoundsRef = androidx.compose.runtime.rememberUpdatedState(tileBounds)

    val request = remember(uriToLoad, cacheKey, item.isVideo) {
        ImageRequest.Builder(context)
            .data(uriToLoad)
            .size(360, 420)
            .scale(Scale.FILL)
            .precision(Precision.INEXACT)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .apply {
                if (item.isVideo) {
                    videoFrameMillis(1_000L)
                }
            }
            .crossfade(false)
            .build()
    }

    Box(
        modifier = modifier
            .aspectRatio(aspectRatio)
            .onGloballyPositioned { coords ->
                tileBounds = coords.boundsInRoot()
            }
            .clip(tileShape)
            .background(cs.surfaceVariant)
            .then(
                if (onPeek != null) Modifier.peekOnHold(
                    enabledAtDown = { !armedNow.value },
                    onPeek = { showing -> currentOnPeek.value?.invoke(showing) }
                ) else Modifier
            )
            .then(
                if (onShareDragStart != null) Modifier.holdToDragShare(
                    armedAtDown = { armedNow.value },
                    tileTopLeft = { tileBoundsRef.value.topLeft },
                    onStart = { dragStart.value?.invoke(it) },
                    onMove = { dragMove.value?.invoke(it) },
                    onEnd = { dragEnd.value?.invoke(it) }
                ) else Modifier
            )
            .combinedClickable(
                onClick = {
                    if (onClickWithBounds != null && tileBounds != Rect.Zero) {
                        onClickWithBounds(tileBounds)
                    } else {
                        onClick()
                    }
                },
                onLongClick = onLongPress
            )
            .then(
                if (isSelected) {
                    Modifier.border(3.dp, cs.primary, tileShape)
                } else Modifier
            )
            .testTag("media_tile_${item.id}")
    ) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isSelected) 3.dp else 0.dp)
                .clip(RoundedCornerShape(if (isSelected) 10.dp else tokens.tileRadius))
        )

        if (item.isVideo || item.isFavorite || item.hasEdits) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f))
                        )
                    )
            )
        }

        if (item.isVideo) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.68f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Video",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text = Formatters.formatDuration(item.durationMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isFavorite) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFF7A6B),
                    modifier = Modifier.size(14.dp)
                )
            }
            if (item.hasEdits) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = "Edited",
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }

        if (isSelectionMode) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) cs.primary else Color.White.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
            )
        }
    }
}

@Composable
fun MultiSelectActionBar(
    visible: Boolean,
    selectedCount: Int,
    onClear: () -> Unit,
    onShare: () -> Unit,
    onAddToAlbum: () -> Unit,
    onFavorite: () -> Unit,
    onMoveToVault: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onAirShare: (() -> Unit)? = null
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        FolioDepthCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .navigationBarsPadding(),
            shape = RoundedCornerShape(26.dp),
            elevation = 10.dp
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "$selectedCount selected",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("clear_selection_button")
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear selection")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (onAirShare != null) {
                        BarActionItem(Icons.Outlined.NearMe, "AirShare", onAirShare, "batch_airshare_button")
                    }
                    BarActionItem(Icons.Outlined.Share, "Share", onShare, "batch_share_button")
                    BarActionItem(Icons.Outlined.FolderCopy, "Album", onAddToAlbum, "batch_album_button")
                    BarActionItem(Icons.Outlined.FavoriteBorder, "Favorite", onFavorite, "batch_favorite_button")
                    BarActionItem(Icons.Outlined.Lock, "Vault", onMoveToVault, "batch_vault_button")
                    BarActionItem(Icons.Outlined.Delete, "Trash", onDelete, "batch_delete_button")
                }
            }
        }
    }
}

@Composable
private fun BarActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToAlbumSheet(
    albums: List<AlbumSummary>,
    onDismiss: () -> Unit,
    onSelectAlbum: (Long) -> Unit,
    onCreateNewAlbum: (String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add to Virtual Album",
                    style = MaterialTheme.typography.titleLarge
                )
                TextButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("sheet_new_album_button")
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("New Album")
                }
            }
            Spacer(Modifier.height(10.dp))
            if (albums.isEmpty()) {
                Text(
                    text = "No virtual albums yet. Create one to organize photos without moving original files.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(albums, key = { it.id }) { album ->
                        Surface(
                            onClick = { onSelectAlbum(album.id) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = album.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${album.itemCount} items",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNameDialog(
            title = "New Virtual Album",
            label = "Album name",
            initialValue = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateNewAlbum(name)
                showCreateDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
fun CreateNameDialog(
    title: String,
    label: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dialog_name_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (value.isNotBlank()) onConfirm(value.trim()) },
                enabled = value.isNotBlank(),
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
