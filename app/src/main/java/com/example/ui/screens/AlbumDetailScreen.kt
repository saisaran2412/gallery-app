package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.AlbumSummary
import com.example.data.local.MediaItemEntity
import com.example.ui.components.CreateNameDialog
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.viewmodel.TimelineSection

@Composable
fun AlbumDetailScreen(
    title: String,
    isVirtualAlbum: Boolean,
    albumId: Long?,
    subfolders: List<AlbumSummary>,
    items: List<MediaItemEntity>,
    gridColumns: Int,
    onBack: () -> Unit,
    onMediaClick: (MediaItemEntity) -> Unit,
    onOpenSubfolder: (Long) -> Unit,
    onCreateSubfolder: (String, Long) -> Unit,
    onRenameAlbum: (Long, String) -> Unit,
    onOpenSlideshow: () -> Unit
) {
    BackHandler { onBack() }

    var showCreateSubfolder by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }

    val sections = remember(items) {
        if (items.isEmpty()) emptyList() else listOf(TimelineSection("All Items", items))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("album_detail_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${items.size} items" + if (isVirtualAlbum) " • Virtual Album" else " • Device Folder",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.isNotEmpty()) {
                IconButton(onClick = onOpenSlideshow, modifier = Modifier.minimumInteractiveComponentSize()) {
                    Icon(Icons.Outlined.Slideshow, contentDescription = "Slideshow")
                }
            }
            if (isVirtualAlbum && albumId != null) {
                IconButton(
                    onClick = { showCreateSubfolder = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.CreateNewFolder, contentDescription = "New subfolder")
                }
                IconButton(
                    onClick = { showRenameDialog = true },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Outlined.DriveFileRenameOutline, contentDescription = "Rename album")
                }
            }
        }

        if (subfolders.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(subfolders, key = { it.id }) { sub ->
                    AssistChip(
                        onClick = { onOpenSubfolder(sub.id) },
                        label = { Text("${sub.name} (${sub.itemCount})") },
                        leadingIcon = { Icon(Icons.Outlined.Folder, null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }
        }

        if (items.isEmpty()) {
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("This album is empty", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Long-press any photo or video in Gallery to add items here without moving originals.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            MixedMediaTimelineGrid(
                sections = sections,
                columns = gridColumns,
                selectedIds = emptySet(),
                isSelectionMode = false,
                onItemClick = onMediaClick,
                onItemLongPress = {},
                contentPadding = PaddingValues(bottom = 32.dp)
            )
        }
    }

    if (showCreateSubfolder && albumId != null) {
        CreateNameDialog(
            title = "Create Subfolder",
            label = "Subfolder name",
            initialValue = "",
            onDismiss = { showCreateSubfolder = false },
            onConfirm = { name ->
                onCreateSubfolder(name, albumId)
                showCreateSubfolder = false
            }
        )
    }

    if (showRenameDialog && albumId != null) {
        CreateNameDialog(
            title = "Rename Album",
            label = "Album name",
            initialValue = title,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                onRenameAlbum(albumId, newName)
                showRenameDialog = false
            }
        )
    }
}
