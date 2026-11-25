package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.util.FolioImageLoader
import com.example.core.util.Formatters
import com.example.data.local.MediaItemEntity
import com.example.security.BiometricCapability
import com.example.security.VaultBiometricHelper
import com.example.ui.components.FolioDepthCard
import com.example.ui.viewmodel.BiometricPrepareResult
import com.example.ui.viewmodel.VaultUiState
import javax.crypto.Cipher

@Composable
fun VaultScreen(
    state: VaultUiState,
    onBack: () -> Unit,
    onSetupPin: (String) -> Unit,
    onVerifyPin: (String) -> Unit,
    onPrepareBiometricUnlock: () -> BiometricPrepareResult,
    onBiometricUnlockSuccess: (Cipher?) -> Unit,
    onPrepareBiometricEnrollment: () -> BiometricPrepareResult,
    onBiometricEnrollmentSuccess: (Cipher?) -> Unit,
    onDisableBiometric: () -> Unit,
    onBiometricError: (String) -> Unit,
    onLockVault: () -> Unit,
    onRequestDecrypt: (MediaItemEntity) -> Unit,
    onRemoveFromVault: (MediaItemEntity) -> Unit
) {
    BackHandler {
        onLockVault()
        onBack()
    }

    val hostContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(Unit) {
        val window = (hostContext as? android.app.Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            onLockVault()
        }
    }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val canBiometric = state.biometricCapability == BiometricCapability.STRONG_CRYPTO ||
        state.biometricCapability == BiometricCapability.WEAK_ONLY

    fun launchBiometricUnlockPrompt() {
        val act = activity ?: return
        when (val prep = onPrepareBiometricUnlock()) {
            is BiometricPrepareResult.Ready -> {
                VaultBiometricHelper.authenticateForUnlock(
                    activity = act,
                    cipher = prep.cipher,
                    onSuccess = { authenticatedCipher ->
                        onBiometricUnlockSuccess(authenticatedCipher)
                    },
                    onCancelOrUsePin = { /* User chose PIN fallback */ },
                    onError = { err -> onBiometricError(err) }
                )
            }
            is BiometricPrepareResult.Failed -> {
                onBiometricError(prep.message)
            }
        }
    }

    fun launchBiometricEnrollmentPrompt() {
        val act = activity ?: return
        when (val prep = onPrepareBiometricEnrollment()) {
            is BiometricPrepareResult.Ready -> {
                VaultBiometricHelper.authenticateForEnrollment(
                    activity = act,
                    cipher = prep.cipher,
                    onSuccess = { authenticatedCipher ->
                        onBiometricEnrollmentSuccess(authenticatedCipher)
                    },
                    onCancel = { /* User cancelled enrollment */ },
                    onError = { err -> onBiometricError(err) }
                )
            }
            is BiometricPrepareResult.Failed -> {
                onBiometricError(prep.message)
            }
        }
    }

    var autoPrompted by remember { mutableStateOf(false) }
    LaunchedEffect(state.isUnlocked, state.hasPinConfigured, state.biometricEnabled, canBiometric) {
        if (!state.isUnlocked && state.hasPinConfigured && state.biometricEnabled && canBiometric && !autoPrompted) {
            autoPrompted = true
            launchBiometricUnlockPrompt()
        }
    }

    if (!state.isUnlocked) {
        VaultPinGate(
            isSettingUp = !state.hasPinConfigured,
            error = state.pinError,
            showBiometricButton = state.hasPinConfigured && state.biometricEnabled && canBiometric,
            onBack = onBack,
            onSubmitPin = { pin ->
                if (!state.hasPinConfigured) onSetupPin(pin) else onVerifyPin(pin)
            },
            onBiometricClick = { launchBiometricUnlockPrompt() }
        )
    } else {
        var previewItem by remember { mutableStateOf<MediaItemEntity?>(null) }
        var showAuditLogs by remember { mutableStateOf(false) }

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
                        onLockVault()
                        onBack()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hidden Vault", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Room + SQLCipher • Keystore AES-256",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showAuditLogs = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("vault_audit_log_button")
                ) {
                    Icon(Icons.Outlined.History, contentDescription = "Vault access log")
                }
                IconButton(
                    onClick = {
                        onLockVault()
                        onBack()
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lock_vault_button")
                ) {
                    Icon(Icons.Outlined.Lock, contentDescription = "Lock vault")
                }
            }

            if (canBiometric || state.biometricCapability == BiometricCapability.NOT_ENROLLED) {
                FolioDepthCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Biometric Unlock", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = when {
                                        state.biometricCapability == BiometricCapability.NOT_ENROLLED ->
                                            "Enroll a fingerprint or face in Settings first"
                                        state.biometricBoundWithCrypto ->
                                            "Bound to hardware CryptoObject key"
                                        state.biometricCapability == BiometricCapability.STRONG_CRYPTO ->
                                            "Class 3 biometric key binding available"
                                        else -> "Device biometric prompt"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (canBiometric) {
                            Switch(
                                checked = state.biometricEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        launchBiometricEnrollmentPrompt()
                                    } else {
                                        onDisableBiometric()
                                    }
                                },
                                modifier = Modifier.testTag("vault_biometric_switch")
                            )
                        }
                    }
                }
            }

            if (state.vaultItems.isEmpty()) {
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
                            Icon(
                                Icons.Outlined.Shield,
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Text("Vault is Empty", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Move sensitive photos or videos here from the Gallery or Viewer. Records are stored in an encrypted SQLCipher Room database and AES-256-GCM files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                val imageLoader = remember(context) { FolioImageLoader.get(context) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(state.vaultItems, key = { it.id }) { item ->
                        LaunchedEffect(item.id) { onRequestDecrypt(item) }
                        val decryptedUri = state.decryptedPreviewUris[item.id]
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { previewItem = item }
                        ) {
                            if (decryptedUri != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context).data(decryptedUri).size(360).build(),
                                    imageLoader = imageLoader,
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    Icons.Outlined.Lock,
                                    contentDescription = "Decrypting",
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showAuditLogs) {
            AlertDialog(
                onDismissRequest = { showAuditLogs = false },
                title = { Text("Encrypted Vault Log") },
                text = {
                    if (state.auditLogs.isEmpty()) {
                        Text("No vault events recorded yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.height(240.dp)
                        ) {
                            items(state.auditLogs, key = { it.id }) { log ->
                                Column {
                                    Text(
                                        text = "${log.eventType} • ${Formatters.formatFullDate(log.timestampMillis)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = log.details,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAuditLogs = false }) { Text("Close") }
                }
            )
        }

        previewItem?.let { item ->
            val decryptedUri = state.decryptedPreviewUris[item.id]
            AlertDialog(
                onDismissRequest = { previewItem = null },
                title = { Text(item.displayName, maxLines = 1) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (decryptedUri != null) {
                            AsyncImage(
                                model = decryptedUri,
                                imageLoader = FolioImageLoader.get(context),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                        }
                        Text(
                            "Decrypted temporarily in app cache while Vault is unlocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onRemoveFromVault(item)
                            previewItem = null
                        }
                    ) {
                        Icon(Icons.Outlined.LockOpen, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Restore to Gallery")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { previewItem = null }) { Text("Close") }
                }
            )
        }
    }
}

@Composable
private fun VaultPinGate(
    isSettingUp: Boolean,
    error: String?,
    showBiometricButton: Boolean,
    onBack: () -> Unit,
    onSubmitPin: (String) -> Unit,
    onBiometricClick: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        Column(
            modifier = Modifier.widthIn(max = 360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }

            Text(
                text = if (isSettingUp) "Create Vault PIN" else "Unlock Hidden Vault",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = if (isSettingUp) {
                    "Choose a 4–6 digit PIN to protect your SQLCipher encrypted vault."
                } else {
                    "Enter your PIN or authenticate with biometrics to open the Vault."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) { idx ->
                    val filled = idx < pin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
                    )
                }
            }

            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("BIO", "0", "DEL")
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { key ->
                            when (key) {
                                "BIO" -> {
                                    if (showBiometricButton) {
                                        IconButton(
                                            onClick = onBiometricClick,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .testTag("vault_biometric_button")
                                        ) {
                                            Icon(
                                                Icons.Outlined.Fingerprint,
                                                contentDescription = "Biometric unlock",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    } else {
                                        Spacer(Modifier.size(64.dp))
                                    }
                                }
                                "DEL" -> {
                                    IconButton(
                                        onClick = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                                        modifier = Modifier.size(64.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete digit")
                                    }
                                }
                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                if (pin.length < 6) pin += key
                                            }
                                            .testTag("pin_key_$key"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = key,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val entered = pin
                    pin = ""
                    onSubmitPin(entered)
                },
                enabled = pin.length in 4..6,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("vault_submit_pin_button")
            ) {
                Text(if (isSettingUp) "Set PIN & Enter Vault" else "Unlock")
            }
        }
    }
}
