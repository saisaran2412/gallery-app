package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.navigation.FolioRoutes
import com.example.ui.theme.LocalFolioTokens

data class TopNavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

val PrimaryNavDestinations = listOf(
    TopNavDestination(
        route = FolioRoutes.GALLERY,
        label = "Gallery",
        selectedIcon = Icons.Filled.PhotoLibrary,
        unselectedIcon = Icons.Outlined.PhotoLibrary,
        testTag = "nav_gallery"
    ),
    TopNavDestination(
        route = FolioRoutes.ALBUMS,
        label = "Albums",
        selectedIcon = Icons.Filled.PhotoAlbum,
        unselectedIcon = Icons.Outlined.PhotoAlbum,
        testTag = "nav_albums"
    ),
    TopNavDestination(
        route = FolioRoutes.SEARCH,
        label = "Search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "nav_search"
    ),
    TopNavDestination(
        route = FolioRoutes.FAVORITES,
        label = "Favorites",
        selectedIcon = Icons.Filled.Favorite,
        unselectedIcon = Icons.Outlined.FavoriteBorder,
        testTag = "nav_favorites"
    ),
    TopNavDestination(
        route = FolioRoutes.SETTINGS,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        testTag = "nav_settings"
    )
)

/**
 * Centered floating Apple Liquid Glass navigation capsule with spring micro-bounce.
 */
@Composable
fun FolioBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val tokens = LocalFolioTokens.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(34.dp),
            color = tokens.dockContainerColor,
            tonalElevation = 8.dp,
            shadowElevation = if (tokens.isLiquidGlass) 16.dp else 12.dp,
            border = if (tokens.isLiquidGlass) null else BorderStroke(1.dp, tokens.borderColor),
            modifier = if (tokens.isLiquidGlass) {
                Modifier.liquidGlassRim(
                    cornerRadius = 34.dp,
                    specularTopLeft = tokens.specularRimColor,
                    specularBottomRight = tokens.borderColor.copy(alpha = 0.55f),
                    sheenColor = tokens.glassHighlightColor
                )
            } else Modifier
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryNavDestinations.forEach { dest ->
                    val isSelected = currentRoute == dest.route
                    val pillColor by animateColorAsState(
                        targetValue = if (isSelected) {
                            cs.primaryContainer.copy(alpha = if (tokens.isLiquidGlass) 0.85f else 1f)
                        } else Color.Transparent,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dock_pill"
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (isSelected) cs.primary else cs.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "dock_tint"
                    )
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 0.96f,
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                        label = "dock_scale"
                    )

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(pillColor)
                            .semantics {
                                selected = isSelected
                                contentDescription = dest.label
                            }
                            .clickable { onNavigate(dest.route) }
                            .testTag(dest.testTag),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isSelected) dest.selectedIcon else dest.unselectedIcon,
                            contentDescription = dest.label,
                            tint = iconTint,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FolioNavigationRail(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val tokens = LocalFolioTokens.current
    NavigationRail(
        containerColor = if (tokens.isLiquidGlass) tokens.dockContainerColor else MaterialTheme.colorScheme.surface
    ) {
        Spacer(Modifier.height(12.dp))
        PrimaryNavDestinations.forEach { dest ->
            val selected = currentRoute == dest.route
            NavigationRailItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = {
                    Icon(
                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                        contentDescription = dest.label
                    )
                },
                label = { Text(dest.label, maxLines = 1) },
                modifier = Modifier.testTag(dest.testTag)
            )
        }
    }
}
