package com.example.share

import java.io.File

enum class AirShareTransportProtocol(
    val title: String,
    val subtitle: String,
    val isAvailableNow: Boolean
) {
    ANDROID_NEARBY(
        title = "Google Nearby Connections",
        subtitle = "Direct Android-to-Android Wi-Fi Direct & Bluetooth transfer • Free & offline",
        isAvailableNow = true
    ),
    CROSS_PLATFORM_LAN(
        title = "Local Multipeer / LAN Bridge (Modular Slot)",
        subtitle = "Future adapter slot for iPhone-to-Android & iPhone-to-iPhone over local Wi-Fi/mDNS",
        isAvailableNow = false
    )
}

enum class AirShareMode(val label: String) {
    SEND("Send"),
    RECEIVE("Receive")
}

enum class PlayServicesCompatibility(
    val isSupported: Boolean,
    val title: String,
    val detail: String
) {
    AVAILABLE(
        isSupported = true,
        title = "Nearby Connections Ready",
        detail = "Google Play Services is available for free, direct device-to-device transfers."
    ),
    SERVICE_MISSING(
        isSupported = false,
        title = "Google Play Services Missing",
        detail = "AirShare Android-to-Android discovery uses Google Nearby Connections, which requires Google Play Services. No cloud fallback is used so your media stays offline."
    ),
    SERVICE_VERSION_UPDATE_REQUIRED(
        isSupported = false,
        title = "Google Play Services Update Required",
        detail = "Please update Google Play Services on this device to use Nearby Connections offline device-to-device sharing."
    ),
    SERVICE_DISABLED(
        isSupported = false,
        title = "Google Play Services Disabled",
        detail = "Enable Google Play Services in device settings to discover and transfer photos offline with nearby Android devices."
    ),
    SERVICE_INVALID(
        isSupported = false,
        title = "Google Play Services Incompatible",
        detail = "Google Play Services on this device cannot initialize Nearby Connections. Your local gallery remains unaffected."
    )
}

data class AirSharePeer(
    val endpointId: String,
    val displayName: String,
    val protocol: AirShareTransportProtocol = AirShareTransportProtocol.ANDROID_NEARBY,
    val isConnected: Boolean = false
)

data class AirSharePendingAuth(
    val endpointId: String,
    val peerName: String,
    val authDigits: String,
    val isIncomingConnection: Boolean
)

enum class AirShareTransferStatus {
    QUEUED,
    TRANSFERRING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class AirShareTransferItem(
    val payloadId: Long,
    val fileName: String,
    val mimeType: String,
    val totalBytes: Long,
    val bytesTransferred: Long,
    val isIncoming: Boolean,
    val status: AirShareTransferStatus,
    val localUri: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed interface AirShareSessionState {
    data object Idle : AirShareSessionState
    data class ReceivingReady(val deviceName: String) : AirShareSessionState
    data class Scanning(val deviceName: String) : AirShareSessionState
    data class AwaitingConfirmation(val pending: AirSharePendingAuth) : AirShareSessionState
    data class Connected(val peer: AirSharePeer) : AirShareSessionState
    data class PlayServicesUnavailable(val compatibility: PlayServicesCompatibility) : AirShareSessionState
    data class Error(val message: String) : AirShareSessionState
}

object AirShareFileSanitizer {
    fun sanitizeFileName(rawName: String, fallbackExt: String = "jpg"): String {
        val baseOnly = File(rawName.replace('\\', '/')).name
            .replace("..", "_")
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .trim('_')
        if (baseOnly.isBlank()) {
            return "airshare_${System.currentTimeMillis()}.$fallbackExt"
        }
        return if (baseOnly.contains('.')) baseOnly else "$baseOnly.$fallbackExt"
    }

    fun inferMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "heic", "heif" -> "image/heic"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> "application/octet-stream"
        }
    }
}
