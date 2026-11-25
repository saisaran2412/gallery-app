package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Scanner
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.FolioImageLoader
import com.example.core.util.Formatters
import com.example.data.scanner.DuplicateGroup
import com.example.data.scanner.HoldReason
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.ScanControlBar

@Composable
fun DuplicatesScreen(
    groups: List<DuplicateGroup>,
    scanProgress: Pair<Int, Int>?,
    onBack: () -> Unit,
    onStartScan: () -> Unit,
    hold: HoldReason = HoldReason.NONE,
    onPauseScan: () -> Unit = {},
    onResumeScan: () -> Unit = {},
    onStopScan: () -> Unit = {},
    onTrashSelected: (List<String>) -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    var checkedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

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
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Duplicate Finder", style = MaterialTheme.typography.titleLarge)
                Text(
                    "SHA-256 exact + dHash similar • Never auto-deletes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onStartScan,
                enabled = scanProgress == null,
                modifier = Modifier.testTag("scan_duplicates_button")
            ) {
                Icon(Icons.Outlined.Scanner, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (scanProgress == null) "Scan" else "Scanning")
            }
        }

        scanProgress?.let { (cur, total) ->
            ScanControlBar(
                current = cur, total = total, label = "Hashing", hold = hold,
                onPause = onPauseScan, onResume = onResumeScan, onStop = onStopScan
            )
        }

        if (groups.isEmpty() && scanProgress == null) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("Find Exact & Similar Photos", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap Scan to compute SHA-256 checksums and perceptual hashes offline. You always review and confirm before moving anything to Trash.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(groups, key = { it.id }) { group ->
                    FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(group.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    if (group.isExact) "SHA-256" else "Perceptual",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(group.items, key = { it.id }) { item ->
                                    val isChecked = item.id in checkedIds
                                    Column(
                                        modifier = Modifier
                                            .width(124.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                            .clickable {
                                                checkedIds = if (isChecked) checkedIds - item.id else checkedIds + item.id
                                            }
                                            .padding(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(104.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(Uri.parse(item.uriString))
                                                    .size(320)
                                                    .build(),
                                                imageLoader = imageLoader,
                                                contentDescription = item.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    checkedIds = if (checked) checkedIds + item.id else checkedIds - item.id
                                                },
                                                modifier = Modifier.align(Alignment.TopEnd)
                                            )
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = Formatters.formatFileSize(item.sizeBytes),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (checkedIds.isNotEmpty()) {
                Button(
                    onClick = {
                        val ids = checkedIds.toList()
                        checkedIds = emptySet()
                        onTrashSelected(ids)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(50.dp)
                        .testTag("confirm_trash_duplicates_button")
                ) {
                    Icon(Icons.Outlined.DeleteOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Move ${checkedIds.size} Selected to Recycle Bin")
                }
            }
        }
    }
}
