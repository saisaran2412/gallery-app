package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.MediaItemEntity
import com.example.ui.components.FolioDepthCard
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.viewmodel.TimelineSection

@Composable
fun DayDetailScreen(
    title: String,
    items: List<MediaItemEntity>,
    gridColumns: Int,
    onBack: () -> Unit,
    onMediaClick: (MediaItemEntity) -> Unit,
    onOpenSlideshow: () -> Unit,
    onAirShareDay: () -> Unit,
    modifier: Modifier = Modifier,
    onMediaClickWithBounds: ((MediaItemEntity, Rect) -> Unit)? = null
) {
    BackHandler { onBack() }

    val sections = remember(items) {
        if (items.isEmpty()) emptyList() else listOf(TimelineSection("Captured on this day", items))
    }

    Column(
        modifier = modifier
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
                    .testTag("day_detail_back")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${items.size} memory item(s) taken on this day",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (items.isNotEmpty()) {
                IconButton(
                    onClick = onAirShareDay,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("day_detail_airshare")
                ) {
                    Icon(Icons.Outlined.NearMe, contentDescription = "AirShare day memories")
                }
                IconButton(
                    onClick = onOpenSlideshow,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("day_detail_slideshow")
                ) {
                    Icon(Icons.Outlined.Slideshow, contentDescription = "Play day slideshow")
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
                        Text("No photos or videos on this day", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "Media matching this date was moved or deleted.",
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
                onItemClickWithBounds = onMediaClickWithBounds,
                onItemLongPress = {},
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp)
            )
        }
    }
}
