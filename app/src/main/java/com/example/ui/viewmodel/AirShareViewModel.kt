package com.example.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.MediaItemEntity
import com.example.data.repository.GalleryRepository
import com.example.share.AirShareMode
import com.example.share.AirSharePeer
import com.example.share.AirShareSessionState
import com.example.share.AirShareTransferItem
import com.example.share.AirShareTransport
import com.example.share.AndroidNearbyConnectionsTransport
import com.example.share.PlayServicesCompatibility
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun defaultDeviceLabel(): String {
    val model = Build.MODEL.orEmpty().trim()
    return if (model.isNotEmpty()) model else "Folio Android"
}

data class AirShareUiState(
    val mode: AirShareMode = AirShareMode.SEND,
    val localDeviceName: String = defaultDeviceLabel(),
    val compatibility: PlayServicesCompatibility = PlayServicesCompatibility.AVAILABLE,
    val sessionState: AirShareSessionState = AirShareSessionState.Idle,
    val discoveredPeers: List<AirSharePeer> = emptyList(),
    val transfers: List<AirShareTransferItem> = emptyList(),
    val queuedItems: List<MediaItemEntity> = emptyList(),
    val hasPermissions: Boolean = false
)

class AirShareViewModel(
    application: Application,
    private val transport: AirShareTransport
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        transport = createDefaultTransport(application)
    )

    private val _mode = MutableStateFlow(AirShareMode.SEND)
    private val _deviceName = MutableStateFlow(defaultDeviceLabel())
    private val _compatibility = MutableStateFlow(transport.checkCompatibility())
    private val _queuedItems = MutableStateFlow<List<MediaItemEntity>>(emptyList())
    private val _hasPermissions = MutableStateFlow(hasAllNearbyPermissions(application))

    val queuedItems: StateFlow<List<MediaItemEntity>> = _queuedItems.asStateFlow()

    val uiState: StateFlow<AirShareUiState> = combine(
        _mode,
        _deviceName,
        _compatibility,
        transport.sessionState,
        transport.discoveredPeers,
        transport.transfers,
        _queuedItems,
        _hasPermissions
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        AirShareUiState(
            mode = values[0] as AirShareMode,
            localDeviceName = values[1] as String,
            compatibility = values[2] as PlayServicesCompatibility,
            sessionState = values[3] as AirShareSessionState,
            discoveredPeers = values[4] as List<AirSharePeer>,
            transfers = values[5] as List<AirShareTransferItem>,
            queuedItems = values[6] as List<MediaItemEntity>,
            hasPermissions = values[7] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AirShareUiState(
            compatibility = _compatibility.value,
            hasPermissions = _hasPermissions.value
        )
    )

    fun refreshCompatibilityAndPermissions() {
        _compatibility.value = transport.checkCompatibility()
        _hasPermissions.value = hasAllNearbyPermissions(getApplication())
    }

    fun setPermissionsGranted(granted: Boolean) {
        _hasPermissions.value = granted || hasAllNearbyPermissions(getApplication())
    }

    fun setMode(mode: AirShareMode) {
        if (_mode.value == mode) return
        transport.stopAll()
        _mode.value = mode
    }

    fun setDeviceName(name: String) {
        _deviceName.value = name.take(28)
    }

    fun setQueuedMedia(items: List<MediaItemEntity>) {
        _queuedItems.value = items
        if (items.isNotEmpty()) {
            _mode.value = AirShareMode.SEND
        }
    }

    fun addQueuedMedia(items: List<MediaItemEntity>) {
        val current = _queuedItems.value.associateBy { it.id }.toMutableMap()
        items.forEach { current[it.id] = it }
        _queuedItems.value = current.values.toList()
        _mode.value = AirShareMode.SEND
    }

    fun removeQueuedItem(mediaId: String) {
        _queuedItems.value = _queuedItems.value.filterNot { it.id == mediaId }
    }

    fun clearQueuedMedia() {
        _queuedItems.value = emptyList()
    }

    fun startScanning() {
        refreshCompatibilityAndPermissions()
        com.example.share.AirShareForegroundService.start(getApplication())
        transport.startDiscovery(_deviceName.value)
    }

    fun startReceiving() {
        refreshCompatibilityAndPermissions()
        com.example.share.AirShareForegroundService.start(getApplication())
        transport.startAdvertising(_deviceName.value)
    }

    fun connectToPeer(endpointId: String) {
        transport.requestConnection(endpointId, _deviceName.value)
    }

    fun acceptPendingConnection(endpointId: String) {
        transport.acceptConnection(endpointId)
    }

    fun rejectPendingConnection(endpointId: String) {
        transport.rejectConnection(endpointId)
    }

    fun sendQueuedToPeer(endpointId: String) {
        val items = _queuedItems.value
        if (items.isEmpty()) return
        transport.sendMediaItems(endpointId, items)
    }

    fun disconnect(endpointId: String) {
        transport.disconnect(endpointId)
    }

    fun stopSession() {
        transport.stopAll()
        com.example.share.AirShareForegroundService.stop(getApplication())
    }

    override fun onCleared() {
        com.example.share.AirShareForegroundService.stop(getApplication())
        super.onCleared()
        transport.stopAll()
    }

    companion object {
        private fun createDefaultTransport(application: Application): AirShareTransport {
            val repo = GalleryRepository.getInstance(application)
            return AndroidNearbyConnectionsTransport(
                context = application,
                onMediaReceived = { destFile, displayName, mimeType ->
                    repo.registerReceivedAirShareMedia(destFile, displayName, mimeType)
                }
            )
        }

        fun requiredNearbyPermissions(sdkInt: Int = Build.VERSION.SDK_INT): Array<String> {
            return when {
                sdkInt >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.NEARBY_WIFI_DEVICES
                )
                sdkInt >= Build.VERSION_CODES.S -> arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_ADVERTISE,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                else -> arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
        }

        fun hasAllNearbyPermissions(context: Context): Boolean {
            return requiredNearbyPermissions().all { perm ->
                ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
            }
        }
    }
}
