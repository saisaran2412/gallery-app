package com.example.share

import android.content.Context
import com.example.data.local.MediaItemEntity
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Direct Android-to-Android offline AirShare transport powered by [NearbyService]
 * (Google Nearby Connections API).
 *
 * - Zero cloud servers or external transfer relays.
 * - Zero subscription, per-transfer, or API usage charges.
 * - Gracefully handles devices where Google Play Services is missing or outdated.
 */
class AndroidNearbyConnectionsTransport(
    context: Context,
    onMediaReceived: suspend (destFile: File, displayName: String, mimeType: String) -> MediaItemEntity?
) : AirShareTransport {

    val nearbyService = NearbyService(
        context = context,
        onMediaReceived = onMediaReceived
    )

    override val protocol: AirShareTransportProtocol = AirShareTransportProtocol.ANDROID_NEARBY
    override val sessionState: StateFlow<AirShareSessionState> = nearbyService.sessionState
    override val discoveredPeers: StateFlow<List<AirSharePeer>> = nearbyService.discoveredPeers
    override val transfers: StateFlow<List<AirShareTransferItem>> = nearbyService.transfers

    override fun checkCompatibility(): PlayServicesCompatibility =
        nearbyService.checkCompatibility()

    override fun startAdvertising(localDeviceName: String) =
        nearbyService.startAdvertising(localDeviceName)

    override fun startDiscovery(localDeviceName: String) =
        nearbyService.startDiscovery(localDeviceName)

    override fun requestConnection(endpointId: String, localDeviceName: String) =
        nearbyService.requestConnection(endpointId, localDeviceName)

    override fun acceptConnection(endpointId: String) =
        nearbyService.acceptConnection(endpointId)

    override fun rejectConnection(endpointId: String) =
        nearbyService.rejectConnection(endpointId)

    override fun sendMediaItems(endpointId: String, items: List<MediaItemEntity>) =
        nearbyService.sendMediaItems(endpointId, items)

    override fun disconnect(endpointId: String) =
        nearbyService.disconnect(endpointId)

    override fun stopAll() =
        nearbyService.stopAll()
}
