package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.storage.MediaFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.offset
import com.example.core.storage.SortOrder
import com.example.data.local.MediaItemEntity
import com.example.ui.components.AddToAlbumSheet
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.FolioSegmentedControl
import com.example.ui.components.MediaCalendarGrid
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.components.PeekPreviewOverlay
import com.example.ui.components.TimelineZoom
import com.example.ui.components.pinchToChangeZoom
import com.example.ui.components.regroupSections
import com.example.ui.components.MultiSelectActionBar
import com.example.ui.viewmodel.GalleryUiState

@Composable
fun HomeGalleryScreen(
    uiState: GalleryUiState,
    onMediaClick: (MediaItemEntity) -> Unit,
    onMediaLongPress: (MediaItemEntity) -> Unit,
    onSelectFilter: (MediaFilter) -> Unit,
    onSelectSort: (SortOrder) -> Unit,
    onToggleColumns: () -> Unit,
    onRefreshSync: () -> Unit,
    onImportUris: (List<Uri>) -> Unit,
    onOpenSlideshow: () -> Unit,
    onClearSelection: () -> Unit,
    onShareSelected: () -> Unit,
    onAddSelectedToAlbum: (Long) -> Unit,
    onCreateAlbumAndAdd: (String) -> Unit,
    onFavoriteSelected: () -> Unit,
    onVaultSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAirShare: () -> Unit = {},
    onAirShareSelected: () -> Unit = {},
    onOpenDay: (Int, String) -> Unit = { _, _ -> },
    onMediaClickWithBounds: ((MediaItemEntity, Rect) -> Unit)? = null,
    onShareSelectedToTarget: (com.example.share.QuickShareTarget) -> Unit = {}
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var showAlbumSheet by remember { mutableStateOf(false) }
    var isCalendarMode by remember { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(TimelineZoom.DAY) }
    val pinchState = remember { com.example.ui.components.LivePinchState() }
    var peekItem by remember { mutableStateOf<MediaItemEntity?>(null) }
    val homeContext = androidx.compose.ui.platform.LocalContext.current
    var shareDragPos by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
    var hoveredDockId by remember { mutableStateOf<String?>(null) }
    var rootOrigin by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val dockBounds = remember { androidx.compose.runtime.mutableStateMapOf<String, Rect>() }
    val dockTargets = remember {
        com.example.share.ShareTargetsHelper.resolveQuickShareTargets(homeContext, "image/*", 4)
            .filter { !it.isCopyClipboard }
    }
    fun hoverAt(p: androidx.compose.ui.geometry.Offset): String? =
        dockBounds.entries.firstOrNull { it.value.inflate(14f).contains(p) }?.key
    val isDateSorted = uiState.settings.sortOrder == SortOrder.NEWEST ||
        uiState.settings.sortOrder == SortOrder.OLDEST
    val zoomedSections = remember(uiState.timelineSections, zoom, isDateSorted) {
        if (isDateSorted) regroupSections(uiState.timelineSections, zoom) else uiState.timelineSections
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 30)
    ) { uris ->
        if (uris.isNotEmpty()) onImportUris(uris)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        onRefreshSync()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .androidx_onPositioned { rootOrigin = it }
    ) {
        val adaptiveColumns = if (maxWidth >= 600.dp) {
            uiState.settings.gridColumns + 2
        } else {
            uiState.settings.gridColumns
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Editorial top bar inspired by the reference gallery
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Gallery",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.filteredMedia.size} items • Offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        onClick = onOpenAirShare,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("home_airshare_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.NearMe,
                                contentDescription = "AirShare Offline",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    if (uiState.filteredMedia.isNotEmpty()) {
                        IconButton(
                            onClick = onOpenSlideshow,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("home_slideshow_button")
                        ) {
                            Icon(Icons.Outlined.Slideshow, contentDescription = "Slideshow")
                        }
                    }
                    IconButton(
                        onClick = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_import_button")
                    ) {
                        Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = "Import media")
                    }
                    if (!isCalendarMode) {
                        IconButton(
                            onClick = onToggleColumns,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("toggle_columns_button")
                        ) {
                            Icon(Icons.Outlined.GridView, contentDescription = "Toggle grid density")
                        }
                    }
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("sort_menu_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort media")
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = order.label,
                                            fontWeight = if (order == uiState.settings.sortOrder) {
                                                FontWeight.Bold
                                            } else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onSelectSort(order)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isSyncing) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                )
            }

            if (uiState.timelineSections.isEmpty() && !uiState.isSyncing) {
                GalleryViewControlsHeader(
                    isCalendarMode = isCalendarMode,
                    onToggleCalendarMode = { isCalendarMode = it },
                    selectedFilter = uiState.settings.mediaFilter,
                    totalAll = uiState.allActiveMedia.size,
                    totalPhotos = uiState.allActiveMedia.count { !it.isVideo },
                    totalVideos = uiState.allActiveMedia.count { it.isVideo },
                    onSelectFilter = onSelectFilter
                )
                EmptyGalleryState(
                    onGrantPermission = { permissionLauncher.launch(requiredMediaPermissions()) },
                    onPickMedia = {
                        pickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    },
                    onRefresh = onRefreshSync
                )
            } else if (isCalendarMode) {
                val birthdayDao = remember { com.example.data.local.AlbumDatabase.getInstance(androidx.compose.ui.platform.LocalContext.current).galleryDao() }
                val birthdayList by birthdayDao.observeBirthdays().collectAsState(initial = emptyList())
                MediaCalendarGrid(
                    birthdays = birthdayList,
                    mediaItems = uiState.filteredMedia,
                    onDayClick = { dayKey, dayTitle, _ ->
                        onOpenDay(dayKey, dayTitle)
                    },
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 104.dp),
                    headerContent = {
                        GalleryViewControlsHeader(
                            isCalendarMode = true,
                            onToggleCalendarMode = { isCalendarMode = it },
                            selectedFilter = uiState.settings.mediaFilter,
                            totalAll = uiState.allActiveMedia.size,
                            totalPhotos = uiState.allActiveMedia.count { !it.isVideo },
                            totalVideos = uiState.allActiveMedia.count { it.isVideo },
                            onSelectFilter = onSelectFilter
                        )
                    }
                )
            } else {
                MixedMediaTimelineGrid(
                    sections = zoomedSections,
                    columns = adaptiveColumns + if (isDateSorted) zoom.extraColumns else 0,
                    pinch = pinchState,
                    modifier = com.example.ui.components.rememberLivePinchZoom(
                        state = pinchState,
                        columnsNow = { adaptiveColumns + if (isDateSorted) zoom.extraColumns else 0 },
                        canZoomIn = { isDateSorted && zoom != TimelineZoom.DAY },
                        canZoomOut = { isDateSorted && zoom != TimelineZoom.YEAR },
                        onStep = { zoomIn -> zoom = if (zoomIn) zoom.zoomedIn() else zoom.zoomedOut() }
                    ),
                    swipeSelectEnabled = shareDragPos == null,
                    onShareDragStart = { p -> shareDragPos = p; hoveredDockId = hoverAt(p) },
                    onShareDragMove = { p -> shareDragPos = p; hoveredDockId = hoverAt(p) },
                    onShareDragEnd = { p ->
                        val hit = hoverAt(p)
                        shareDragPos = null
                        hoveredDockId = null
                        dockTargets.firstOrNull { it.id == hit }?.let { onShareSelectedToTarget(it) }
                    },
                    onPeek = if (uiState.isSelectionMode && uiState.selectedIds.size > 1) null else { item ->
                        peekItem = item
                        // A hold also fires the normal long-press; undo an accidental single selection.
                        if (item != null && uiState.selectedIds.size <= 1) onClearSelection()
                    },
                    selectedIds = uiState.selectedIds,
                    isSelectionMode = uiState.isSelectionMode,
                    onItemClick = onMediaClick,
                    onItemClickWithBounds = if (!uiState.isSelectionMode) onMediaClickWithBounds else null,
                    onItemLongPress = onMediaLongPress,
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (uiState.isSelectionMode) 150.dp else 104.dp
                    ),
                    headerContent = {
                        GalleryViewControlsHeader(
                            isCalendarMode = false,
                            onToggleCalendarMode = { isCalendarMode = it },
                            selectedFilter = uiState.settings.mediaFilter,
                            totalAll = uiState.allActiveMedia.size,
                            totalPhotos = uiState.allActiveMedia.count { !it.isVideo },
                            totalVideos = uiState.allActiveMedia.count { it.isVideo },
                            onSelectFilter = onSelectFilter
                        )
                    }
                )
            }
        }

        PeekPreviewOverlay(item = peekItem)

        if (shareDragPos != null) {
            com.example.ui.components.MultiShareDock(
                targets = dockTargets,
                hoveredId = hoveredDockId,
                onTargetBounds = { id, r -> dockBounds[id] = r },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 96.dp)
            )
            val pos = shareDragPos!!
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .offset { androidx.compose.ui.unit.IntOffset((pos.x - rootOrigin.x - 36f).toInt(), (pos.y - rootOrigin.y - 90f).toInt()) }
                    .size(72.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${uiState.selectedIds.size}",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
        }

        MultiSelectActionBar(
            visible = uiState.isSelectionMode,
            selectedCount = uiState.selectedIds.size,
            onClear = onClearSelection,
            onShare = onShareSelected,
            onAirShare = onAirShareSelected,
            onAddToAlbum = { showAlbumSheet = true },
            onFavorite = onFavoriteSelected,
            onMoveToVault = onVaultSelected,
            onDelete = onDeleteSelected,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showAlbumSheet) {
        AddToAlbumSheet(
            albums = uiState.virtualAlbums,
            onDismiss = { showAlbumSheet = false },
            onSelectAlbum = { albumId ->
                showAlbumSheet = false
                onAddSelectedToAlbum(albumId)
            },
            onCreateNewAlbum = { name ->
                onCreateAlbumAndAdd(name)
            }
        )
    }
}

