package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaItemEntity
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.viewmodel.TimelineSection

@Composable
fun FavoritesScreen(
    favorites: List<MediaItemEntity>,
    gridColumns: Int,
    onMediaClick: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = remember(favorites) {
        if (favorites.isEmpty()) emptyList() else listOf(TimelineSection("Starred Memories", favorites))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Favorites",
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                text = "${favorites.size} starred photos & videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (favorites.isEmpty()) {
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
                            imageVector = Icons.Outlined.FavoriteBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(44.dp)
                        )
                        Text("No Favorites Yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Tap the heart in the viewer or multi-select items in your gallery to bookmark them here.",
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
                contentPadding = PaddingValues(bottom = 96.dp)
            )
        }
    }
}
