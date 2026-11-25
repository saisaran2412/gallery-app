package com.example.share

import android.content.Context
import android.net.Uri
import com.example.core.storage.AppStorageDirs
import com.example.data.local.MediaItemEntity
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Encapsulates the Google Nearby Connections API for offline Android-to-Android
 * discovery, advertising, pairing verification, and direct peer-to-peer file transfers.
 */
class NearbyService(
    private val context: Context,
    private val onMediaReceived: suspend (destFile: File, displayName: String, mimeType: String) -> MediaItemEntity? = { _, _, _ -> null }
) {
    constructor(context: Context) : this(context, { _, _, _ -> null })

    companion object {
        const val SERVICE_ID = "com.aistudio.folioalbum.airshare"
        private val TRANSFER_STRATEGY = Strategy.P2P_POINT_TO_POINT
        private const val MAX_INCOMING_BYTES = 4L * 1024 * 1024 * 1024
    }

    private data class IncomingFileMeta(
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionState = MutableStateFlow<AirShareSessionState>(AirShareSessionState.Idle)
    val sessionState: StateFlow<AirShareSessionState> = _sessionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<AirSharePeer>>(emptyList())
    val discoveredPeers: StateFlow<List<AirSharePeer>> = _discoveredPeers.asStateFlow()

    private val _transfers = MutableStateFlow<List<AirShareTransferItem>>(emptyList())
    val transfers: StateFlow<List<AirShareTransferItem>> = _transfers.asStateFlow()

    private val pendingFilePayloads = ConcurrentHashMap<Long, Payload>()
    private val incomingMetaByPayloadId = ConcurrentHashMap<Long, IncomingFileMeta>()
    private val knownEndpointNames = ConcurrentHashMap<String, String>()

    private val connectionsClient: ConnectionsClient by lazy {
        Nearby.getConnectionsClient(context.applicationContext)
    }

    fun checkCompatibility(): PlayServicesCompatibility {
        val availability = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        }.getOrDefault(ConnectionResult.SERVICE_INVALID)
        return mapConnectionResult(availability)
    }

    /**
     * Starts Nearby Connections discovery mode to scan for nearby Android devices
     * advertising [SERVICE_ID] for offline file sharing.
     */
    fun startDiscovery(localDeviceName: String) {
        val compat = checkCompatibility()
        if (!compat.isSupported) {
            _sessionState.value = AirShareSessionState.PlayServicesUnavailable(compat)
            return
        }

        val cleanName = localDeviceName.trim().ifEmpty { "Folio Device" }
        _discoveredPeers.value = emptyList()
        runCatching {
            connectionsClient.stopAdvertising()
            val options = DiscoveryOptions.Builder()
                .setStrategy(TRANSFER_STRATEGY)
                .build()
            connectionsClient.startDiscovery(
                SERVICE_ID,
                endpointDiscoveryCallback,
                options
            ).addOnSuccessListener {
                _sessionState.value = AirShareSessionState.Scanning(cleanName)
            }.addOnFailureListener { err ->
                _sessionState.value = AirShareSessionState.Error(
                    err.localizedMessage ?: "Unable to scan for nearby Android devices"
                )
            }
        }.onFailure { err ->
            _sessionState.value = AirShareSessionState.Error(
                err.localizedMessage ?: "Nearby Connections discovery failed to start"
            )
        }
    }

    fun stopDiscovery() {
        runCatching { connectionsClient.stopDiscovery() }
        if (_sessionState.value is AirShareSessionState.Scanning) {
            _sessionState.value = AirShareSessionState.Idle
        }
    }

    fun startAdvertising(localDeviceName: String) {
        val compat = checkCompatibility()
        if (!compat.isSupported) {
            _sessionState.value = AirShareSessionState.PlayServicesUnavailable(compat)
            return
        }

        val cleanName = localDeviceName.trim().ifEmpty { "Folio Device" }
        runCatching {
            connectionsClient.stopDiscovery()
            val options = AdvertisingOptions.Builder()
                .setStrategy(TRANSFER_STRATEGY)
                .build()
            connectionsClient.startAdvertising(
                cleanName,
                SERVICE_ID,
                connectionLifecycleCallback,
                options
            ).addOnSuccessListener {
                _sessionState.value = AirShareSessionState.ReceivingReady(cleanName)
            }.addOnFailureListener { err ->
                _sessionState.value = AirShareSessionState.Error(
                    err.localizedMessage ?: "Unable to start Nearby advertising"
                )
            }
        }.onFailure { err ->
            _sessionState.value = AirShareSessionState.Error(
                err.localizedMessage ?: "Nearby Connections could not initialize"
            )
        }
    }

    fun stopAdvertising() {
        runCatching { connectionsClient.stopAdvertising() }
        if (_sessionState.value is AirShareSessionState.ReceivingReady) {
            _sessionState.value = AirShareSessionState.Idle
        }
    }

    fun requestConnection(endpointId: String, localDeviceName: String) {
        val compat = checkCompatibility()
        if (!compat.isSupported) {
            _sessionState.value = AirShareSessionState.PlayServicesUnavailable(compat)
            return
        }

        val cleanName = localDeviceName.trim().ifEmpty { "Folio Device" }
        runCatching {
            connectionsClient.requestConnection(cleanName, endpointId, connectionLifecycleCallback)
                .addOnFailureListener { err ->
                    _sessionState.value = AirShareSessionState.Error(
                        err.localizedMessage ?: "Connection request failed"
                    )
                }
        }.onFailure { err ->
            _sessionState.value = AirShareSessionState.Error(
                err.localizedMessage ?: "Connection request failed"
            )
        }
    }

    fun acceptConnection(endpointId: String) {
        runCatching {
            connectionsClient.acceptConnection(endpointId, payloadCallback)
                .addOnFailureListener { err ->
                    _sessionState.value = AirShareSessionState.Error(
                        err.localizedMessage ?: "Failed to accept connection"
                    )
                }
        }
    }

    fun rejectConnection(endpointId: String) {
        runCatching { connectionsClient.rejectConnection(endpointId) }
        _sessionState.value = AirShareSessionState.Idle
    }

    fun sendMediaItems(endpointId: String, items: List<MediaItemEntity>) {
        if (items.isEmpty()) return
        scope.launch {
            items.forEach { item ->
                runCatching {
                    val uri = Uri.parse(item.uriString)
                    val filePayload = when (uri.scheme) {
                        "file" -> {
                            val file = File(uri.path.orEmpty())
                            if (!file.exists()) return@runCatching
                            Payload.fromFile(file)
                        }
                        else -> {
                            val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching
                            Payload.fromFile(pfd)
                        }
                    }

                    val metaJson = JSONObject().apply {
                        put("kind", "folio_file_meta")
                        put("filePayloadId", filePayload.id)
                        put("fileName", item.displayName)
                        put("mimeType", item.mimeType)
                        put("sizeBytes", item.sizeBytes)
                    }.toString()

                    upsertTransfer(
                        AirShareTransferItem(
                            payloadId = filePayload.id,
                            fileName = item.displayName,
                            mimeType = item.mimeType,
                            totalBytes = item.sizeBytes,
                            bytesTransferred = 0L,
                            isIncoming = false,
                            status = AirShareTransferStatus.QUEUED,
                            localUri = item.uriString
                        )
                    )

                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(metaJson.toByteArray(Charsets.UTF_8)))
                    connectionsClient.sendPayload(endpointId, filePayload)
                }.onFailure {
                    _sessionState.value = AirShareSessionState.Error(
                        "Could not read ${item.displayName} for offline transfer"
                    )
                }
            }
        }
    }

    fun disconnect(endpointId: String) {
        runCatching { connectionsClient.disconnectFromEndpoint(endpointId) }
        _sessionState.value = AirShareSessionState.Idle
    }

    fun stopAll() {
        runCatching {
            connectionsClient.stopDiscovery()
            connectionsClient.stopAdvertising()
            connectionsClient.stopAllEndpoints()
        }
        pendingFilePayloads.clear()
        incomingMetaByPayloadId.clear()
        _discoveredPeers.value = emptyList()
        _sessionState.value = AirShareSessionState.Idle
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            knownEndpointNames[endpointId] = info.endpointName
            val peer = AirSharePeer(
                endpointId = endpointId,
                displayName = info.endpointName,
                protocol = AirShareTransportProtocol.ANDROID_NEARBY
            )
            _discoveredPeers.update { current ->
                current.filterNot { it.endpointId == endpointId } + peer
            }
        }

        override fun onEndpointLost(endpointId: String) {
            _discoveredPeers.update { current ->
                current.filterNot { it.endpointId == endpointId }
            }
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
            knownEndpointNames[endpointId] = connectionInfo.endpointName
            _sessionState.value = AirShareSessionState.AwaitingConfirmation(
                AirSharePendingAuth(
                    endpointId = endpointId,
                    peerName = connectionInfo.endpointName,
                    authDigits = connectionInfo.authenticationDigits,
                    isIncomingConnection = connectionInfo.isIncomingConnection
                )
            )
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    runCatching {
                        connectionsClient.stopDiscovery()
                        connectionsClient.stopAdvertising()
                    }
                    val peerName = knownEndpointNames[endpointId] ?: "Nearby Android"
                    _sessionState.value = AirShareSessionState.Connected(
                        AirSharePeer(
                            endpointId = endpointId,
                            displayName = peerName,
                            protocol = AirShareTransportProtocol.ANDROID_NEARBY,
                            isConnected = true
                        )
                    )
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    _sessionState.value = AirShareSessionState.Error("Connection declined by peer")
                }
                else -> {
                    _sessionState.value = AirShareSessionState.Error("Unable to establish offline link")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            _sessionState.value = AirShareSessionState.Idle
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val text = payload.asBytes()?.toString(Charsets.UTF_8) ?: return
                    runCatching {
                        val obj = JSONObject(text)
                        if (obj.optString("kind") == "folio_file_meta") {
                            val filePayloadId = obj.getLong("filePayloadId")
                            val rawName = obj.optString("fileName", "received_${filePayloadId}.jpg")
                            val mime = obj.optString("mimeType", AirShareFileSanitizer.inferMimeType(rawName))
                            val size = obj.optLong("sizeBytes", 0L)
                            incomingMetaByPayloadId[filePayloadId] = IncomingFileMeta(
                                fileName = rawName,
                                mimeType = mime,
                                sizeBytes = size
                            )
                        }
                    }
                }
                Payload.Type.FILE -> {
                    pendingFilePayloads[payload.id] = payload
                    val meta = incomingMetaByPayloadId[payload.id]
                    val fileName = meta?.fileName ?: "airshare_${payload.id}.jpg"
                    val mime = meta?.mimeType ?: AirShareFileSanitizer.inferMimeType(fileName)
                    val total = meta?.sizeBytes?.takeIf { it > 0L } ?: payload.asFile()?.size ?: 0L
                    upsertTransfer(
                        AirShareTransferItem(
                            payloadId = payload.id,
                            fileName = fileName,
                            mimeType = mime,
                            totalBytes = total,
                            bytesTransferred = 0L,
                            isIncoming = true,
                            status = AirShareTransferStatus.TRANSFERRING
                        )
                    )
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            val payloadId = update.payloadId
            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> {
                    updateTransferProgress(
                        payloadId = payloadId,
                        bytesTransferred = update.bytesTransferred,
                        totalBytes = update.totalBytes,
                        status = AirShareTransferStatus.TRANSFERRING
                    )
                }
                PayloadTransferUpdate.Status.SUCCESS -> {
                    val filePayload = pendingFilePayloads.remove(payloadId)
                    val meta = incomingMetaByPayloadId.remove(payloadId)
                    if (filePayload != null) {
                        scope.launch {
                            val savedEntity = persistIncomingFilePayload(filePayload, meta)
                            updateTransferProgress(
                                payloadId = payloadId,
                                bytesTransferred = update.totalBytes.coerceAtLeast(update.bytesTransferred),
                                totalBytes = update.totalBytes.coerceAtLeast(update.bytesTransferred),
                                status = if (savedEntity != null) AirShareTransferStatus.COMPLETED else AirShareTransferStatus.FAILED,
                                localUri = savedEntity?.uriString
                            )
                        }
                    } else {
                        updateTransferProgress(
                            payloadId = payloadId,
                            bytesTransferred = update.totalBytes.coerceAtLeast(update.bytesTransferred),
                            totalBytes = update.totalBytes.coerceAtLeast(update.bytesTransferred),
                            status = AirShareTransferStatus.COMPLETED
                        )
                    }
                }
                PayloadTransferUpdate.Status.FAILURE -> {
                    pendingFilePayloads.remove(payloadId)
                    incomingMetaByPayloadId.remove(payloadId)
                    updateTransferProgress(
                        payloadId = payloadId,
                        bytesTransferred = update.bytesTransferred,
                        totalBytes = update.totalBytes,
                        status = AirShareTransferStatus.FAILED
                    )
                }
                PayloadTransferUpdate.Status.CANCELED -> {
                    pendingFilePayloads.remove(payloadId)
                    incomingMetaByPayloadId.remove(payloadId)
                    updateTransferProgress(
                        payloadId = payloadId,
                        bytesTransferred = update.bytesTransferred,
                        totalBytes = update.totalBytes,
                        status = AirShareTransferStatus.CANCELLED
                    )
                }
            }
        }
    }

    private suspend fun persistIncomingFilePayload(
        payload: Payload,
        meta: IncomingFileMeta?
    ): MediaItemEntity? = runCatching {
        val payloadFile = payload.asFile() ?: return@runCatching null
        val safeName = AirShareFileSanitizer.sanitizeFileName(
            meta?.fileName ?: "received_${System.currentTimeMillis()}.jpg"
        )
        val mime = meta?.mimeType?.takeIf { it.isNotBlank() }
            ?: AirShareFileSanitizer.inferMimeType(safeName)
        val extMime = AirShareFileSanitizer.inferMimeType(safeName)
        val isMedia = (mime.startsWith("image/") || mime.startsWith("video/")) &&
            (extMime.startsWith("image/") || extMime.startsWith("video/"))
        if (!isMedia || payloadFile.size > MAX_INCOMING_BYTES) {
            runCatching { payloadFile.close() }
            runCatching { payloadFile.asJavaFile()?.delete() }
            return@runCatching null
        }
        val destFile = File(
            AppStorageDirs.airShareDir(context),
            "${UUID.randomUUID().toString().take(8)}_$safeName"
        )

        val copied = payloadFile.asUri()?.let { contentUri ->
            context.contentResolver.openInputStream(contentUri)?.use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
                true
            }
        } ?: payloadFile.asJavaFile()?.let { src ->
            src.inputStream().use { input ->
                FileOutputStream(destFile).use { output -> input.copyTo(output) }
            }
            runCatching { src.delete() }
            true
        } ?: false

        runCatching { payloadFile.close() }
        if (!copied || !destFile.exists()) return@runCatching null
        onMediaReceived(destFile, safeName, mime)
    }.getOrNull()

    private fun upsertTransfer(item: AirShareTransferItem) {
        _transfers.update { list ->
            val without = list.filterNot { it.payloadId == item.payloadId }
            listOf(item) + without
        }
    }

    private fun updateTransferProgress(
        payloadId: Long,
        bytesTransferred: Long,
        totalBytes: Long,
        status: AirShareTransferStatus,
        localUri: String? = null
    ) {
        _transfers.update { list ->
            list.map { existing ->
                if (existing.payloadId == payloadId) {
                    existing.copy(
                        bytesTransferred = bytesTransferred,
                        totalBytes = if (totalBytes > 0L) totalBytes else existing.totalBytes,
                        status = status,
                        localUri = localUri ?: existing.localUri
                    )
                } else existing
            }
        }
    }

    fun mapConnectionResult(code: Int): PlayServicesCompatibility = when (code) {
        ConnectionResult.SUCCESS -> PlayServicesCompatibility.AVAILABLE
        ConnectionResult.SERVICE_MISSING -> PlayServicesCompatibility.SERVICE_MISSING
        ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> PlayServicesCompatibility.SERVICE_VERSION_UPDATE_REQUIRED
        ConnectionResult.SERVICE_DISABLED -> PlayServicesCompatibility.SERVICE_DISABLED
        else -> PlayServicesCompatibility.SERVICE_INVALID
    }
}