@Composable
private fun GalleryViewControlsHeader(
    isCalendarMode: Boolean,
    onToggleCalendarMode: (Boolean) -> Unit,
    selectedFilter: MediaFilter,
    totalAll: Int,
    totalPhotos: Int,
    totalVideos: Int,
    onSelectFilter: (MediaFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FolioSegmentedControl(
                options = listOf("Timeline", "Calendar"),
                selectedIndex = if (isCalendarMode) 1 else 0,
                onSelect = { onToggleCalendarMode(it == 1) },
                testTagPrefix = "gallery_view_mode",
                modifier = Modifier.width(210.dp)
            )
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = if (isCalendarMode) Icons.Outlined.CalendarMonth else Icons.Outlined.GridView,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val counts = mapOf(
                MediaFilter.ALL to totalAll,
                MediaFilter.PHOTOS to totalPhotos,
                MediaFilter.VIDEOS to totalVideos
            )
            MediaFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) },
                    label = { Text("${filter.label} (${counts[filter] ?: 0})") },
                    modifier = Modifier.testTag("filter_${filter.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun EmptyGalleryState(
    onGrantPermission: () -> Unit,
    onPickMedia: () -> Unit,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.PhotoLibrary,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = "No Photos or Videos Found",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Grant device media access to browse your local timeline, or pick photos & videos directly.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Button(
                        onClick = onGrantPermission,
                        modifier = Modifier.testTag("empty_grant_permission_button")
                    ) {
                        Text("Allow Access")
                    }
                    OutlinedButton(
                        onClick = onPickMedia,
                        modifier = Modifier.testTag("empty_pick_media_button")
                    ) {
                        Text("Pick Files")
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRefresh)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text("Rescan MediaStore", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun Modifier.androidx_onPositioned(onOrigin: (androidx.compose.ui.geometry.Offset) -> Unit): Modifier =
    this.then(
        androidx.compose.ui.Modifier.onGloballyPositioned { onOrigin(it.positionInRoot()) }
    )
