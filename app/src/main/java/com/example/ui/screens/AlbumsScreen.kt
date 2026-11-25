package com.example.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.FolioImageLoader
import com.example.data.local.AlbumSummary
import com.example.data.local.DeviceFolderSummary
import com.example.data.local.MediaItemEntity
import com.example.data.local.PersonGroupEntity
import com.example.ui.components.CreateNameDialog
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.FolioSearchPillRow
import com.example.ui.theme.LocalFolioTokens

@Composable
fun AlbumsScreen(
    virtualAlbums: List<AlbumSummary>,
    deviceFolders: List<DeviceFolderSummary>,
    peopleCount: Int,
    trashCount: Int,
    onOpenVirtualAlbum: (Long) -> Unit,
    onOpenDeviceFolder: (String, String) -> Unit,
    onCreateAlbum: (String, Long?) -> Unit,
    onRenameAlbum: (Long, String) -> Unit,
    onDeleteAlbum: (Long) -> Unit,
    onOpenPeople: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenTrash: () -> Unit,
    modifier: Modifier = Modifier,
    personGroups: List<PersonGroupEntity> = emptyList(),
    mediaById: Map<String, MediaItemEntity> = emptyMap(),
    onOpenSearch: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenAirShare: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AlbumSummary?>(null) }

    val rootAlbums = remember(virtualAlbums) { virtualAlbums.filter { it.parentAlbumId == null } }
    // With many albums, show a tidy preview first and let the user expand the rest.
    val previewLimit = 8
    var showAllFolders by remember { mutableStateOf(false) }

    // Large libraries (more than 12 albums) switch to a compact, searchable, sortable layout.
    val largeLibrary = rootAlbums.size > 12
    var albumQuery by remember { mutableStateOf("") }
    var albumSort by remember { mutableStateOf(AlbumSort.RECENT) }
    var listModeOverride by remember { mutableStateOf<Boolean?>(null) }
    val useList = largeLibrary && (listModeOverride ?: true)
    val shownAlbums = remember(rootAlbums, albumQuery, albumSort) {
        val filtered = if (albumQuery.isBlank()) rootAlbums
        else rootAlbums.filter { it.name.contains(albumQuery.trim(), ignoreCase = true) }
        when (albumSort) {
            AlbumSort.RECENT -> filtered
            AlbumSort.NAME -> filtered.sortedBy { it.name.lowercase() }
            AlbumSort.COUNT -> filtered.sortedByDescending { it.itemCount }
        }
    }
    val topAlbums = remember(rootAlbums) { rootAlbums.sortedByDescending { it.itemCount }.take(8) }
    val visibleFolders = if (showAllFolders || deviceFolders.size <= previewLimit) deviceFolders else deviceFolders.take(previewLimit - 2)

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 152.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Warm Studio inspired Search Pill + Favorites circular shortcut
        item(span = { GridItemSpan(maxLineSpan) }, key = "search_header") {
            FolioSearchPillRow(
                placeholder = "Search photos, videos, places...",
                onSearchClick = onOpenSearch,
                onFavoritesClick = onOpenFavorites
            )
        }

        // Albums header with + Add New
        item(span = { GridItemSpan(maxLineSpan) }, key = "albums_title_row") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Albums",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (rootAlbums.isNotEmpty()) {
                        Spacer(Modifier.width(8.dp))
                        CountChip(if (rootAlbums.size > 99) "99+" else "${rootAlbums.size}")
                    }
                }
                TextButton(
                    onClick = { showCreateDialog = true },
                    modifier = Modifier.testTag("create_album_fab")
                ) {
                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add New", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (largeLibrary) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "albums_toolbar") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedTextField(
                        value = albumQuery,
                        onValueChange = { albumQuery = it },
                        singleLine = true,
                        placeholder = { Text("Find an album") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AlbumSort.entries.forEach { s ->
                                androidx.compose.material3.FilterChip(
                                    selected = albumSort == s,
                                    onClick = { albumSort = s },
                                    label = { Text(s.label) }
                                )
                            }
                        }
                        IconButton(onClick = { listModeOverride = !useList }) {
                            Icon(
                                imageVector = if (useList) Icons.Outlined.GridView else Icons.AutoMirrored.Outlined.ViewList,
                                contentDescription = if (useList) "Grid view" else "List view"
                            )
                        }
                    }
                }
            }

            if (albumQuery.isBlank() && albumSort != AlbumSort.COUNT) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "albums_top") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Biggest albums",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(topAlbums, key = { "top_${it.id}" }) { album ->
                                Box(modifier = Modifier.width(132.dp)) {
                                    AlbumFrostedGridCard(
                                        title = album.name,
                                        count = album.itemCount,
                                        coverUri = album.coverUri,
                                        onClick = { onOpenVirtualAlbum(album.id) },
                                        onRename = null,
                                        onDelete = null,
                                        tag = "top_album_${album.id}"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (shownAlbums.isEmpty() && largeLibrary) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "albums_none") {
                Text(
                    "No albums match \"$albumQuery\"",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else if (useList) {
            var lastLetter = ' '
            shownAlbums.forEach { album ->
                if (albumSort == AlbumSort.NAME) {
                    val letter = album.name.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
                    if (letter != lastLetter) {
                        lastLetter = letter
                        item(span = { GridItemSpan(maxLineSpan) }, key = "hdr_$letter") {
                            Text(
                                text = letter.toString(),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }, key = "va_${album.id}") {
                    AlbumCompactRow(
                        title = album.name,
                        count = album.itemCount,
                        coverUri = album.coverUri,
                        onClick = { onOpenVirtualAlbum(album.id) },
                        onRename = { renameTarget = album },
                        onDelete = { onDeleteAlbum(album.id) },
                        tag = "virtual_album_${album.id}"
                    )
                }
            }
        } else if (shownAlbums.isNotEmpty()) {
            items(shownAlbums, key = { "va_${it.id}" }) { album ->
                AlbumFrostedGridCard(
                    title = album.name,
                    count = album.itemCount,
                    coverUri = album.coverUri,
                    onClick = { onOpenVirtualAlbum(album.id) },
                    onRename = { renameTarget = album },
                    onDelete = { onDeleteAlbum(album.id) },
                    tag = "virtual_album_${album.id}"
                )
            }
        }

        if (deviceFolders.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "folders_title") {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.width(8.dp))
                    CountChip(if (deviceFolders.size > 99) "99+" else "${deviceFolders.size}")
                }
            }

            items(visibleFolders, key = { "df_${it.bucketId}" }) { folder ->
                AlbumFrostedGridCard(
                    title = folder.bucketName,
                    count = folder.itemCount,
                    coverUri = folder.coverUri,
                    onClick = { onOpenDeviceFolder(folder.bucketId, folder.bucketName) },
                    onRename = null,
                    onDelete = null,
                    tag = "device_folder_${folder.bucketId}"
                )
            }
        }

        if (deviceFolders.size > previewLimit) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "folders_toggle") {
                TextButton(
                    onClick = { showAllFolders = !showAllFolders },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (showAllFolders) "Show fewer folders" else "See all ${deviceFolders.size} folders",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Bottom People Avatar Strip + Utilities Libraries (kept cleanly at the bottom of Albums)
        item(span = { GridItemSpan(maxLineSpan) }, key = "utilities_header") {
            Text(
                text = "People & Utilities",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        if (personGroups.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "people_avatar_strip") {
                PeopleAvatarStrip(
                    people = personGroups,
                    mediaById = mediaById,
                    onOpenPeople = onOpenPeople
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }, key = "utilities_list") {
            FolioDepthCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column {
                    AlbumUtilityRow(
                        icon = Icons.Outlined.NearMe,
                        title = "AirShare Offline",
                        subtitle = "Direct Android-to-Android Nearby sharing (free)",
                        countText = "P2P",
                        onClick = onOpenAirShare,
                        tag = "shortcut_airshare"
                    )
                    UtilityDivider()
                    AlbumUtilityRow(
                        icon = Icons.Outlined.Face,
                        title = "People",
                        subtitle = if (peopleCount > 0) "$peopleCount groups on device" else "Offline face grouping",
                        countText = if (peopleCount > 0) "$peopleCount" else null,
                        onClick = onOpenPeople,
                        tag = "shortcut_people"
                    )
                    UtilityDivider()
                    AlbumUtilityRow(
                        icon = Icons.Outlined.Lock,
                        title = "Hidden Vault",
                        subtitle = "PIN & biometric encrypted SQLCipher storage",
                        countText = null,
                        onClick = onOpenVault,
                        tag = "shortcut_vault"
                    )
                    UtilityDivider()
                    AlbumUtilityRow(
                        icon = Icons.Outlined.ContentCopy,
                        title = "Duplicates",
                        subtitle = "Find exact & perceptual matches",
                        countText = null,
                        onClick = onOpenDuplicates,
                        tag = "shortcut_duplicates"
                    )
                    UtilityDivider()
                    AlbumUtilityRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Trash",
                        subtitle = "Recently deleted items",
                        countText = if (trashCount > 0) "$trashCount" else null,
                        onClick = onOpenTrash,
                        tag = "shortcut_trash"
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNameDialog(
            title = "Create Virtual Album",
            label = "Album name",
            initialValue = "",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreateAlbum(name, null)
                showCreateDialog = false
            }
        )
    }

    renameTarget?.let { target ->
        CreateNameDialog(
            title = "Rename Album",
            label = "New name",
            initialValue = target.name,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameAlbum(target.id, newName)
                renameTarget = null
            }
        )
    }
}

private enum class AlbumSort(val label: String) {
    RECENT("Default"),
    NAME("A to Z"),
    COUNT("Most items")
}

@Composable
private fun AlbumCompactRow(
    title: String,
    count: Int,
    coverUri: String,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    tag: String
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val cs = MaterialTheme.colorScheme
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = cs.surface.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cs.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (coverUri.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(Uri.parse(coverUri)).size(160).build(),
                        imageLoader = imageLoader,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Outlined.Folder, null, tint = cs.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "$count items",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
            if (onRename != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Album options")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        onRename?.let {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                onClick = { menuExpanded = false; it() }
                            )
                        }
                        onDelete?.let {
                            DropdownMenuItem(
                                text = { Text("Delete Album") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                onClick = { menuExpanded = false; it() }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CountChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun UtilityDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun PeopleAvatarStrip(
    people: List<PersonGroupEntity>,
    mediaById: Map<String, MediaItemEntity>,
    onOpenPeople: () -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val cs = MaterialTheme.colorScheme
    val visiblePeople = people.take(5)
    val overflow = (people.size - visiblePeople.size).coerceAtLeast(0)

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        items(visiblePeople, key = { it.id }) { person ->
            val coverUri = mediaById[person.coverMediaId]?.uriString.orEmpty()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .clickable(onClick = onOpenPeople)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(cs.surfaceVariant)
                        .border(1.5.dp, cs.primary.copy(alpha = 0.35f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverUri.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(Uri.parse(coverUri))
                                .size(160)
                                .crossfade(true)
                                .build(),
                            imageLoader = imageLoader,
                            contentDescription = person.label,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Face,
                            contentDescription = person.label,
                            tint = cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = person.label,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (overflow > 0) {
            item(key = "people_more") {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(64.dp)
                        .clickable(onClick = onOpenPeople)
                ) {
                    Box(
                        modifier = Modifier
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(cs.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+$overflow More",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumFrostedGridCard(
    title: String,
    count: Int,
    coverUri: String,
    onClick: () -> Unit,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    tag: String
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = cs.surfaceVariant,
        border = BorderStroke(1.dp, tokens.borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.95f)
            .testTag(tag)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (coverUri.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(coverUri))
                        .size(420)
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier
                        .size(42.dp)
                        .align(Alignment.Center)
                )
            }

            if (onRename != null || onDelete != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.42f))
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            Icons.Outlined.MoreVert,
                            contentDescription = "Album options",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        onRename?.let { renameAction ->
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Outlined.DriveFileRenameOutline, null) },
                                onClick = {
                                    menuExpanded = false
                                    renameAction()
                                }
                            )
                        }
                        onDelete?.let { deleteAction ->
                            DropdownMenuItem(
                                text = { Text("Delete Album") },
                                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null) },
                                onClick = {
                                    menuExpanded = false
                                    deleteAction()
                                }
                            )
                        }
                    }
                }
            }

            // Frosted pill label anchored at bottom (inspired by Image 1)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black.copy(alpha = 0.52f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$title ($count)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun AlbumUtilityRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    countText: String?,
    onClick: () -> Unit,
    tag: String
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .minimumInteractiveComponentSize()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.primaryContainer.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = cs.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (countText != null) {
            Text(
                text = countText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(cs.primaryContainer.copy(alpha = 0.65f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
