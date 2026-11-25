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
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirShareScreen(
    uiState: AirShareUiState,
    galleryMedia: List<MediaItemEntity>,
    reducedMotion: Boolean,
    shareAnimStyle: com.example.core.storage.ShareAnimStyle = com.example.core.storage.ShareAnimStyle.STREAM,
    onBack: () -> Unit,
    onSelectMode: (AirShareMode) -> Unit,
    onPermissionsResult: (Boolean) -> Unit,
    onStartScanning: () -> Unit,
    onStartReceiving: () -> Unit,
    onConnectPeer: (String) -> Unit,
    onAcceptConnection: (String) -> Unit,
    onRejectConnection: (String) -> Unit,
    onSendQueued: (String) -> Unit,
    onDisconnect: (String) -> Unit,
    onStopSession: () -> Unit,
    onAddGalleryMedia: (List<MediaItemEntity>) -> Unit,
    onImportPickedUris: (List<Uri>) -> Unit,
    onRemoveQueuedItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler {
        onStopSession()
        onBack()
    }

    DisposableEffect(Unit) {
        onDispose { onStopSession() }
    }

    var showGalleryPickerSheet by remember { mutableStateOf(false) }
    var showArchitectureDetails by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    // Full-screen send / receive moment. A batch plays one animation, with a short cooldown.
    var animEvent by remember { mutableStateOf<com.example.ui.components.ShareAnimEvent?>(null) }
    val startedOut = remember { mutableSetOf<Long>() }
    val doneIn = remember { mutableSetOf<Long>() }
    var lastPlay by remember { mutableStateOf(0L) }
    LaunchedEffect(uiState.transfers) {
        if (shareAnimStyle.isJourney && !reducedMotion) return@LaunchedEffect
        val t = uiState.transfers
        val outs = t.filter {
            !it.isIncoming && it.status == com.example.share.AirShareTransferStatus.TRANSFERRING && startedOut.add(it.payloadId)
        }
        val ins = t.filter {
            it.isIncoming && it.status == com.example.share.AirShareTransferStatus.COMPLETED && doneIn.add(it.payloadId)
        }
        val now = System.currentTimeMillis()
        if (animEvent != null || now - lastPlay < 4000L) return@LaunchedEffect
        val out = outs.firstOrNull()
        val inn = ins.firstOrNull()
        if (out != null) {
            val uri = uiState.queuedItems.firstOrNull { q -> q.displayName.equals(out.fileName, true) }?.uriString
            animEvent = com.example.ui.components.ShareAnimEvent(System.nanoTime(), com.example.ui.components.ShareRole.SEND, uri, out.fileName)
            lastPlay = now
        } else if (inn != null) {
            animEvent = com.example.ui.components.ShareAnimEvent(System.nanoTime(), com.example.ui.components.ShareRole.RECEIVE, inn.localUri, inn.fileName)
            lastPlay = now
        }
    }
    com.example.ui.components.ShareMagicOverlay(animEvent, shareAnimStyle, reducedMotion) { animEvent = null }

    // Journey styles (rider / plane / rocket / drone): live for the whole real transfer.
    var journey by remember { mutableStateOf<com.example.ui.components.ShareJourney?>(null) }
    var hiddenJourneyId by remember { mutableStateOf(-1L) }
    val journeyMembers = remember { mutableSetOf<Long>() }
    LaunchedEffect(uiState.transfers, shareAnimStyle, reducedMotion) {
        if (!shareAnimStyle.isJourney || reducedMotion) { journey = null; return@LaunchedEffect }
        val all = uiState.transfers
        val active = all.filter {
            it.status == com.example.share.AirShareTransferStatus.TRANSFERRING || it.status == com.example.share.AirShareTransferStatus.QUEUED
        }
        val current = journey
        if (current == null) {
            if (active.isEmpty()) return@LaunchedEffect
            val role = if (active.first().isIncoming) com.example.ui.components.ShareRole.RECEIVE else com.example.ui.components.ShareRole.SEND
            journeyMembers.clear()
            active.filter { (it.isIncoming) == (role == com.example.ui.components.ShareRole.RECEIVE) }.forEach { journeyMembers.add(it.payloadId) }
            val peer = (uiState.sessionState as? AirShareSessionState.Connected)?.peer?.displayName ?: "nearby device"
            journey = com.example.ui.components.ShareJourney(System.nanoTime(), role, peer, 0f, false, true, 0, journeyMembers.size)
            return@LaunchedEffect
        }
        // late joiners of the same direction belong to the same trip
        active.filter { (it.isIncoming) == (current.role == com.example.ui.components.ShareRole.RECEIVE) }.forEach { journeyMembers.add(it.payloadId) }
        val members = all.filter { it.payloadId in journeyMembers }
        if (members.isEmpty()) return@LaunchedEffect
        val total = members.sumOf { it.totalBytes }.coerceAtLeast(1L)
        val sent = members.sumOf { if (it.status == com.example.share.AirShareTransferStatus.COMPLETED) it.totalBytes else it.bytesTransferred }
        val finished = members.count {
            it.status == com.example.share.AirShareTransferStatus.COMPLETED ||
                it.status == com.example.share.AirShareTransferStatus.FAILED ||
                it.status == com.example.share.AirShareTransferStatus.CANCELLED
        }
        val completed = members.count { it.status == com.example.share.AirShareTransferStatus.COMPLETED }
        journey = current.copy(
            target = (sent.toFloat() / total).coerceIn(0f, 1f),
            done = finished == members.size,
            success = completed > 0,
            doneCount = completed,
            count = members.size
        )
    }
    journey?.let { j ->
        if (j.id != hiddenJourneyId) {
            com.example.ui.components.ShareJourneyOverlay(
                journey = j,
                style = shareAnimStyle,
                onHide = { hiddenJourneyId = j.id },
                onFinished = { journey = null; journeyMembers.clear() }
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        onPermissionsResult(allGranted)
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 25)
    ) { uris ->
        if (uris.isNotEmpty()) onImportPickedUris(uris)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    onStopSession()
                    onBack()
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("airshare_back_button")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AirShare Offline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Free P2P • Google Nearby Connections",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(cs.primaryContainer.copy(alpha = 0.7f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = cs.primary, modifier = Modifier.size(14.dp))
                Text(
                    text = "No Cloud",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.primary
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("airshare_lazy_column"),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Google Play Services Compatibility Card
            if (!uiState.compatibility.isSupported) {
                item(key = "compat_warning") {
                    PlayServicesCompatibilityBanner(compatibility = uiState.compatibility)
                }
            }

            // Permissions Card (only when runtime Nearby permissions are missing)
            if (!uiState.hasPermissions && uiState.compatibility.isSupported) {
                item(key = "permissions_card") {
                    FolioDepthCard(
                        containerColor = cs.primaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Security, contentDescription = null, tint = cs.primary)
                                Text(
                                    text = "Nearby Device Permission Needed",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "Allow Bluetooth and local Wi-Fi device discovery so Nearby Connections can exchange photos directly without internet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                            Button(
                                onClick = {
                                    permissionLauncher.launch(AirShareViewModel.requiredNearbyPermissions())
                                },
                                modifier = Modifier.testTag("airshare_grant_permissions_button")
                            ) {
                                Text("Grant Nearby Access")
                            }
                        }
                    }
                }
            }

            // Send / Receive segmented toggle
            item(key = "mode_switch") {
                FolioSegmentedControl(
                    options = listOf("Send", "Receive"),
                    selectedIndex = if (uiState.mode == AirShareMode.SEND) 0 else 1,
                    onSelect = { idx ->
                        onSelectMode(if (idx == 0) AirShareMode.SEND else AirShareMode.RECEIVE)
                    },
                    testTagPrefix = "airshare_mode",
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Radar & Connection Status Hero Card
            item(key = "radar_card") {
                AirShareRadarCard(
                    mode = uiState.mode,
                    deviceName = uiState.localDeviceName,
                    sessionState = uiState.sessionState,
                    compatibility = uiState.compatibility,
                    hasPermissions = uiState.hasPermissions,
                    reducedMotion = reducedMotion,
                    queuedCount = uiState.queuedItems.size,
                    onStartScanning = onStartScanning,
                    onStartReceiving = onStartReceiving,
                    onStopSession = onStopSession,
                    onSendQueued = onSendQueued,
                    onDisconnect = onDisconnect
                )
            }

            // Send Queue (only in Send mode)
            if (uiState.mode == AirShareMode.SEND) {
                item(key = "send_queue") {
                    FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Ready to Send (${uiState.queuedItems.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "Original quality transferred directly device-to-device",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { showGalleryPickerSheet = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("airshare_pick_from_gallery_button")
                                ) {
                                    Icon(Icons.Outlined.PhotoLibrary, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("From Gallery", maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("airshare_system_picker_button")
                                ) {
                                    Icon(Icons.Outlined.AddPhotoAlternate, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Photo Picker", maxLines = 1)
                                }
                            }

                            if (uiState.queuedItems.isNotEmpty()) {
                                QueuedMediaStrip(
                                    items = uiState.queuedItems,
                                    onRemove = onRemoveQueuedItem
                                )
                            }
                        }
                    }
                }
            }

            // Discovered Nearby Devices (in Send mode)
            if (uiState.mode == AirShareMode.SEND && uiState.discoveredPeers.isNotEmpty()) {
                item(key = "peers_header") {
                    Text(
                        text = "Nearby Android Devices (${uiState.discoveredPeers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.discoveredPeers, key = { it.endpointId }) { peer ->
                    DiscoveredPeerCard(
                        peer = peer,
                        onConnect = { onConnectPeer(peer.endpointId) }
                    )
                }
            }

            // Active & Recent Transfers
            if (uiState.transfers.isNotEmpty()) {
                item(key = "transfers_header") {
                    Text(
                        text = "Transfers (${uiState.transfers.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                items(uiState.transfers, key = { it.payloadId }) { transfer ->
                    TransferProgressCard(item = transfer)
                }
            }

            // Free Offline & Cross-Platform Modularity Guarantee Card
            item(key = "free_offline_info") {
                FolioDepthCard(
                    onClick = { showArchitectureDetails = !showArchitectureDetails },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("airshare_architecture_card")
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Outlined.Info, contentDescription = null, tint = cs.primary, modifier = Modifier.size(18.dp))
                                Text(
                                    text = "100% Free & Modular Offline Sharing",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Icon(
                                imageVector = if (showArchitectureDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                                contentDescription = "Toggle details",
                                tint = cs.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Zero subscription, zero per-transfer fees, and zero cloud uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = cs.onSurfaceVariant
                        )
                        if (showArchitectureDetails) {
                            Text(
                                text = "• Android-to-Android uses Google Nearby Connections directly over local Wi-Fi Direct & Bluetooth.\n" +
                                    "• Media is never uploaded to Google servers or third-party relays.\n" +
                                    "• Built behind a modular AirShareTransport engine so future iPhone-to-Android and iPhone-to-iPhone sharing can plug in compatible local technologies (mDNS/Bonjour & Multipeer).\n" +
                                    "• Optional remote cloud relays, if ever offered in a future release, would be strictly optional and clearly disclose any third-party bandwidth costs before use.",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // 4-Digit Verification Dialog before accepting transfer
    val awaiting = uiState.sessionState as? AirShareSessionState.AwaitingConfirmation
    if (awaiting != null) {
        AlertDialog(
            onDismissRequest = { onRejectConnection(awaiting.pending.endpointId) },
            title = { Text("Confirm Nearby Device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Pair with ${awaiting.pending.peerName}?",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Verify both screens show the same 4-digit security code before transferring photos or videos:",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(cs.primaryContainer)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = awaiting.pending.authDigits,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.onPrimaryContainer
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onAcceptConnection(awaiting.pending.endpointId) },
                    modifier = Modifier.testTag("airshare_accept_auth_button")
                ) {
                    Text("Match & Connect")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { onRejectConnection(awaiting.pending.endpointId) },
                    modifier = Modifier.testTag("airshare_reject_auth_button")
                ) {
                    Text("Decline")
                }
            }
        )
    }

    // In-App Gallery Picker Sheet
    if (showGalleryPickerSheet) {
        GalleryMediaPickerSheet(
            media = galleryMedia,
            alreadySelectedIds = uiState.queuedItems.map { it.id }.toSet(),
            onDismiss = { showGalleryPickerSheet = false },
            onConfirmSelection = { chosen ->
                onAddGalleryMedia(chosen)
                showGalleryPickerSheet = false
            }
        )
    }
}
