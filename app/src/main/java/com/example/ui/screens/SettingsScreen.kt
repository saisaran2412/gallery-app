package com.example.ui.screens

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.example.core.storage.CacheCleaner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.ModelTraining
import androidx.compose.material.icons.outlined.MotionPhotosPaused
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.storage.AppStorageDirs
import com.example.core.storage.UserSettings
import com.example.ui.components.FolioDepthCard
import com.example.ui.theme.ThemeMode
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onSelectTheme: (ThemeMode) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onSelectShareAnimStyle: (com.example.core.storage.ShareAnimStyle) -> Unit = {},
    onToggleLocationMetadata: (Boolean) -> Unit,
    onSelectAutoCleanDays: (Int) -> Unit,
    onToggleFaceGrouping: (Boolean) -> Unit,
    onImportAiModelPack: (String) -> Unit,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var previewEvent by remember { mutableStateOf<com.example.ui.components.ShareAnimEvent?>(null) }
    var previewJourney by remember { mutableStateOf<com.example.ui.components.ShareJourney?>(null) }
    LaunchedEffect(previewJourney?.id) {
        val id = previewJourney?.id ?: return@LaunchedEffect
        for (i in 1..36) {
            kotlinx.coroutines.delay(100)
            previewJourney = previewJourney?.takeIf { it.id == id }?.copy(target = i / 36f) ?: return@LaunchedEffect
        }
        previewJourney = previewJourney?.takeIf { it.id == id }?.copy(done = true, doneCount = 3)
    }

    val modelPackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0) else null
                }
            }.getOrNull() ?: "offline_pack.tflite"
            val dest = File(AppStorageDirs.aiModelsDir(context), name.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(dest).use { out -> input.copyTo(out) }
                }
            }
            onImportAiModelPack(dest.name)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(
                "Appearance, privacy, storage and offline intelligence.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }

        // ---------- Appearance ----------
        item { SettingsSectionLabel("Appearance") }
        item {
            FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val glass = ThemeMode.entries.filter {
                        it == ThemeMode.LIQUID_GLASS || it == ThemeMode.GLASS_25D ||
                            it == ThemeMode.LIQUID_GLASS_NIGHT || it == ThemeMode.ARCANE_GLASS
                    }
                    val light = ThemeMode.entries.filter { it !in glass && !it.isDark }
                    val dark = ThemeMode.entries.filter { it !in glass && it.isDark }
                    ThemeGroupRow("Glass", glass, settings.themeMode, onSelectTheme)
                    ThemeGroupRow("Light", light, settings.themeMode, onSelectTheme)
                    ThemeGroupRow("Dark", dark, settings.themeMode, onSelectTheme)
                }
            }
        }
        item {
            SettingsCard {
                SettingSwitchRow(
                    icon = Icons.Outlined.MotionPhotosPaused,
                    title = "Reduce motion & effects",
                    subtitle = "Simpler transitions and no glass background glow",
                    checked = settings.reducedMotion,
                    onCheckedChange = onToggleReducedMotion,
                    tag = "switch_reduced_motion"
                )
            }
        }
        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Share animation", style = MaterialTheme.typography.titleSmall)
                    Text(
                        settings.shareAnimStyle.blurb,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.example.core.storage.ShareAnimStyle.entries.forEach { st ->
                            FilterChip(
                                selected = settings.shareAnimStyle == st,
                                onClick = { onSelectShareAnimStyle(st) },
                                label = { Text(st.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                if (settings.shareAnimStyle.isJourney) {
                                    previewJourney = com.example.ui.components.ShareJourney(
                                        System.nanoTime(), com.example.ui.components.ShareRole.SEND, "Preview phone", 0f, false, true, 0, 3
                                    )
                                } else {
                                    previewEvent = com.example.ui.components.ShareAnimEvent(
                                        System.nanoTime(), com.example.ui.components.ShareRole.SEND, null, "Preview.jpg"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Preview send") }
                        OutlinedButton(
                            onClick = {
                                if (settings.shareAnimStyle.isJourney) {
                                    previewJourney = com.example.ui.components.ShareJourney(
                                        System.nanoTime(), com.example.ui.components.ShareRole.RECEIVE, "Preview phone", 0f, false, true, 0, 3
                                    )
                                } else {
                                    previewEvent = com.example.ui.components.ShareAnimEvent(
                                        System.nanoTime(), com.example.ui.components.ShareRole.RECEIVE, null, "Preview.jpg"
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Preview receive") }
                    }
                }
            }
        }

        // ---------- Privacy & Security ----------
        item { SettingsSectionLabel("Privacy & Security") }
        item {
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Strictly offline", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "No internet permission, telemetry or cloud SDKs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Outlined.CheckCircle, "Verified", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                SettingsDivider()
                SettingSwitchRow(
                    icon = Icons.Outlined.LocationOn,
                    title = "Show GPS location",
                    subtitle = "When off, GPS is hidden in Info and removed from JPEGs you share",
                    checked = settings.showLocationMetadata,
                    onCheckedChange = onToggleLocationMetadata,
                    tag = "switch_location_meta"
                )
            }
        }

        // ---------- Storage ----------
        item { SettingsSectionLabel("Storage") }
        item {
            SettingsCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Recycle Bin auto-clean", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Permanently remove deleted items after this time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Never", 7 to "7 days", 30 to "30 days").forEach { (days, label) ->
                            FilterChip(
                                selected = settings.trashAutoCleanDays == days,
                                onClick = { onSelectAutoCleanDays(days) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
                SettingsDivider()
                CacheRow()
                SettingsDivider()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Text("Local backup", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Albums, tags, favorites, notes and edit settings. Saved privately on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onCreateBackup,
                            modifier = Modifier.weight(1f).testTag("create_backup_button")
                        ) {
                            Icon(Icons.Outlined.Backup, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Back up now")
                        }
                        OutlinedButton(
                            onClick = onRestoreBackup,
                            modifier = Modifier.weight(1f).testTag("restore_backup_button")
                        ) {
                            Icon(Icons.Outlined.Restore, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Restore")
                        }
                    }
                }
            }
        }

        // ---------- Offline intelligence ----------
        item { SettingsSectionLabel("Birthdays") }
        item { SettingsCard { BirthdaySection() } }

        item { SettingsSectionLabel("Offline intelligence") }
        item { SettingsCard { OcrSection() } }
        item {
            SettingsCard {
                SettingSwitchRow(
                    icon = Icons.Outlined.Security,
                    title = "Face grouping",
                    subtitle = "Group people on this device. Nothing leaves your phone.",
                    checked = settings.offlineFaceGroupingEnabled,
                    onCheckedChange = onToggleFaceGrouping,
                    tag = "switch_face_grouping"
                )
                SettingsDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI model pack", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = if (settings.importedAiModelName.isNotEmpty()) {
                                "Installed: ${settings.importedAiModelName}"
                            } else {
                                "Built-in blur and cut-out are active. You can import a .tflite pack."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { modelPackPicker.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Outlined.ModelTraining, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import")
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "Folio Album  •  Offline  •  v1.0",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
    com.example.ui.components.ShareMagicOverlay(previewEvent, settings.shareAnimStyle, settings.reducedMotion) { previewEvent = null }
    previewJourney?.let { j ->
        com.example.ui.components.ShareJourneyOverlay(
            journey = j,
            style = settings.shareAnimStyle,
            onHide = { previewJourney = null },
            onFinished = { previewJourney = null }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    FolioDepthCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    }
}

@Composable
private fun CacheRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(Unit) { bytes = withContext(Dispatchers.IO) { CacheCleaner.sizeBytes(context) } }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Cache", style = MaterialTheme.typography.titleSmall)
            Text(
                if (bytes < 0) "Calculating..." else
                    "${android.text.format.Formatter.formatShortFileSize(context, bytes)} of thumbnails, depth maps and temp files. Cleaned automatically.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        OutlinedButton(
            enabled = bytes != 0L,
            onClick = {
                scope.launch {
                    withContext(Dispatchers.IO) { CacheCleaner.clearAll(context) }
                    bytes = withContext(Dispatchers.IO) { CacheCleaner.sizeBytes(context) }
                }
            },
            modifier = Modifier.testTag("clear_cache_button")
        ) { Text("Clear") }
    }
}

@Composable
private fun SettingsDivider() {
    androidx.compose.material3.HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    )
}

@Composable
private fun ThemeGroupRow(
    label: String,
    modes: List<ThemeMode>,
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    if (modes.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            modes.forEach { mode ->
                val isSel = selected == mode
                Column(
                    modifier = Modifier
                        .width(92.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(mode) }
                        .testTag("theme_${mode.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(mode.previewBackground)
                            .border(
                                width = if (isSel) 2.5.dp else 1.dp,
                                color = if (isSel) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                                shape = RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(mode.previewPrimary)
                        )
                        if (isSel) {
                            Icon(
                                Icons.Outlined.CheckCircle,
                                contentDescription = "Active",
                                tint = if (mode.isDark) Color.White else Color(0xFF111827),
                                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp)
                            )
                        }
                    }
                    Text(
                        mode.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}
