package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.core.util.FolioImageLoader
import com.example.core.util.Formatters
import com.example.data.local.MediaItemEntity
import com.example.share.AirShareMode
import com.example.share.AirSharePeer
import com.example.share.AirShareSessionState
import com.example.share.AirShareTransferItem
import com.example.share.AirShareTransferStatus
import com.example.share.PlayServicesCompatibility
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.FolioSegmentedControl
import com.example.ui.theme.LocalFolioTokens
import com.example.ui.viewmodel.AirShareUiState
import com.example.ui.viewmodel.AirShareViewModel

@Composable
internal fun PlayServicesCompatibilityBanner(compatibility: PlayServicesCompatibility) {
    val cs = MaterialTheme.colorScheme
    FolioDepthCard(
        containerColor = cs.errorContainer.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("airshare_compat_banner")
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = cs.error,
                modifier = Modifier.size(24.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = compatibility.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface
                )
                Text(
                    text = compatibility.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun AirShareRadarCard(
    mode: AirShareMode,
    deviceName: String,
    sessionState: AirShareSessionState,
    compatibility: PlayServicesCompatibility,
    hasPermissions: Boolean,
    reducedMotion: Boolean,
    queuedCount: Int,
    onStartScanning: () -> Unit,
    onStartReceiving: () -> Unit,
    onStopSession: () -> Unit,
    onSendQueued: (String) -> Unit,
    onDisconnect: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val isActiveRadar = sessionState is AirShareSessionState.Scanning ||
        sessionState is AirShareSessionState.ReceivingReady

    val pulseProgress = if (isActiveRadar && !reducedMotion) {
        val transition = rememberInfiniteTransition(label = "radar")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "radar_ring"
        ).value
    } else {
        0.35f
    }

    FolioDepthCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(116.dp),
                contentAlignment = Alignment.Center
            ) {
                val ringColor = cs.primary
                val baseTrack = cs.outline.copy(alpha = 0.35f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxRadius = size.minDimension / 2f
                    drawCircle(color = baseTrack, radius = maxRadius * 0.92f, style = Stroke(2.dp.toPx()))
                    drawCircle(color = baseTrack, radius = maxRadius * 0.64f, style = Stroke(1.5.dp.toPx()))
                    if (isActiveRadar) {
                        val r = maxRadius * (0.35f + 0.6f * pulseProgress)
                        drawCircle(
                            color = ringColor.copy(alpha = (1f - pulseProgress).coerceIn(0.15f, 0.85f)),
                            radius = r,
                            style = Stroke(3.dp.toPx())
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = cs.primaryContainer,
                    modifier = Modifier.size(54.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (mode == AirShareMode.SEND) Icons.Outlined.NearMe else Icons.Outlined.Devices,
                            contentDescription = null,
                            tint = cs.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Text(
                text = "This Device: $deviceName",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant
            )

            when (sessionState) {
                is AirShareSessionState.Connected -> {
                    Text(
                        text = "Connected to ${sessionState.peer.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (mode == AirShareMode.SEND) {
                            Button(
                                onClick = { onSendQueued(sessionState.peer.endpointId) },
                                enabled = queuedCount > 0,
                                modifier = Modifier.testTag("airshare_send_files_button")
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Send, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Send $queuedCount Item(s)")
                            }
                        }
                        OutlinedButton(
                            onClick = { onDisconnect(sessionState.peer.endpointId) },
                            modifier = Modifier.testTag("airshare_disconnect_button")
                        ) {
                            Text("Disconnect")
                        }
                    }
                }

                is AirShareSessionState.Scanning -> {
                    Text(
                        text = "Scanning nearby Android devices...",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onStopSession,
                        modifier = Modifier.testTag("airshare_stop_button")
                    ) {
                        Icon(Icons.Outlined.StopCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop Scanning")
                    }
                }

                is AirShareSessionState.ReceivingReady -> {
                    Text(
                        text = "Visible to nearby senders as \"$deviceName\"",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(
                        onClick = onStopSession,
                        modifier = Modifier.testTag("airshare_stop_button")
                    ) {
                        Icon(Icons.Outlined.StopCircle, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Stop Receiving")
                    }
                }

                is AirShareSessionState.Error -> {
                    Text(
                        text = sessionState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.error
                    )
                    Button(
                        onClick = { if (mode == AirShareMode.SEND) onStartScanning() else onStartReceiving() },
                        enabled = compatibility.isSupported && hasPermissions
                    ) {
                        Text("Try Again")
                    }
                }

                else -> {
                    Text(
                        text = if (mode == AirShareMode.SEND) {
                            "Select photos/videos below and scan for a nearby Android receiver."
                        } else {
                            "Make this device discoverable to receive originals into your AirShare folder."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            if (mode == AirShareMode.SEND) onStartScanning() else onStartReceiving()
                        },
                        enabled = compatibility.isSupported && hasPermissions,
                        modifier = Modifier.testTag("airshare_primary_action_button")
                    ) {
                        Text(if (mode == AirShareMode.SEND) "Scan Nearby Devices" else "Ready to Receive")
                    }
                }
            }
        }
    }
}

@Composable
internal fun QueuedMediaStrip(
    items: List<MediaItemEntity>,
    onRemove: (String) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items, key = { it.id }) { item ->
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(item.uriString))
                        .size(200)
                        .apply { if (item.isVideo) videoFrameMillis(1_000L) }
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = item.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                IconButton(
                    onClick = { onRemove(item.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun DiscoveredPeerCard(
    peer: AirSharePeer,
    onConnect: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(cs.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, tint = cs.primary)
                }
                Column {
                    Text(
                        text = peer.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = peer.protocol.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onConnect,
                modifier = Modifier.testTag("airshare_connect_${peer.endpointId}")
            ) {
                Text("Connect")
            }
        }
    }
}

@Composable
internal fun TransferProgressCard(item: AirShareTransferItem) {
    val cs = MaterialTheme.colorScheme
    FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${if (item.isIncoming) "Receiving" else "Sending"} • ${item.fileName}",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (item.status == AirShareTransferStatus.COMPLETED) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = "Completed",
                        tint = cs.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = item.status.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.primary
                    )
                }
            }

            if (item.status == AirShareTransferStatus.TRANSFERRING || item.status == AirShareTransferStatus.QUEUED) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                )
            }

            Text(
                text = if (item.totalBytes > 0L) {
                    "${Formatters.formatFileSize(item.bytesTransferred)} / ${Formatters.formatFileSize(item.totalBytes)}"
                } else {
                    "Preparing local transfer..."
                },
                style = MaterialTheme.typography.labelSmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GalleryMediaPickerSheet(
    media: List<MediaItemEntity>,
    alreadySelectedIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirmSelection: (List<MediaItemEntity>) -> Unit
) {
    val context = LocalContext.current
    val imageLoader = remember(context) { FolioImageLoader.get(context) }
    val cs = MaterialTheme.colorScheme
    var selectedIds by remember { mutableStateOf(alreadySelectedIds) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Gallery Media (${selectedIds.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Button(
                    onClick = {
                        onConfirmSelection(media.filter { it.id in selectedIds })
                    },
                    enabled = selectedIds.isNotEmpty()
                ) {
                    Text("Done")
                }
            }

            if (media.isEmpty()) {
                Text(
                    text = "No offline gallery items available yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                ) {
                    items(media, key = { it.id }) { item ->
                        val isPicked = item.id in selectedIds
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .border(
                                    width = if (isPicked) 2.5.dp else 0.dp,
                                    color = if (isPicked) cs.primary else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    selectedIds = if (isPicked) selectedIds - item.id else selectedIds + item.id
                                }
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(Uri.parse(item.uriString))
                                    .size(200)
                                    .apply { if (item.isVideo) videoFrameMillis(1_000L) }
                                    .crossfade(true)
                                    .build(),
                                imageLoader = imageLoader,
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

