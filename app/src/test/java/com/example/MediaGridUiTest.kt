package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.ui.components.MixedMediaTimelineGrid
import com.example.ui.screens.AlbumsScreen
import com.example.ui.theme.FolioAlbumTheme
import com.example.ui.viewmodel.TimelineSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaGridUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun mixedMediaLazyGrid_displaysPhotoAndVideoThumbnailsAndHandlesInteractions() {
        val photo = MediaItemEntity(
            id = "img_10",
            mediaStoreId = 10L,
            uriString = "content://media/external/images/media/10",
            mediaType = MediaType.PHOTO,
            displayName = "beach.jpg",
            mimeType = "image/jpeg",
            dateTakenMillis = 1_700_000_000_000L,
            dateAddedMillis = 1_700_000_000_000L,
            dateModifiedMillis = 1_700_000_000_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 1_200_000L,
            isFavorite = true
        )
        val video = MediaItemEntity(
            id = "vid_20",
            mediaStoreId = 20L,
            uriString = "content://media/external/video/media/20",
            mediaType = MediaType.VIDEO,
            displayName = "clip.mp4",
            mimeType = "video/mp4",
            dateTakenMillis = 1_700_000_000_000L,
            dateAddedMillis = 1_700_000_000_000L,
            dateModifiedMillis = 1_700_000_000_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 8_400_000L,
            durationMillis = 32_000L
        )

        var clickedId = ""
        var longPressedId = ""

        composeRule.setContent {
            FolioAlbumTheme {
                MixedMediaTimelineGrid(
                    sections = listOf(TimelineSection("Today", listOf(photo, video))),
                    columns = 3,
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    onItemClick = { clickedId = it.id },
                    onItemLongPress = { longPressedId = it.id }
                )
            }
        }

        composeRule.onNodeWithTag("mixed_media_lazy_grid").assertIsDisplayed()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithTag("media_tile_img_10").assertIsDisplayed()
        composeRule.onNodeWithTag("media_tile_vid_20").assertIsDisplayed()
        composeRule.onNodeWithText("00:32").assertIsDisplayed()

        composeRule.onNodeWithTag("media_tile_img_10").performClick()
        assertEquals("img_10", clickedId)

        composeRule.onNodeWithTag("media_tile_vid_20").performTouchInput { longClick() }
        assertEquals("vid_20", longPressedId)
    }

    @Test
    fun albumsScreen_displaysUtilitiesSectionAtBottomAndTriggersNavigation() {
        var openedPeople = false
        var openedVault = false
        var openedDuplicates = false
        var openedTrash = false

        composeRule.setContent {
            FolioAlbumTheme {
                AlbumsScreen(
                    virtualAlbums = emptyList(),
                    deviceFolders = emptyList(),
                    peopleCount = 3,
                    trashCount = 5,
                    onOpenVirtualAlbum = {},
                    onOpenDeviceFolder = { _, _ -> },
                    onCreateAlbum = { _, _ -> },
                    onRenameAlbum = { _, _ -> },
                    onDeleteAlbum = {},
                    onOpenPeople = { openedPeople = true },
                    onOpenVault = { openedVault = true },
                    onOpenDuplicates = { openedDuplicates = true },
                    onOpenTrash = { openedTrash = true }
                )
            }
        }

        composeRule.onNodeWithText("People & Utilities").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("shortcut_people").performScrollTo().performClick()
        composeRule.onNodeWithTag("shortcut_vault").performScrollTo().performClick()
        composeRule.onNodeWithTag("shortcut_duplicates").performScrollTo().performClick()
        composeRule.onNodeWithTag("shortcut_trash").performScrollTo().performClick()

        assertTrue(openedPeople)
        assertTrue(openedVault)
        assertTrue(openedDuplicates)
        assertTrue(openedTrash)
    }
}
