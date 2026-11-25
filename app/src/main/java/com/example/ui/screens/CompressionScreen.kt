package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.util.Formatters
import com.example.ui.components.FolioDepthCard
import com.example.ui.viewmodel.CompressionUiState
import kotlin.math.roundToInt

@Composable
fun CompressionScreen(
    mediaId: String,
    state: CompressionUiState,
    onLoad: (String) -> Unit,
    onBack: () -> Unit,
    onQualityChange: (Int) -> Unit,
    onScaleChange: (Float) -> Unit,
    onExportCompressed: () -> Unit
) {
    LaunchedEffect(mediaId) { onLoad(mediaId) }
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                Text("Compress & Export Copy", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Original file will never be overwritten",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (state.isComputing) {
                CircularProgressIndicator()
            } else if (state.previewBitmap != null) {
                Image(
                    bitmap = state.previewBitmap.asImageBitmap(),
                    contentDescription = "Compressed preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        FolioDepthCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Original", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Formatters.formatFileSize(state.originalBytes), style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Compressed Copy", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text(Formatters.formatFileSize(state.estimatedBytes), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Text("JPEG Quality: ${state.quality}%", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = state.quality.toFloat(),
                    onValueChange = { onQualityChange(it.roundToInt()) },
                    valueRange = 15f..100f
                )

                Text("Resolution Scale", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1f to "100%", 0.75f to "75%", 0.5f to "50%", 0.25f to "25%").forEach { (scale, label) ->
                        FilterChip(
                            selected = kotlin.math.abs(state.scaleFraction - scale) < 0.05f,
                            onClick = { onScaleChange(scale) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onExportCompressed,
                    enabled = !state.isExporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("export_compressed_button")
                ) {
                    Text(if (state.isExporting) "Saving Compressed Copy..." else "Export Compressed Copy")
                }
            }
        }
    }
}
