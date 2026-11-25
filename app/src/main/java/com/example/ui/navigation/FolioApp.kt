package com.example.ui.navigation

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.share.ShareTargetsHelper
import kotlinx.coroutines.launch
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.MediaItemEntity
import com.example.data.local.PersonGroupEntity
import com.example.ui.components.FolioAtmosphereBackground
import com.example.ui.components.FolioBottomBar
import com.example.ui.components.FolioNavigationRail
import com.example.ui.components.PrimaryNavDestinations
import com.example.ui.screens.AirShareScreen
import com.example.ui.screens.AlbumDetailScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.CompressionScreen
import com.example.ui.screens.DayDetailScreen
import com.example.ui.screens.DuplicatesScreen
import com.example.ui.screens.EditorScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeGalleryScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.screens.PeopleScreen
import com.example.ui.screens.PermissionIntroScreen
import com.example.ui.screens.RecycleBinScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SlideshowScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.FolioAlbumTheme
import com.example.ui.viewmodel.AirShareViewModel
import com.example.ui.viewmodel.EditorViewModel
import com.example.ui.viewmodel.GalleryViewModel
import com.example.ui.viewmodel.VaultViewModel

@Composable
fun FolioApp(
    galleryViewModel: GalleryViewModel = viewModel(),
    vaultViewModel: VaultViewModel = viewModel(),
    editorViewModel: EditorViewModel = viewModel(),
    airShareViewModel: AirShareViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by galleryViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by galleryViewModel.searchState.collectAsStateWithLifecycle()
    val searchResults by galleryViewModel.searchResults.collectAsStateWithLifecycle()
    val duplicateGroups by galleryViewModel.duplicateGroups.collectAsStateWithLifecycle()
    val duplicateProgress by galleryViewModel.duplicateScanProgress.collectAsStateWithLifecycle()
    val faceProgress by galleryViewModel.faceScanProgress.collectAsStateWithLifecycle()
    val dupHold by galleryViewModel.duplicateHold.collectAsStateWithLifecycle()
    val faceHold by galleryViewModel.faceHold.collectAsStateWithLifecycle()
    val exifMap by galleryViewModel.loadedExif.collectAsStateWithLifecycle()

    val vaultState by vaultViewModel.uiState.collectAsStateWithLifecycle()
    val editorState by editorViewModel.editorState.collectAsStateWithLifecycle()
    val compressionState by editorViewModel.compressionState.collectAsStateWithLifecycle()
    val airShareState by airShareViewModel.uiState.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            galleryViewModel.clearStatusMessage()
        }
    }
    LaunchedEffect(editorState.message) {
        editorState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            editorViewModel.clearMessage()
        }
    }
    LaunchedEffect(compressionState.message) {
        compressionState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            editorViewModel.clearMessage()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (uiState.settings.hasSeenIntro) {
            galleryViewModel.syncMediaStore(force = false)
        }
    }

    var pendingPermanentDeleteItems by remember { mutableStateOf<List<MediaItemEntity>>(emptyList()) }
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && pendingPermanentDeleteItems.isNotEmpty()) {
            galleryViewModel.permanentlyDelete(pendingPermanentDeleteItems)
        }
        pendingPermanentDeleteItems = emptyList()
    }

    fun triggerPermanentDelete(items: List<MediaItemEntity>) {
        val pendingIntent = galleryViewModel.createMediaStoreDeleteIntent(items)
        if (pendingIntent != null) {
            pendingPermanentDeleteItems = items
            deleteRequestLauncher.launch(IntentSenderRequest.Builder(pendingIntent.intentSender).build())
        } else {
            galleryViewModel.permanentlyDelete(items)
        }
    }

    fun shareItems(items: List<MediaItemEntity>) {
        val intent = galleryViewModel.buildShareIntent(items)
        if (intent != null) {
            runCatching { context.startActivity(intent) }
                .onFailure { galleryViewModel.showStatus("No compatible share app found") }
        }
    }

    FolioAlbumTheme(
        themeMode = uiState.settings.themeMode,
        reducedMotion = uiState.settings.reducedMotion
    ) {
        val navController = rememberNavController()
        val appScope = rememberCoroutineScope()
        var viewerOriginRect by remember { mutableStateOf<Rect?>(null) }
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route

        // Decide the start screen once, from stored settings, so the permission page never
        // flashes on launch and the NavHost graph is never rebuilt when settings finish loading.
        var initialRoute by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            initialRoute = if (galleryViewModel.readHasSeenIntro()) FolioRoutes.GALLERY else FolioRoutes.INTRO
        }
        val startRoute = initialRoute
        if (startRoute == null) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            )
            return@FolioAlbumTheme
        }
        val primaryRoutes = remember { PrimaryNavDestinations.map { it.route }.toSet() }
        val showPrimaryNav = currentRoute in primaryRoutes && !uiState.isSelectionMode

        fun navigateTopLevel(route: String) {
            viewerOriginRect = null
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }

        FolioAtmosphereBackground {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isWideScreen = maxWidth >= 600.dp

                Scaffold(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (showPrimaryNav && !isWideScreen) {
                            FolioBottomBar(
                                currentRoute = currentRoute,
                                onNavigate = ::navigateTopLevel
                            )
                        }
                    }
                ) { innerPadding ->
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (showPrimaryNav && isWideScreen) {
                            FolioNavigationRail(
                                currentRoute = currentRoute,
                                onNavigate = ::navigateTopLevel
                            )
                        }

                        NavHost(
                            navController = navController,
                            startDestination = startRoute,
                            enterTransition = {
                                when {
                                    uiState.settings.reducedMotion -> EnterTransition.None
                                    targetState.destination.route == FolioRoutes.VIEWER_PATTERN -> EnterTransition.None
                                    else -> fadeIn(tween(220)) + scaleIn(
                                        initialScale = 0.96f,
                                        animationSpec = tween(220)
                                    )
                                }
                            },
                            exitTransition = {
                                when {
                                    uiState.settings.reducedMotion -> ExitTransition.None
                                    targetState.destination.route == FolioRoutes.VIEWER_PATTERN -> fadeOut(tween(180))
                                    else -> fadeOut(tween(180)) + scaleOut(
                                        targetScale = 0.97f,
                                        animationSpec = tween(180)
                                    )
                                }
                            },
                            popEnterTransition = {
                                when {
                                    uiState.settings.reducedMotion -> EnterTransition.None
                                    initialState.destination.route == FolioRoutes.VIEWER_PATTERN -> fadeIn(tween(160))
                                    else -> fadeIn(tween(220)) + scaleIn(
                                        initialScale = 0.97f,
                                        animationSpec = tween(220)
                                    )
                                }
                            },
                            popExitTransition = {
                                when {
                                    uiState.settings.reducedMotion -> ExitTransition.None
                                    initialState.destination.route == FolioRoutes.VIEWER_PATTERN -> ExitTransition.None
                                    else -> fadeOut(tween(180)) + scaleOut(
                                        targetScale = 0.95f,
                                        animationSpec = tween(180)
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            composable(FolioRoutes.INTRO) {
                                PermissionIntroScreen(
                                    onPermissionsResolved = {
                                        galleryViewModel.markIntroSeen()
                                        galleryViewModel.syncMediaStore()
                                        navController.navigate(FolioRoutes.GALLERY) {
                                            popUpTo(FolioRoutes.INTRO) { inclusive = true }
                                        }
                                    },
                                    onPickedFallbackMedia = { uris ->
                                        galleryViewModel.importPickedMedia(uris)
                                    }
                                )
                            }

                            composable(FolioRoutes.GALLERY) {
                                HomeGalleryScreen(
                                    uiState = uiState,
                                    onMediaClick = { item ->
                                        if (uiState.isSelectionMode) {
                                            galleryViewModel.toggleSelection(item.id)
                                        } else {
                                            viewerOriginRect = null
                                            navController.navigate(FolioRoutes.viewer(item.id, "gallery"))
                                        }
                                    },
                                    onMediaClickWithBounds = { item, bounds ->
                                        if (uiState.isSelectionMode) {
                                            galleryViewModel.toggleSelection(item.id)
                                        } else {
                                            viewerOriginRect = bounds
                                            navController.navigate(FolioRoutes.viewer(item.id, "gallery"))
                                        }
                                    },
                                    onMediaLongPress = { item ->
                                        galleryViewModel.startSelection(item.id)
                                    },
                                    onShareSelectedToTarget = { target ->
                                        val picked = galleryViewModel.getSelectedItems()
                                        when {
                                            target.isAirShare -> {
                                                airShareViewModel.setQueuedMedia(picked)
                                                navController.navigate(FolioRoutes.AIR_SHARE)
                                            }
                                            target.packageName != null -> {
                                                ShareTargetsHelper.recordShareUsage(context, target.packageName)
                                                val intent = galleryViewModel.buildTargetedMultiShareIntent(
                                                    picked, target.packageName, target.activityClassName
                                                )
                                                if (intent != null) {
                                                    runCatching { context.startActivity(intent) }
                                                        .onFailure { shareItems(picked) }
                                                }
                                            }
                                            else -> shareItems(picked)
                                        }
                                    },
                                    onSelectFilter = galleryViewModel::setMediaFilter,
                                    onSelectSort = galleryViewModel::setSortOrder,
                                    onToggleColumns = {
                                        val nextCols = if (uiState.settings.gridColumns == 3) 4 else 3
                                        galleryViewModel.setGridColumns(nextCols)
                                    },
                                    onRefreshSync = galleryViewModel::syncMediaStore,
                                    onImportUris = galleryViewModel::importPickedMedia,
                                    onOpenSlideshow = { navController.navigate(FolioRoutes.slideshow()) },
                                    onClearSelection = galleryViewModel::clearSelection,
                                    onShareSelected = {
                                        shareItems(galleryViewModel.getSelectedItems())
                                    },
                                    onAddSelectedToAlbum = { albumId ->
                                        galleryViewModel.addItemsToAlbum(albumId, galleryViewModel.getSelectedItems())
                                    },
                                    onCreateAlbumAndAdd = { name ->
                                        galleryViewModel.createAlbum(name)
                                    },
                                    onFavoriteSelected = galleryViewModel::toggleFavoriteForSelected,
                                    onVaultSelected = {
                                        val items = galleryViewModel.getSelectedItems()
                                        vaultViewModel.moveItemsToVault(items) { count ->
                                            galleryViewModel.clearSelection()
                                            galleryViewModel.showStatus("Encrypted $count item(s) into Vault")
                                        }
                                    },
                                    onDeleteSelected = galleryViewModel::moveSelectedToTrash,
                                    onOpenAirShare = {
                                        navController.navigate(FolioRoutes.AIR_SHARE)
                                    },
                                    onAirShareSelected = {
                                        val items = galleryViewModel.getSelectedItems()
                                        airShareViewModel.setQueuedMedia(items)
                                        galleryViewModel.clearSelection()
                                        navController.navigate(FolioRoutes.AIR_SHARE)
                                    },
                                    onOpenDay = { dayKey, title ->
                                        navController.navigate(FolioRoutes.dayDetail(dayKey, title))
                                    }
                                )
                            }

                            composable(FolioRoutes.ALBUMS) {
                                val mediaById = remember(uiState.allActiveMedia) {
                                    uiState.allActiveMedia.associateBy { it.id }
                                }
                                AlbumsScreen(
                                    virtualAlbums = uiState.virtualAlbums,
                                    deviceFolders = uiState.deviceFolders,
                                    peopleCount = uiState.personGroups.size,
                                    trashCount = uiState.trashedMedia.size,
                                    onOpenVirtualAlbum = { albumId ->
                                        navController.navigate(FolioRoutes.albumDetail(albumId))
                                    },
                                    onOpenDeviceFolder = { bucketId, name ->
                                        navController.navigate(FolioRoutes.folderDetail(bucketId, name))
                                    },
                                    onCreateAlbum = galleryViewModel::createAlbum,
                                    onRenameAlbum = galleryViewModel::renameAlbum,
                                    onDeleteAlbum = galleryViewModel::deleteAlbum,
                                    onOpenPeople = { navController.navigate(FolioRoutes.PEOPLE) },
                                    onOpenVault = { navController.navigate(FolioRoutes.VAULT) },
                                    onOpenDuplicates = { navController.navigate(FolioRoutes.DUPLICATES) },
                                    onOpenTrash = { navController.navigate(FolioRoutes.TRASH) },
                                    personGroups = uiState.personGroups,
                                    mediaById = mediaById,
                                    onOpenSearch = { navigateTopLevel(FolioRoutes.SEARCH) },
                                    onOpenFavorites = { navigateTopLevel(FolioRoutes.FAVORITES) },
                                    onOpenAirShare = { navController.navigate(FolioRoutes.AIR_SHARE) }
                                )
                            }

                            composable(
                                route = FolioRoutes.ALBUM_DETAIL_PATTERN,
                                arguments = listOf(navArgument("albumId") { type = NavType.LongType })
                            ) { entry ->
                                val albumId = entry.arguments?.getLong("albumId") ?: 0L
                                val album = uiState.virtualAlbums.find { it.id == albumId }
                                val subfolders = remember(uiState.virtualAlbums, albumId) {
                                    uiState.virtualAlbums.filter { it.parentAlbumId == albumId }
                                }
                                val albumItems by remember(albumId) {
                                    galleryViewModel.observeVirtualAlbumMedia(albumId)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                AlbumDetailScreen(
                                    title = album?.name ?: "Album",
                                    isVirtualAlbum = true,
                                    albumId = albumId,
                                    subfolders = subfolders,
                                    items = albumItems,
                                    gridColumns = uiState.settings.gridColumns,
                                    onBack = { navController.popBackStack() },
                                    onMediaClick = { item ->
                                        viewerOriginRect = null
                                        navController.navigate(FolioRoutes.viewer(item.id, "album", albumId.toString()))
                                    },
                                    onOpenSubfolder = { subId ->
                                        navController.navigate(FolioRoutes.albumDetail(subId))
                                    },
                                    onCreateSubfolder = { name, parentId ->
                                        galleryViewModel.createAlbum(name, parentId)
                                    },
                                    onRenameAlbum = galleryViewModel::renameAlbum,
                                    onOpenSlideshow = {
                                        navController.navigate(FolioRoutes.slideshow(albumItems.firstOrNull()?.id))
                                    }
                                )
                            }

                            composable(
                                route = FolioRoutes.FOLDER_DETAIL_PATTERN,
                                arguments = listOf(
                                    navArgument("bucketId") { type = NavType.StringType },
                                    navArgument("name") { type = NavType.StringType; defaultValue = "Folder" }
                                )
                            ) { entry ->
                                val bucketId = Uri.decode(entry.arguments?.getString("bucketId").orEmpty())
                                val folderName = Uri.decode(entry.arguments?.getString("name").orEmpty())
                                val folderItems by remember(bucketId) {
                                    galleryViewModel.observeFolderMedia(bucketId)
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                AlbumDetailScreen(
                                    title = folderName,
                                    isVirtualAlbum = false,
                                    albumId = null,
                                    subfolders = emptyList(),
                                    items = folderItems,
                                    gridColumns = uiState.settings.gridColumns,
                                    onBack = { navController.popBackStack() },
                                    onMediaClick = { item ->
                                        viewerOriginRect = null
                                        navController.navigate(FolioRoutes.viewer(item.id, "folder", bucketId))
                                    },
                                    onOpenSubfolder = {},
                                    onCreateSubfolder = { _, _ -> },
                                    onRenameAlbum = { _, _ -> },
                                    onOpenSlideshow = {
                                        navController.navigate(FolioRoutes.slideshow(folderItems.firstOrNull()?.id))
                                    }
                                )
                            }

                            composable(
                                route = FolioRoutes.DAY_DETAIL_PATTERN,
                                arguments = listOf(
                                    navArgument("dayKey") { type = NavType.IntType },
                                    navArgument("title") { type = NavType.StringType; defaultValue = "Memories" }
                                )
                            ) { entry ->
                                val dayKey = entry.arguments?.getInt("dayKey") ?: 0
                                val dayTitle = Uri.decode(entry.arguments?.getString("title").orEmpty())
                                    .ifBlank { "Memories" }
                                val dayItems = remember(uiState.filteredMedia, dayKey) {
                                    galleryViewModel.mediaForDayKey(uiState.filteredMedia, dayKey)
                                }

                                DayDetailScreen(
                                    title = dayTitle,
                                    items = dayItems,
                                    gridColumns = uiState.settings.gridColumns,
                                    onBack = { navController.popBackStack() },
                                    onMediaClick = { item ->
                                        viewerOriginRect = null
                                        navController.navigate(FolioRoutes.viewer(item.id, "day", dayKey.toString()))
                                    },
                                    onMediaClickWithBounds = { item, bounds ->
                                        viewerOriginRect = bounds
                                        navController.navigate(FolioRoutes.viewer(item.id, "day", dayKey.toString()))
                                    },
                                    onOpenSlideshow = {
                                        navController.navigate(FolioRoutes.slideshow(dayItems.firstOrNull()?.id))
                                    },
                                    onAirShareDay = {
                                        airShareViewModel.setQueuedMedia(dayItems)
                                        navController.navigate(FolioRoutes.AIR_SHARE)
                                    }
                                )
                            }

                            composable(FolioRoutes.SEARCH) {
                                SearchScreen(
                                    searchState = searchState,
                                    results = searchResults,
                                    allTags = uiState.allTags,
                                    gridColumns = uiState.settings.gridColumns,
                                    onQueryChange = galleryViewModel::updateSearchQuery,
                                    onFilterChange = galleryViewModel::updateSearchFilter,
                                    onSortChange = galleryViewModel::updateSearchSort,
                                    onToggleFavoritesOnly = galleryViewModel::toggleSearchFavoritesOnly,
                                    onSelectTag = galleryViewModel::setSearchTag,
                                    onMediaClick = { item ->
                                        viewerOriginRect = null
                                        navController.navigate(FolioRoutes.viewer(item.id, "search"))
                                    }
                                )
                            }

                            composable(FolioRoutes.FAVORITES) {
                                FavoritesScreen(
                                    favorites = uiState.favoriteMedia,
                                    gridColumns = uiState.settings.gridColumns,
                                    onMediaClick = { item ->
                                        viewerOriginRect = null
                                        navController.navigate(FolioRoutes.viewer(item.id, "favorites"))
                                    }
                                )
                            }

                            composable(FolioRoutes.SETTINGS) {
                                SettingsScreen(
                                    settings = uiState.settings,
                                    onSelectTheme = galleryViewModel::setThemeMode,
                                    onToggleReducedMotion = galleryViewModel::setReducedMotion,
                                    onSelectShareAnimStyle = galleryViewModel::setShareAnimStyle,
                                    onToggleLocationMetadata = galleryViewModel::setShowLocationMetadata,
                                    onSelectAutoCleanDays = galleryViewModel::setTrashAutoCleanDays,
                                    onToggleFaceGrouping = galleryViewModel::setOfflineFaceGroupingEnabled,
                                    onImportAiModelPack = { name ->
                                        galleryViewModel.setImportedAiModelName(name)
                                        galleryViewModel.showStatus("Installed local AI pack: $name")
                                    },
                                    onCreateBackup = galleryViewModel::createLocalBackup,
                                    onRestoreBackup = galleryViewModel::restoreLatestBackup
                                )
                            }

                            composable(
                                route = FolioRoutes.VIEWER_PATTERN,
                                arguments = listOf(
                                    navArgument("mediaId") { type = NavType.StringType },
                                    navArgument("source") { type = NavType.StringType; defaultValue = "gallery" },
                                    navArgument("key") { type = NavType.StringType; defaultValue = "" }
                                )
                            ) { entry ->
                                val mediaId = Uri.decode(entry.arguments?.getString("mediaId").orEmpty())
                                val source = Uri.decode(entry.arguments?.getString("source") ?: "gallery")
                                val key = Uri.decode(entry.arguments?.getString("key").orEmpty())

                                val albumMedia by remember(source, key) {
                                    if (source == "album") {
                                        galleryViewModel.observeVirtualAlbumMedia(key.toLongOrNull() ?: -1L)
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                val folderMedia by remember(source, key) {
                                    if (source == "folder") {
                                        galleryViewModel.observeFolderMedia(key)
                                    } else {
                                        kotlinx.coroutines.flow.flowOf(emptyList())
                                    }
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                val viewerItems = when (source) {
                                    "favorites" -> uiState.favoriteMedia
                                    "search" -> searchResults
                                    "day" -> galleryViewModel.mediaForDayKey(
                                        uiState.filteredMedia,
                                        key.toIntOrNull() ?: 0
                                    )
                                    "album" -> albumMedia
                                    "folder" -> folderMedia
                                    else -> uiState.filteredMedia
                                }.ifEmpty {
                                    uiState.allActiveMedia
                                }

                                MediaViewerScreen(
                                    items = viewerItems,
                                    initialMediaId = mediaId,
                                    mediaTagMap = uiState.mediaTagMap,
                                    exifMap = exifMap,
                                    showLocationMetadata = uiState.settings.showLocationMetadata,
                                    onBack = {
                                        navController.popBackStack()
                                        viewerOriginRect = null
                                    },
                                    onRequestExif = galleryViewModel::loadExifIfNeeded,
                                    onToggleFavorite = galleryViewModel::toggleFavoriteSingle,
                                    onOpenEditor = { item -> navController.navigate(FolioRoutes.editor(item.id)) },
                                    onOpenCompress = { item -> navController.navigate(FolioRoutes.compress(item.id)) },
                                    onOpenSlideshow = { item -> navController.navigate(FolioRoutes.slideshow(item.id)) },
                                    onShare = { item -> shareItems(listOf(item)) },
                                    onMoveToVault = { item ->
                                        vaultViewModel.moveItemsToVault(listOf(item)) {
                                            galleryViewModel.showStatus("Encrypted & moved to Hidden Vault")
                                            navController.popBackStack()
                                            viewerOriginRect = null
                                        }
                                    },
                                    onDelete = { item ->
                                        galleryViewModel.moveSingleToTrash(item.id)
                                        navController.popBackStack()
                                        viewerOriginRect = null
                                    },
                                    onUpdateNote = galleryViewModel::updateNote,
                                    onAddTag = galleryViewModel::addTagToMedia,
                                    onRemoveTag = galleryViewModel::removeTagFromMedia,
                                    onSavePlaybackPosition = galleryViewModel::updatePlaybackPosition,
                                    onOpenExternalPlayer = { item ->
                                        runCatching {
                                            context.startActivity(galleryViewModel.buildExternalPlayerIntent(item))
                                        }.onFailure {
                                            galleryViewModel.showStatus("No external video player installed")
                                        }
                                    },
                                    onAirShare = { item ->
                                        airShareViewModel.setQueuedMedia(listOf(item))
                                        navController.navigate(FolioRoutes.AIR_SHARE)
                                    },
                                    launchOriginRect = viewerOriginRect,
                                    onTargetedShare = { item, target, isLiteMode ->
                                        when {
                                            target.isAirShare -> {
                                                airShareViewModel.setQueuedMedia(listOf(item))
                                                navController.navigate(FolioRoutes.AIR_SHARE)
                                            }
                                            target.isCopyClipboard -> {
                                                val copied = galleryViewModel.copyMediaToClipboard(item)
                                                galleryViewModel.showStatus(
                                                    if (copied) "Copied to clipboard" else "Could not copy media"
                                                )
                                            }
                                            target.isSystemShare -> {
                                                if (isLiteMode) {
                                                    appScope.launch {
                                                        val intent = galleryViewModel.buildLiteTargetedShareIntent(item)
                                                        if (intent != null) {
                                                            runCatching { context.startActivity(intent) }
                                                                .onFailure { shareItems(listOf(item)) }
                                                        } else {
                                                            shareItems(listOf(item))
                                                        }
                                                    }
                                                } else {
                                                    shareItems(listOf(item))
                                                }
                                            }
                                            target.packageName != null -> {
                                                ShareTargetsHelper.recordShareUsage(context, target.packageName)
                                                appScope.launch {
                                                    val intent = if (isLiteMode) {
                                                        galleryViewModel.buildLiteTargetedShareIntent(
                                                            item = item,
                                                            packageName = target.packageName,
                                                            activityClassName = target.activityClassName
                                                        )
                                                    } else {
                                                        galleryViewModel.buildTargetedShareIntent(
                                                            item = item,
                                                            packageName = target.packageName,
                                                            activityClassName = target.activityClassName
                                                        )
                                                    }
                                                    if (intent != null) {
                                                        runCatching { context.startActivity(intent) }
                                                            .onFailure { shareItems(listOf(item)) }
                                                    } else {
                                                        shareItems(listOf(item))
                                                    }
                                                }
                                            }
                                            else -> shareItems(listOf(item))
                                        }
                                    }
                                )
                            }

                            composable(
                                route = FolioRoutes.EDITOR_PATTERN,
                                arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
                            ) { entry ->
                                val mediaId = Uri.decode(entry.arguments?.getString("mediaId").orEmpty())
                                EditorScreen(
                                    mediaId = mediaId,
                                    state = editorState,
                                    onLoad = editorViewModel::loadForEditor,
                                    onBack = { navController.popBackStack() },
                                    onRotate90 = editorViewModel::rotate90,
                                    onFlip = editorViewModel::toggleFlip,
                                    onSelectAspect = editorViewModel::setCropAspect,
                                    onSelectFilter = editorViewModel::setFilterPreset,
                                    onUpdateParams = editorViewModel::updateParams,
                                    onAddText = editorViewModel::addTextOverlay,
                                    onAddSticker = editorViewModel::addSticker,
                                    onClearOverlays = editorViewModel::clearOverlays,
                                    onAddTextBehind = { editorViewModel.addTextOverlay(it, behind = true) },
                                    onToggleTextBehind = editorViewModel::toggleTextBehind,
                                    onSetTextScale = editorViewModel::setTextScale,
                                    onMoveText = editorViewModel::moveText,
                                    onReset = editorViewModel::resetAll,
                                    onSaveNonDestructive = {
                                        editorViewModel.saveNonDestructive { navController.popBackStack() }
                                    },
                                    onExportCopy = {
                                        editorViewModel.exportEditedCopy()
                                        galleryViewModel.syncMediaStore()
                                    }
                                )
                            }

                            composable(
                                route = FolioRoutes.COMPRESS_PATTERN,
                                arguments = listOf(navArgument("mediaId") { type = NavType.StringType })
                            ) { entry ->
                                val mediaId = Uri.decode(entry.arguments?.getString("mediaId").orEmpty())
                                CompressionScreen(
                                    mediaId = mediaId,
                                    state = compressionState,
                                    onLoad = editorViewModel::loadForCompression,
                                    onBack = { navController.popBackStack() },
                                    onQualityChange = editorViewModel::setCompressionQuality,
                                    onScaleChange = editorViewModel::setCompressionScale,
                                    onExportCompressed = {
                                        editorViewModel.exportCompressedCopy()
                                        galleryViewModel.syncMediaStore()
                                    }
                                )
                            }

                            composable(
                                route = FolioRoutes.SLIDESHOW_PATTERN,
                                arguments = listOf(
                                    navArgument("startId") {
                                        type = NavType.StringType
                                        nullable = true
                                        defaultValue = null
                                    }
                                )
                            ) { entry ->
                                val startId = entry.arguments?.getString("startId")?.let { Uri.decode(it) }
                                SlideshowScreen(
                                    items = uiState.filteredMedia,
                                    startMediaId = startId,
                                    reducedMotion = uiState.settings.reducedMotion,
                                    onBack = { navController.popBackStack() }
                                )
                            }

                            composable(FolioRoutes.VAULT) {
                                VaultScreen(
                                    state = vaultState,
                                    onBack = { navController.popBackStack() },
                                    onSetupPin = vaultViewModel::setupPin,
                                    onVerifyPin = vaultViewModel::verifyPin,
                                    onPrepareBiometricUnlock = vaultViewModel::prepareBiometricUnlock,
                                    onBiometricUnlockSuccess = vaultViewModel::completeBiometricUnlock,
                                    onPrepareBiometricEnrollment = vaultViewModel::prepareBiometricEnrollment,
                                    onBiometricEnrollmentSuccess = vaultViewModel::completeBiometricEnrollment,
                                    onDisableBiometric = vaultViewModel::disableBiometric,
                                    onBiometricError = vaultViewModel::reportBiometricError,
                                    onLockVault = vaultViewModel::lockVault,
                                    onRequestDecrypt = vaultViewModel::ensureDecryptedPreview,
                                    onRemoveFromVault = vaultViewModel::removeFromVault
                                )
                            }

                            composable(FolioRoutes.DUPLICATES) {
                                DuplicatesScreen(
                                    groups = duplicateGroups,
                                    scanProgress = duplicateProgress,
                                    onBack = { navController.popBackStack() },
                                    onStartScan = galleryViewModel::scanDuplicates,
                                    hold = dupHold,
                                    onPauseScan = galleryViewModel::pauseDuplicateScan,
                                    onResumeScan = galleryViewModel::resumeDuplicateScan,
                                    onStopScan = galleryViewModel::stopDuplicateScan,
                                    onTrashSelected = { ids ->
                                        galleryViewModel.moveIdsToTrash(ids)
                                        galleryViewModel.scanDuplicates()
                                    }
                                )
                            }

                            composable(FolioRoutes.PEOPLE) {
                                var selectedPerson by remember { mutableStateOf<PersonGroupEntity?>(null) }
                                val personMedia by remember(selectedPerson?.id) {
                                    val pid = selectedPerson?.id
                                    if (pid != null) galleryViewModel.observePersonMedia(pid)
                                    else kotlinx.coroutines.flow.flowOf(emptyList())
                                }.collectAsStateWithLifecycle(initialValue = emptyList())

                                val mediaById = remember(uiState.allActiveMedia) {
                                    uiState.allActiveMedia.associateBy { it.id }
                                }

                                PeopleScreen(
                                    people = uiState.personGroups,
                                    mediaById = mediaById,
                                    faceScanProgress = faceProgress,
                                    selectedPersonMedia = personMedia,
                                    selectedPerson = selectedPerson,
                                    gridColumns = uiState.settings.gridColumns,
                                    onBack = { navController.popBackStack() },
                                    onSelectPerson = { selectedPerson = it },
                                    onScanFaces = galleryViewModel::scanFaces,
                                    hold = faceHold,
                                    onPauseScan = galleryViewModel::pauseFaceScan,
                                    onResumeScan = galleryViewModel::resumeFaceScan,
                                    onStopScan = galleryViewModel::stopFaceScan,
                                    onRenamePerson = galleryViewModel::renamePerson,
                                    onDeleteAllFaceData = galleryViewModel::deleteAllFaceData,
                                    onMediaClick = { item ->
                                        viewerOriginRect = null
                                        navController.navigate(FolioRoutes.viewer(item.id, "gallery"))
                                    }
                                )
                            }

                            composable(FolioRoutes.TRASH) {
                                RecycleBinScreen(
                                    trashedItems = uiState.trashedMedia,
                                    autoCleanDays = uiState.settings.trashAutoCleanDays,
                                    onBack = { navController.popBackStack() },
                                    onRestore = galleryViewModel::restoreFromTrash,
                                    onPermanentDelete = ::triggerPermanentDelete
                                )
                            }

                            composable(FolioRoutes.AIR_SHARE) {
                                AirShareScreen(
                                    uiState = airShareState,
                                    galleryMedia = uiState.allActiveMedia,
                                    reducedMotion = uiState.settings.reducedMotion,
                                    shareAnimStyle = uiState.settings.shareAnimStyle,
                                    onBack = { navController.popBackStack() },
                                    onSelectMode = airShareViewModel::setMode,
                                    onPermissionsResult = airShareViewModel::setPermissionsGranted,
                                    onStartScanning = airShareViewModel::startScanning,
                                    onStartReceiving = airShareViewModel::startReceiving,
                                    onConnectPeer = airShareViewModel::connectToPeer,
                                    onAcceptConnection = airShareViewModel::acceptPendingConnection,
                                    onRejectConnection = airShareViewModel::rejectPendingConnection,
                                    onSendQueued = airShareViewModel::sendQueuedToPeer,
                                    onDisconnect = airShareViewModel::disconnect,
                                    onStopSession = airShareViewModel::stopSession,
                                    onAddGalleryMedia = airShareViewModel::addQueuedMedia,
                                    onImportPickedUris = { uris ->
                                        galleryViewModel.importPickedMedia(uris)
                                    },
                                    onRemoveQueuedItem = airShareViewModel::removeQueuedItem
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
