package com.example

import com.example.core.util.Formatters
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.data.scanner.MediaHashingEngine
import com.example.editor.CropAspect
import com.example.editor.EditParams
import com.example.editor.FilterPreset
import com.example.editor.StickerOverlayItem
import com.example.editor.TextOverlayItem
import com.example.security.VaultCryptoManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FolioCoreUnitTest {

    @Test
    fun editParams_roundTripsJsonFaithfully() {
        val original = EditParams(
            rotationDegrees = 90,
            flipHorizontal = true,
            cropAspect = CropAspect.SQUARE,
            brightness = 0.2f,
            contrast = 1.25f,
            saturation = 1.4f,
            temperature = -0.3f,
            vignette = 0.5f,
            portraitBlur = 0.6f,
            cutoutMask = true,
            filterPreset = FilterPreset.WARM_FILM,
            textOverlays = listOf(TextOverlayItem("t1", "Sunset", 0.4f, 0.7f)),
            stickerOverlays = listOf(StickerOverlayItem("s1", "✨", 0.5f, 0.3f))
        )

        val restored = EditParams.fromJson(original.toJson())
        assertEquals(original.rotationDegrees, restored.rotationDegrees)
        assertEquals(original.flipHorizontal, restored.flipHorizontal)
        assertEquals(original.cropAspect, restored.cropAspect)
        assertEquals(original.filterPreset, restored.filterPreset)
        assertEquals(original.cutoutMask, restored.cutoutMask)
        assertEquals(1, restored.textOverlays.size)
        assertEquals("Sunset", restored.textOverlays.first().text)
        assertEquals(1, restored.stickerOverlays.size)
    }

    @Test
    fun duplicateFinder_detectsExactAndSimilarGroupsWithoutFalsePositives() {
        val base = MediaItemEntity(
            id = "img_1",
            mediaStoreId = 1L,
            uriString = "content://media/1",
            mediaType = MediaType.PHOTO,
            displayName = "one.jpg",
            mimeType = "image/jpeg",
            dateTakenMillis = 1000L,
            dateAddedMillis = 1000L,
            dateModifiedMillis = 1000L,
            width = 1080,
            height = 1080,
            sizeBytes = 200_000L,
            sha256Hash = "abc123",
            dHash = "00000000000000ff"
        )
        val exactDup = base.copy(id = "img_2", displayName = "one_copy.jpg", sizeBytes = 200_000L)
        val similarPhoto = base.copy(
            id = "img_3",
            displayName = "similar.jpg",
            sha256Hash = "def456",
            dHash = "00000000000000fe"
        )
        val differentPhoto = base.copy(
            id = "img_4",
            displayName = "different.jpg",
            sha256Hash = "xyz789",
            dHash = "ffffffff00000000"
        )
        val anotherSimilar = similarPhoto.copy(
            id = "img_5",
            displayName = "similar2.jpg",
            sha256Hash = "ghi000",
            dHash = "00000000000000fc"
        )

        val groups = MediaHashingEngine.findDuplicateGroups(
            listOf(base, exactDup, similarPhoto, anotherSimilar, differentPhoto)
        )
        assertEquals(2, groups.size)
        assertTrue(groups.any { it.isExact && it.items.size == 2 })
        assertTrue(groups.any { !it.isExact && it.items.size == 2 })
    }

    @Test
    fun vaultPinHashing_verifiesCorrectPinAndRejectsWrongPin() {
        val salt = VaultCryptoManager.generateSaltBase64()
        val hash = VaultCryptoManager.hashPin("2580", salt)
        assertTrue(VaultCryptoManager.verifyPin("2580", salt, hash))
        assertFalse(VaultCryptoManager.verifyPin("1234", salt, hash))
    }

    @Test
    fun formatters_formatDurationsAndSizes() {
        assertEquals("00:32", Formatters.formatDuration(32_000L))
        assertEquals("1:02:05", Formatters.formatDuration(3_725_000L))
        assertEquals("500 B", Formatters.formatFileSize(500L))
        assertEquals("2.5 MB", Formatters.formatFileSize(2_621_440L))
    }
}
