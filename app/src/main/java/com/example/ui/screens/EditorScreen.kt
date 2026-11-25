package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Crop
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.editor.CropAspect
import com.example.editor.EditParams
import com.example.editor.FilterPreset
import com.example.ui.components.FolioRulerSlider
import com.example.ui.components.MagicProcessingOverlay
import com.example.ui.components.FolioSegmentedControl
import com.example.ui.theme.LocalFolioTokens
import com.example.ui.viewmodel.EditorUiState
import kotlin.math.roundToInt

@Composable
fun EditorScreen(
    mediaId: String,
    state: EditorUiState,
    onLoad: (String) -> Unit,
    onBack: () -> Unit,
    onRotate90: () -> Unit,
    onFlip: () -> Unit,
    onSelectAspect: (CropAspect) -> Unit,
    onSelectFilter: (FilterPreset) -> Unit,
    onUpdateParams: ((EditParams) -> EditParams) -> Unit,
    onAddText: (String) -> Unit,
    onAddSticker: (String) -> Unit,
    onClearOverlays: () -> Unit,
    onAddTextBehind: (String) -> Unit = {},
    onToggleTextBehind: (String) -> Unit = {},
    onSetTextScale: (String, Float) -> Unit = { _, _ -> },
    onMoveText: (String, Float, Float) -> Unit = { _, _, _ -> },
    onReset: () -> Unit,
    onSaveNonDestructive: () -> Unit,
    onExportCopy: () -> Unit
) {
    LaunchedEffect(mediaId) { onLoad(mediaId) }
    BackHandler { onBack() }

    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedAdjustTool by remember { mutableIntStateOf(0) }
    var showAddTextDialog by remember { mutableStateOf(false) }
    var addTextBehind by remember { mutableStateOf(false) }
    val tabs = listOf("Adjustment", "Filters", "Ratio & Crop", "Decor")
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(cs.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Warm Studio / Obsidian Editorial Top Bar (inspired by Image 1)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Photo Studio",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onBackground
                )
                Text(
                    "Non-destructive • Local only",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant
                )
            }
            IconButton(onClick = onReset, modifier = Modifier.minimumInteractiveComponentSize()) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = "Reset edits")
            }
            IconButton(
                onClick = onSaveNonDestructive,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("editor_save_button")
            ) {
                Icon(Icons.Outlined.Save, contentDescription = "Save parameters", tint = cs.primary)
            }
            Button(
                onClick = onExportCopy,
                enabled = !state.isExporting,
                shape = RoundedCornerShape(50),
                modifier = Modifier.testTag("editor_export_button")
            ) {
                Icon(Icons.Outlined.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(if (state.isExporting) "..." else "Export")
            }
        }

        // Segmented Control at top just like Image 1
        FolioSegmentedControl(
            options = tabs,
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            testTagPrefix = "editor_tab",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )

        // Framed Photo Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(cs.surfaceVariant.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center
        ) {
            if (state.isLoading) {
                CircularProgressIndicator()
            } else if (state.previewBitmap != null) {
                val depthTabActive = selectedTab == 3 && state.params.depthBlur > 0.02f
                val bmp = state.previewBitmap
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Edited photo preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .pointerInput(depthTabActive, bmp) {
                            if (!depthTabActive) return@pointerInput
                            detectTapGestures { off ->
                                val bw = bmp.width.toFloat()
                                val bh = bmp.height.toFloat()
                                val sc = minOf(size.width / bw, size.height / bh)
                                val dw = bw * sc
                                val dh = bh * sc
                                val left = (size.width - dw) / 2f
                                val top = (size.height - dh) / 2f
                                val fx = ((off.x - left) / dw).coerceIn(0f, 1f)
                                val fy = ((off.y - top) / dh).coerceIn(0f, 1f)
                                onUpdateParams { it.copy(depthFocusX = fx, depthFocusY = fy) }
                            }
                        }
                        .drawWithContent {
                            drawContent()
                            if (depthTabActive) {
                                val bw = bmp.width.toFloat()
                                val bh = bmp.height.toFloat()
                                val sc = minOf(size.width / bw, size.height / bh)
                                val dw = bw * sc
                                val dh = bh * sc
                                val c = Offset(
                                    (size.width - dw) / 2f + state.params.depthFocusX * dw,
                                    (size.height - dh) / 2f + state.params.depthFocusY * dh
                                )
                                drawCircle(Color.White, radius = 34f, center = c, style = Stroke(width = 3f))
                                drawCircle(Color.White.copy(alpha = 0.85f), radius = 4f, center = c)
                            }
                        }
                )
            }
            MagicProcessingOverlay(
                visible = state.isDepthComputing || state.isExporting,
                labels = if (state.isExporting) {
                    listOf("Rendering in full quality", "Painting the bokeh", "Saving your photo")
                } else {
                    listOf("Reading the scene", "Finding your subject", "Mapping depth", "Painting the bokeh")
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Tactile Bottom Tools Sheet
        Surface(
            shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
            color = cs.surface,
            border = BorderStroke(1.dp, tokens.borderColor),
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                when (selectedTab) {
                    0 -> {
                        val adjustLabels = listOf(
                            Triple("Brightness", Icons.Outlined.Brightness6, 0),
                            Triple("Contrast", Icons.Outlined.Contrast, 1),
                            Triple("Saturation", Icons.Outlined.Palette, 2),
                            Triple("Warmth", Icons.Outlined.Thermostat, 3)
                        )
                        when (selectedAdjustTool) {
                            0 -> FolioRulerSlider(
                                label = "Brightness",
                                value = state.params.brightness,
                                valueRange = -0.6f..0.6f,
                                onValueChange = { v -> onUpdateParams { it.copy(brightness = v) } },
                                formatValue = { "${(it * 100f).roundToInt()}%" }
                            )
                            1 -> FolioRulerSlider(
                                label = "Contrast",
                                value = state.params.contrast,
                                valueRange = 0.6f..1.6f,
                                onValueChange = { v -> onUpdateParams { it.copy(contrast = v) } },
                                formatValue = { "${((it - 1f) * 100f).roundToInt()}%" }
                            )
                            2 -> FolioRulerSlider(
                                label = "Saturation",
                                value = state.params.saturation,
                                valueRange = 0f..2f,
                                onValueChange = { v -> onUpdateParams { it.copy(saturation = v) } },
                                formatValue = { "${((it - 1f) * 100f).roundToInt()}%" }
                            )
                            else -> FolioRulerSlider(
                                label = "Warmth",
                                value = state.params.temperature,
                                valueRange = -1f..1f,
                                onValueChange = { v -> onUpdateParams { it.copy(temperature = v) } },
                                formatValue = { "${(it * 100f).roundToInt()}%" }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            adjustLabels.forEach { (title, icon, idx) ->
                                val active = selectedAdjustTool == idx
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    Surface(
                                        onClick = { selectedAdjustTool = idx },
                                        shape = CircleShape,
                                        color = if (active) cs.primary else cs.surfaceVariant,
                                        modifier = Modifier
                                            .size(46.dp)
                                            .minimumInteractiveComponentSize()
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = title,
                                                tint = if (active) cs.onPrimary else cs.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (active) cs.primary else cs.onSurfaceVariant,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterPreset.entries.forEach { preset ->
                                FilterChip(
                                    selected = state.params.filterPreset == preset,
                                    onClick = { onSelectFilter(preset) },
                                    label = { Text(preset.label) }
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        FolioRulerSlider(
                            label = "Vignette",
                            value = state.params.vignette,
                            valueRange = 0f..1f,
                            onValueChange = { v -> onUpdateParams { it.copy(vignette = v) } }
                        )
                    }

                    2 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = onRotate90,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.RotateRight, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Rotate 90°")
                            }
                            OutlinedButton(
                                onClick = onFlip,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.Flip, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Flip H")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CropAspect.entries.forEach { aspect ->
                                FilterChip(
                                    selected = state.params.cropAspect == aspect,
                                    onClick = { onSelectAspect(aspect) },
                                    leadingIcon = { Icon(Icons.Outlined.Crop, null, modifier = Modifier.size(16.dp)) },
                                    label = { Text(aspect.label) }
                                )
                            }
                        }
                    }

                    3 -> {
                        FolioRulerSlider(
                            label = "Cinematic Depth Blur (tap photo to focus)",
                            value = state.params.depthBlur,
                            valueRange = 0f..1f,
                            onValueChange = { v -> onUpdateParams { it.copy(depthBlur = v) } }
                        )
                        Spacer(Modifier.height(4.dp))
                        AssistChip(
                            onClick = {
                                onUpdateParams {
                                    it.copy(
                                        depthBlur = 0.8f,
                                        vignette = maxOf(it.vignette, 0.35f),
                                        contrast = maxOf(it.contrast, 1.08f),
                                        saturation = maxOf(it.saturation, 1.06f)
                                    )
                                }
                            },
                            label = { Text("Cinematic look") },
                            leadingIcon = { Icon(Icons.Outlined.AutoFixHigh, null, modifier = Modifier.size(16.dp)) }
                        )
                        Spacer(Modifier.height(8.dp))
                        FolioRulerSlider(
                            label = "Portrait Blur (Offline)",
                            value = state.params.portraitBlur,
                            valueRange = 0f..1f,
                            onValueChange = { v -> onUpdateParams { it.copy(portraitBlur = v) } }
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoFixHigh, null, tint = cs.primary)
                                Spacer(Modifier.width(8.dp))
                                Text("Subject Spotlight Cut-out", style = MaterialTheme.typography.bodyMedium)
                            }
                            Switch(
                                checked = state.params.cutoutMask,
                                onCheckedChange = { checked -> onUpdateParams { it.copy(cutoutMask = checked) } }
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(
                                onClick = { addTextBehind = false; showAddTextDialog = true },
                                leadingIcon = { Icon(Icons.Outlined.TextFields, null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Add Text") }
                            )
                            AssistChip(
                                onClick = { addTextBehind = true; showAddTextDialog = true },
                                leadingIcon = { Icon(Icons.Outlined.Layers, null, modifier = Modifier.size(16.dp)) },
                                label = { Text("Text depth") }
                            )
                            listOf("✨", "❤️", "🌿", "☀️", "📷", "🎉").forEach { emoji ->
                                AssistChip(
                                    onClick = { onAddSticker(emoji) },
                                    label = { Text(emoji) }
                                )
                            }
                            if (state.params.textOverlays.isNotEmpty() || state.params.stickerOverlays.isNotEmpty()) {
                                TextButton(onClick = onClearOverlays) { Text("Clear Decor") }
                            }
                        }
                        state.params.textOverlays.forEach { t ->
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    t.text,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("Behind subject", style = MaterialTheme.typography.labelSmall)
                                Spacer(Modifier.width(6.dp))
                                Switch(checked = t.behind, onCheckedChange = { onToggleTextBehind(t.id) })
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Size", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                                androidx.compose.material3.Slider(
                                    value = t.scale,
                                    onValueChange = { onSetTextScale(t.id, it) },
                                    valueRange = 0.5f..6f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Up/Down", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                                androidx.compose.material3.Slider(
                                    value = t.yFraction,
                                    onValueChange = { onMoveText(t.id, t.xFraction, it) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Left/Right", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(34.dp))
                                androidx.compose.material3.Slider(
                                    value = t.xFraction,
                                    onValueChange = { onMoveText(t.id, it, t.yFraction) },
                                    valueRange = 0f..1f,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddTextDialog) {
        var textInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddTextDialog = false },
            title = { Text(if (addTextBehind) "Text behind subject" else "Add Photo Caption") },
            text = {
                OutlinedTextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    label = { Text("Caption text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (addTextBehind) onAddTextBehind(textInput) else onAddText(textInput)
                        showAddTextDialog = false
                    },
                    enabled = textInput.isNotBlank()
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddTextDialog = false }) { Text("Cancel") }
            }
        )
    }
}
