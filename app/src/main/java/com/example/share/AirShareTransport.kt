package com.example.share

import com.example.data.local.MediaItemEntity
import kotlinx.coroutines.flow.StateFlow

/**
 * Modular device-to-device transport abstraction for AirShare.
 *
 * Current implementation:
 *  - [AndroidNearbyConnectionsTransport]: Android-to-Android direct offline discovery & transfer
 *    via Google Nearby Connections (free, zero cloud servers, no per-transfer fees).
 *
 * Future cross-platform extensibility (without altering UI or ViewModels):
 *  - iPhone-to-Android / iPhone-to-iPhone adapters can implement this interface using compatible
 *    local-only technologies such as mDNS/Bonjour (`NsdManager`) + peer-to-peer local TLS HTTP
 *    or Apple MultipeerConnectivity bridges over Wi-Fi/BLE.
 */
interface AirShareTransport {
    val protocol: AirShareTransportProtocol
    val sessionState: StateFlow<AirShareSessionState>
    val discoveredPeers: StateFlow<List<AirSharePeer>>
    val transfers: StateFlow<List<AirShareTransferItem>>

    fun checkCompatibility(): PlayServicesCompatibility
    fun startAdvertising(localDeviceName: String)
    fun startDiscovery(localDeviceName: String)
    fun requestConnection(endpointId: String, localDeviceName: String)
    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)
    fun sendMediaItems(endpointId: String, items: List<MediaItemEntity>)
    fun disconnect(endpointId: String)
    fun stopAll()
}
