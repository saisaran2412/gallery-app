package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.FolioImageLoader
import com.example.data.local.MediaItemEntity
import com.example.data.local.PersonGroupEntity
import com.example.ui.components.CreateNameDialog
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.viewmodel.TimelineSection
import com.example.data.scanner.HoldReason
import com.example.ui.components.ScanControlBar

@Composable
fun PeopleScreen(
    people: List<PersonGroupEntity>,
    mediaById: Map<String, MediaItemEntity>,
    faceScanProgress: Pair<Int, Int>?,
    selectedPersonMedia: List<MediaItemEntity>,
    selectedPerson: PersonGroupEntity?,
    gridColumns: Int,
    onBack: () -> Unit,
    onSelectPerson: (PersonGroupEntity?) -> Unit,
    onScanFaces: () -> Unit,
    hold: HoldReason = HoldReason.NONE,
    onPauseScan: () -> Unit = {},
    onResumeScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onRenamePerson: (Long, String) -> Unit,
    onDeleteAllFaceData: () -> Unit,
    onMediaClick: (MediaItemEntity) -> Unit
) {
    BackHandler {
        if (selectedPerson != null) {
            onSelectPerson(null)
        } else {
            onBack()
        }
    }

    var renameTarget by remember { mutableStateOf<PersonGroupEntity?>(null) }
    var showEraseDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (selectedPerson != null) onSelectPerson(null) else onBack()
                },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selectedPerson?.label ?: "People (Offline)",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "On-device face detection • Never leaves your phone",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedPerson == null) {
                if (people.isNotEmpty()) {
                    IconButton(
                        onClick = { showEraseDialog = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Outlined.DeleteSweep, contentDescription = "Delete all face data")
                    }
                }
                Button(
                    onClick = onScanFaces,
                    enabled = faceScanProgress == null,
                    modifier = Modifier.testTag("scan_faces_button")
                ) {
                    Icon(Icons.Outlined.PersonSearch, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (faceScanProgress == null) "Scan" else "Scanning")
                }
            }
        }

        faceScanProgress?.let { (cur, total) ->
            ScanControlBar(
                current = cur, total = total, label = "Scanning photo", hold = hold,
                onPause = onPauseScan, onResume = onResumeScan, onStop = onStopScan
            )
        }

        if (selectedPerson != null) {
            val sections = remember(selectedPersonMedia) {
                if (selectedPersonMedia.isEmpty()) emptyList()
                else listOf(TimelineSection("${selectedPerson.label} (${selectedPersonMedia.size})", selectedPersonMedia))
            }
            MixedMediaTimelineGrid(
                sections = sections,
                columns = gridColumns,
                selectedIds = emptySet(),
                isSelectionMode = false,
                onItemClick = onMediaClick,
                onItemLongPress = {},
                contentPadding = PaddingValues(bottom = 32.dp)
            )
        } else if (people.isEmpty()) {
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
                        Icon(Icons.Outlined.Face, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
                        Text("Offline People Grouping", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap Scan to group faces locally using Android's offline detector. Rename groups or erase all face data anytime.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            val context = LocalContext.current
            val imageLoader = remember(context) { FolioImageLoader.get(context) }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 136.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(people, key = { it.id }) { person ->
                    val cover = mediaById[person.coverMediaId]
                    FolioDepthCard(
                        onClick = { onSelectPerson(person) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(84.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cover != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(Uri.parse(cover.uriString))
                                            .size(240)
                                            .build(),
                                        imageLoader = imageLoader,
                                        contentDescription = person.label,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Outlined.Face, null, modifier = Modifier.size(34.dp))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = person.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                IconButton(
                                    onClick = { renameTarget = person },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.DriveFileRenameOutline,
                                        contentDescription = "Rename person",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Text(
                                text = "${person.faceCount} photo(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    renameTarget?.let { target ->
        CreateNameDialog(
            title = "Name Person",
            label = "Name",
            initialValue = target.label,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenamePerson(target.id, newName)
                renameTarget = null
            }
        )
    }

    if (showEraseDialog) {
        AlertDialog(
            onDismissRequest = { showEraseDialog = false },
            title = { Text("Erase All Face Data?") },
            text = { Text("Deletes all local face signatures and person labels. Your photos will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllFaceData()
                        showEraseDialog = false
                    }
                ) { Text("Erase") }
            },
            dismissButton = {
                TextButton(onClick = { showEraseDialog = false }) { Text("Cancel") }
            }
        )
    }
}
