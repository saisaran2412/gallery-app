package com.example

import android.Manifest
import android.app.Application
import android.os.Build
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.core.util.Formatters
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.share.AirShareFileSanitizer
import com.example.share.AirShareMode
import com.example.share.AirSharePeer
import com.example.share.AirSharePendingAuth
import com.example.share.AirShareSessionState
import com.example.share.AirShareTransferItem
import com.example.share.AirShareTransport
import com.example.share.AirShareTransportProtocol
import com.example.share.NearbyService
import com.example.share.PlayServicesCompatibility
import com.example.share.ShareTargetsHelper
import com.example.ui.components.FolioAtmosphereBackground
import com.example.ui.components.FolioBottomBar
import com.example.ui.components.MediaCalendarGrid
import com.example.ui.navigation.FolioRoutes
import com.example.ui.screens.AirShareScreen
import com.example.ui.screens.AlbumsScreen
import com.example.ui.screens.DayDetailScreen
import com.example.ui.screens.MediaViewerScreen
import com.example.ui.theme.FolioAlbumTheme
import com.example.ui.theme.LocalFolioTokens
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.AirShareUiState
import com.example.ui.viewmodel.AirShareViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

private class FakeAirShareTransport(
    private val compatibility: PlayServicesCompatibility = PlayServicesCompatibility.AVAILABLE
) : AirShareTransport {
    override val protocol = AirShareTransportProtocol.ANDROID_NEARBY

    private val _sessionState = MutableStateFlow<AirShareSessionState>(AirShareSessionState.Idle)
    override val sessionState: StateFlow<AirShareSessionState> = _sessionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<AirSharePeer>>(emptyList())
    override val discoveredPeers: StateFlow<List<AirSharePeer>> = _discoveredPeers.asStateFlow()

    private val _transfers = MutableStateFlow<List<AirShareTransferItem>>(emptyList())
    override val transfers: StateFlow<List<AirShareTransferItem>> = _transfers.asStateFlow()

    var sentItemIds: List<String> = emptyList()
    var lastAcceptedEndpoint: String? = null
    var lastRejectedEndpoint: String? = null

    override fun checkCompatibility(): PlayServicesCompatibility = compatibility

    override fun startAdvertising(localDeviceName: String) {
        if (!compatibility.isSupported) {
            _sessionState.value = AirShareSessionState.PlayServicesUnavailable(compatibility)
            return
        }
        _sessionState.value = AirShareSessionState.ReceivingReady(localDeviceName)
    }

    override fun startDiscovery(localDeviceName: String) {
        if (!compatibility.isSupported) {
            _sessionState.value = AirShareSessionState.PlayServicesUnavailable(compatibility)
            return
        }
        _sessionState.value = AirShareSessionState.Scanning(localDeviceName)
        _discoveredPeers.value = listOf(AirSharePeer("ep_1", "Pixel 9 Receiver"))
    }

    override fun requestConnection(endpointId: String, localDeviceName: String) {
        _sessionState.value = AirShareSessionState.AwaitingConfirmation(
            AirSharePendingAuth(endpointId, "Pixel 9 Receiver", "4821", isIncomingConnection = false)
        )
    }

    override fun acceptConnection(endpointId: String) {
        lastAcceptedEndpoint = endpointId
        _sessionState.value = AirShareSessionState.Connected(AirSharePeer(endpointId, "Pixel 9 Receiver", isConnected = true))
    }

    override fun rejectConnection(endpointId: String) {
        lastRejectedEndpoint = endpointId
        _sessionState.value = AirShareSessionState.Idle
    }

    override fun sendMediaItems(endpointId: String, items: List<MediaItemEntity>) {
        sentItemIds = items.map { it.id }
    }

    override fun disconnect(endpointId: String) {
        _sessionState.value = AirShareSessionState.Idle
    }

    override fun stopAll() {
        _discoveredPeers.value = emptyList()
        _sessionState.value = AirShareSessionState.Idle
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AirShareAndDesignTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun samplePhoto(id: String = "p1") = MediaItemEntity(
        id = id,
        mediaStoreId = 1L,
        uriString = "content://media/external/images/media/1",
        mediaType = MediaType.PHOTO,
        displayName = "sunset.jpg",
        mimeType = "image/jpeg",
        dateTakenMillis = 1_735_382_400_000L, // Dec 28, 2024
        dateAddedMillis = 1_735_382_400_000L,
        dateModifiedMillis = 1_735_382_400_000L,
        width = 1920,
        height = 1080,
        sizeBytes = 2_400_000L
    )

    @Test
    fun airShareFileSanitizer_stripsTraversalAndUnsafeChars() {
        assertEquals("secret.jpg", AirShareFileSanitizer.sanitizeFileName("../../etc/secret.jpg"))
        assertEquals("my_photo_1.png", AirShareFileSanitizer.sanitizeFileName("folder\\my photo@1.png"))
        assertTrue(AirShareFileSanitizer.sanitizeFileName("   ", "mp4").endsWith(".mp4"))
    }

    @Test
    fun airSharePermissions_areVersionScopedCorrectly() {
        val api33 = AirShareViewModel.requiredNearbyPermissions(Build.VERSION_CODES.TIRAMISU).toSet()
        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in api33)
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in api33)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in api33)

        val api31 = AirShareViewModel.requiredNearbyPermissions(Build.VERSION_CODES.S).toSet()
        assertTrue(Manifest.permission.BLUETOOTH_SCAN in api31)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in api31)

        val api30 = AirShareViewModel.requiredNearbyPermissions(Build.VERSION_CODES.R).toSet()
        assertTrue(Manifest.permission.BLUETOOTH in api30)
        assertTrue(Manifest.permission.ACCESS_FINE_LOCATION in api30)
    }

    @Test
    fun airShareViewModel_handlesDiscoveryPairingAndSendingViaModularTransport() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val transport = FakeAirShareTransport()
        val vm = AirShareViewModel(app, transport)

        val item = samplePhoto("img_99")
        vm.setQueuedMedia(listOf(item))
        vm.startScanning()

        assertTrue(transport.sessionState.value is AirShareSessionState.Scanning)
        assertEquals(1, transport.discoveredPeers.value.size)

        vm.connectToPeer("ep_1")
        assertTrue(transport.sessionState.value is AirShareSessionState.AwaitingConfirmation)

        vm.acceptPendingConnection("ep_1")
        assertEquals("ep_1", transport.lastAcceptedEndpoint)
        assertTrue(transport.sessionState.value is AirShareSessionState.Connected)

        vm.sendQueuedToPeer("ep_1")
        assertEquals(listOf("img_99"), transport.sentItemIds)
    }

    @Test
    fun airShareScreen_displaysCompatibilityBannerWhenPlayServicesUnavailable() {
        val state = AirShareUiState(
            mode = AirShareMode.SEND,
            compatibility = PlayServicesCompatibility.SERVICE_MISSING,
            sessionState = AirShareSessionState.PlayServicesUnavailable(PlayServicesCompatibility.SERVICE_MISSING),
            hasPermissions = true
        )

        composeRule.setContent {
            FolioAlbumTheme {
                AirShareScreen(
                    uiState = state,
                    galleryMedia = emptyList(),
                    reducedMotion = true,
                    onBack = {},
                    onSelectMode = {},
                    onPermissionsResult = {},
                    onStartScanning = {},
                    onStartReceiving = {},
                    onConnectPeer = {},
                    onAcceptConnection = {},
                    onRejectConnection = {},
                    onSendQueued = {},
                    onDisconnect = {},
                    onStopSession = {},
                    onAddGalleryMedia = {},
                    onImportPickedUris = {},
                    onRemoveQueuedItem = {}
                )
            }
        }

        composeRule.onNodeWithTag("airshare_compat_banner").assertIsDisplayed()
        composeRule.onNodeWithText("Google Play Services Missing").assertIsDisplayed()
        composeRule.onNodeWithTag("airshare_lazy_column")
            .performScrollToNode(hasTestTag("airshare_architecture_card"))
        composeRule.onNodeWithTag("airshare_architecture_card").performClick()
        composeRule.onNodeWithText("100% Free & Modular Offline Sharing").assertIsDisplayed()
    }

    @Test
    fun mediaCalendarGrid_rendersMonthCountBadgeAndTriggersDaySelection() {
        var selectedCount = 0
        var selectedKey = 0
        composeRule.setContent {
            FolioAlbumTheme {
                MediaCalendarGrid(
                    mediaItems = listOf(samplePhoto("cal_1"), samplePhoto("cal_2")),
                    onDayClick = { dayKey, _, items ->
                        selectedKey = dayKey
                        selectedCount = items.size
                    }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("media_calendar_grid").assertIsDisplayed()
        composeRule.onNodeWithTag("calendar_count_cal_202411_28", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("28").performClick()
        assertEquals(2, selectedCount)
        assertEquals(20241228, selectedKey)
    }

    @Test
    fun albumsScreen_triggersAirShareShortcutAtBottom() {
        var airShareOpened = false
        composeRule.setContent {
            FolioAlbumTheme {
                AlbumsScreen(
                    virtualAlbums = emptyList(),
                    deviceFolders = emptyList(),
                    peopleCount = 2,
                    trashCount = 1,
                    onOpenVirtualAlbum = {},
                    onOpenDeviceFolder = { _, _ -> },
                    onCreateAlbum = { _, _ -> },
                    onRenameAlbum = { _, _ -> },
                    onDeleteAlbum = {},
                    onOpenPeople = {},
                    onOpenVault = {},
                    onOpenDuplicates = {},
                    onOpenTrash = {},
                    onOpenAirShare = { airShareOpened = true }
                )
            }
        }

        composeRule.onNodeWithTag("shortcut_airshare").performScrollTo().performClick()
        assertTrue(airShareOpened)
    }

    @Test
    fun nearbyService_exposesDiscoveryAndCompatibilityState() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val service = NearbyService(app)
        assertEquals("com.aistudio.folioalbum.airshare", NearbyService.SERVICE_ID)
        assertTrue(service.discoveredPeers.value.isEmpty())
        // In Robolectric, Play Services check completes safely without crashing
        val compat = service.checkCompatibility()
        assertTrue(compat in PlayServicesCompatibility.entries)
        service.stopDiscovery()
        assertTrue(service.sessionState.value is AirShareSessionState.Idle)
    }

    @Test
    fun dayDetailScreen_displaysOnlySelectedDayItems() {
        var clickedId: String? = null
        var sharedDay = false
        val items = listOf(
            samplePhoto("day_p1"),
            samplePhoto("day_p2").copy(displayName = "beach.jpg")
        )
        composeRule.setContent {
            FolioAlbumTheme {
                DayDetailScreen(
                    title = "28 December 2024",
                    items = items,
                    gridColumns = 3,
                    onBack = {},
                    onMediaClick = { clickedId = it.id },
                    onOpenSlideshow = {},
                    onAirShareDay = { sharedDay = true }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("28 December 2024").assertIsDisplayed()
        composeRule.onNodeWithText("2 memory item(s) taken on this day").assertIsDisplayed()
        composeRule.onNodeWithTag("media_tile_day_p2").performClick()
        assertEquals("day_p2", clickedId)
        composeRule.onNodeWithTag("day_detail_airshare").performClick()
        assertTrue(sharedDay)
    }

    @Test
    fun mediaViewerScreen_displaysCounterAndControls() {
        val items = listOf(
            samplePhoto("v1"),
            samplePhoto("v2").copy(displayName = "portrait.jpg")
        )
        composeRule.setContent {
            FolioAlbumTheme {
                MediaViewerScreen(
                    items = items,
                    initialMediaId = "v1",
                    mediaTagMap = emptyMap(),
                    exifMap = emptyMap(),
                    showLocationMetadata = true,
                    onBack = {},
                    onRequestExif = {},
                    onToggleFavorite = {},
                    onOpenEditor = {},
                    onOpenCompress = {},
                    onOpenSlideshow = {},
                    onShare = {},
                    onMoveToVault = {},
                    onDelete = {},
                    onUpdateNote = { _, _ -> },
                    onAddTag = { _, _ -> },
                    onRemoveTag = { _, _ -> },
                    onSavePlaybackPosition = { _, _ -> },
                    onOpenExternalPlayer = {}
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("sunset.jpg").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer_share").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer_airshare").assertIsDisplayed()
    }

    @Test
    fun liquidGlassTheme_providesOpticalTokensAndRendersNavChrome() {
        var isLiquidGlass = false
        composeRule.setContent {
            FolioAlbumTheme(themeMode = ThemeMode.LIQUID_GLASS) {
                isLiquidGlass = LocalFolioTokens.current.isLiquidGlass
                FolioAtmosphereBackground {
                    FolioBottomBar(
                        currentRoute = FolioRoutes.GALLERY,
                        onNavigate = {}
                    )
                }
            }
        }

        composeRule.waitForIdle()
        assertTrue(isLiquidGlass)
        composeRule.onNodeWithTag("nav_gallery").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_albums").assertIsDisplayed()
    }

    @Test
    fun shareTargetsHelper_includesCopyTargetAndRecordsMruSafely() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        ShareTargetsHelper.recordShareUsage(app, "com.whatsapp")
        val targets = ShareTargetsHelper.resolveQuickShareTargets(app, "image/jpeg")
        assertTrue(targets.first().isAirShare)
        assertTrue(targets.any { it.isCopyClipboard && it.label == "Copy" })
        assertTrue(targets.last().isSystemShare)
    }

    @Test
    fun formattersSectionHeaderResolver_resolvesTodayYesterdayAndDates() {
        val resolver = Formatters.SectionHeaderResolver()
        val now = Calendar.getInstance()
        assertEquals("Today", resolver.resolve(now.timeInMillis))

        val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        if (yesterday.get(Calendar.YEAR) == now.get(Calendar.YEAR)) {
            assertEquals("Yesterday", resolver.resolve(yesterday.timeInMillis))
        }

        val older = Calendar.getInstance().apply { set(2023, Calendar.JULY, 14, 10, 0) }
        assertTrue(resolver.resolve(older.timeInMillis).contains("2023"))
    }

    @Test
    fun mediaViewerScreen_supportsHeroOriginRectAndThreeArgShareCallback() {
        val items = listOf(samplePhoto("hero_1"))
        composeRule.setContent {
            FolioAlbumTheme(themeMode = ThemeMode.LIQUID_GLASS) {
                MediaViewerScreen(
                    items = items,
                    initialMediaId = "hero_1",
                    mediaTagMap = emptyMap(),
                    exifMap = emptyMap(),
                    showLocationMetadata = true,
                    onBack = {},
                    onRequestExif = {},
                    onToggleFavorite = {},
                    onOpenEditor = {},
                    onOpenCompress = {},
                    onOpenSlideshow = {},
                    onShare = {},
                    onMoveToVault = {},
                    onDelete = {},
                    onUpdateNote = { _, _ -> },
                    onAddTag = { _, _ -> },
                    onRemoveTag = { _, _ -> },
                    onSavePlaybackPosition = { _, _ -> },
                    onOpenExternalPlayer = {},
                    launchOriginRect = Rect(40f, 120f, 340f, 420f),
                    onTargetedShare = { _, _, _ -> }
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithText("sunset.jpg").assertIsDisplayed()
        composeRule.onNodeWithTag("viewer_favorite_button").assertIsDisplayed()
    }
}
