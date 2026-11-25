package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.storage.MediaFilter
import com.example.core.storage.SortOrder
import com.example.data.local.MediaItemEntity
import com.example.data.local.TagEntity
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.viewmodel.SearchState
import com.example.ui.viewmodel.TimelineSection

@Composable
fun SearchScreen(
    searchState: SearchState,
    results: List<MediaItemEntity>,
    allTags: List<TagEntity>,
    gridColumns: Int,
    onQueryChange: (String) -> Unit,
    onFilterChange: (MediaFilter) -> Unit,
    onSortChange: (SortOrder) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSelectTag: (Long?) -> Unit,
    onMediaClick: (MediaItemEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val sections = remember(results) {
        if (results.isEmpty()) emptyList() else listOf(TimelineSection("Matches (${results.size})", results))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Text(
                text = "Search & Filter",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = searchState.query,
                onValueChange = onQueryChange,
                placeholder = { Text("Filename, folder, note, tag, or format...") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchState.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_query_input")
            )
        }

        // Type + Favorites filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MediaFilter.entries.forEach { filter ->
                FilterChip(
                    selected = searchState.filter == filter,
                    onClick = { onFilterChange(filter) },
                    label = { Text(filter.label) }
                )
            }
            FilterChip(
                selected = searchState.favoritesOnly,
                onClick = onToggleFavoritesOnly,
                leadingIcon = { Icon(Icons.Outlined.FavoriteBorder, null, modifier = Modifier.size(16.dp)) },
                label = { Text("Favorites") }
            )
        }

        // Sort + Tags row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SortOrder.entries.forEach { order ->
                FilterChip(
                    selected = searchState.sortOrder == order,
                    onClick = { onSortChange(order) },
                    label = { Text(order.label) }
                )
            }
            allTags.forEach { tag ->
                FilterChip(
                    selected = searchState.selectedTagId == tag.id,
                    onClick = {
                        onSelectTag(if (searchState.selectedTagId == tag.id) null else tag.id)
                    },
                    label = { Text("#${tag.name}") }
                )
            }
        }

        if (results.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No matching photos or videos offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
