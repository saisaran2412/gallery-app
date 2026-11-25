package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.storage.AppStorageDirs
import com.example.core.storage.PreferencesManager
import com.example.core.storage.UserSettings
import com.example.data.local.MediaItemEntity
import com.example.data.local.vault.VaultAuditLogEntity
import com.example.data.repository.VaultRepository
import com.example.security.BiometricCapability
import com.example.security.BiometricCipherResult
import com.example.security.VaultBiometricHelper
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.crypto.Cipher

data class VaultUiState(
    val isUnlocked: Boolean = false,
    val hasPinConfigured: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricBoundWithCrypto: Boolean = false,
    val biometricCapability: BiometricCapability = BiometricCapability.UNAVAILABLE,
    val vaultItems: List<MediaItemEntity> = emptyList(),
    val auditLogs: List<VaultAuditLogEntity> = emptyList(),
    val decryptedPreviewUris: Map<String, Uri> = emptyMap(),
    val pinError: String? = null,
    val isBusy: Boolean = false
)

sealed interface BiometricPrepareResult {
    data class Ready(val cipher: Cipher?) : BiometricPrepareResult
    data class Failed(val message: String) : BiometricPrepareResult
}

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val vaultRepository = VaultRepository.getInstance(application)
    private val prefs = PreferencesManager(application)

    private val _isUnlocked = MutableStateFlow(false)
    private val _decryptedUris = MutableStateFlow<Map<String, Uri>>(emptyMap())
    private val _pinError = MutableStateFlow<String?>(null)
    private val _isBusy = MutableStateFlow(false)
    private val _latestSettings = MutableStateFlow(UserSettings())
    private val _biometricCapability = MutableStateFlow(
        VaultBiometricHelper.checkCapability(application)
    )


    init {
        viewModelScope.launch {
            prefs.settingsFlow.collect { _latestSettings.value = it }
        }
    }

    val uiState: StateFlow<VaultUiState> = combine(
        prefs.settingsFlow,
        vaultRepository.vaultMedia,
        vaultRepository.auditLogs,
        _isUnlocked,
        _decryptedUris,
        _pinError,
        _isBusy,
        _biometricCapability
    ) { values ->
        val settings = values[0] as UserSettings
        @Suppress("UNCHECKED_CAST")
        val items = values[1] as List<MediaItemEntity>
        @Suppress("UNCHECKED_CAST")
        val logs = values[2] as List<VaultAuditLogEntity>
        val unlocked = values[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val decrypted = values[4] as Map<String, Uri>
        val error = values[5] as String?
        val busy = values[6] as Boolean
        val bioCap = values[7] as BiometricCapability

        VaultUiState(
            isUnlocked = unlocked,
            hasPinConfigured = settings.hasVaultPin,
            biometricEnabled = settings.vaultBiometricEnabled,
            biometricBoundWithCrypto = settings.vaultBiometricKeyBlob.isNotBlank(),
            biometricCapability = bioCap,
            vaultItems = if (unlocked) items else emptyList(),
            auditLogs = if (unlocked) logs else emptyList(),
            decryptedPreviewUris = if (unlocked) decrypted else emptyMap(),
            pinError = error,
            isBusy = busy
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = VaultUiState()
    )

    fun refreshBiometricCapability() {
        _biometricCapability.value = VaultBiometricHelper.checkCapability(getApplication())
    }

    fun setupPin(pin: String) {
        if (pin.length !in 4..6) {
            _pinError.value = "PIN must be 4 to 6 digits"
            return
        }
        viewModelScope.launch {
            _isBusy.value = true
            val salt = VaultCryptoManager.generateSaltBase64()
            val hash = VaultCryptoManager.hashPin(pin, salt)
            prefs.setVaultPin(hash, salt)
            val opened = vaultRepository.unlockWithAppKeystore("Vault initialized with PIN")
            _isBusy.value = false
            if (opened) {
                _pinError.value = null
                _isUnlocked.value = true
            } else {
                _pinError.value = "Unable to initialize encrypted Vault database."
            }
        }
    }

    fun verifyPin(pin: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val (storedFails, storedLock) = prefs.readPinGuard()
            // Clock rolled back? Treat a lock further away than the max delay as expired-safe cap.
            val lockUntil = storedLock.coerceAtMost(now + PIN_LOCK_STEPS_MS.last())
            if (now < lockUntil) {
                val remainingSec = ((lockUntil - now) / 1000L).coerceAtLeast(1L)
                _pinError.value = "Too many attempts. Try again in ${remainingSec}s."
                return@launch
            }
            val settings = _latestSettings.value
            val valid = withContext(Dispatchers.Default) {
                VaultCryptoManager.verifyPin(pin, settings.vaultPinSalt, settings.vaultPinHash)
            }
            if (!valid) {
                val fails = storedFails + 1
                if (fails >= PIN_FREE_ATTEMPTS) {
                    val step = (fails - PIN_FREE_ATTEMPTS).coerceAtMost(PIN_LOCK_STEPS_MS.lastIndex)
                    val delayMs = PIN_LOCK_STEPS_MS[step]
                    prefs.writePinGuard(fails, now + delayMs)
                    _pinError.value = "Too many attempts. Locked for ${delayMs / 1000L}s."
                } else {
                    prefs.writePinGuard(fails, 0L)
                    _pinError.value = "Incorrect PIN. Try again."
                }
                return@launch
            }

            prefs.writePinGuard(0, 0L)
            _isBusy.value = true
            val opened = vaultRepository.unlockWithAppKeystore("Unlocked via PIN")
            _isBusy.value = false
            if (opened) {
                _pinError.value = null
                _isUnlocked.value = true
            } else {
                _pinError.value = "Failed to decrypt Vault database."
            }
        }
    }

    fun prepareBiometricUnlock(): BiometricPrepareResult {
        val cap = VaultBiometricHelper.checkCapability(getApplication())
        _biometricCapability.value = cap
        if (cap != BiometricCapability.STRONG_CRYPTO && cap != BiometricCapability.WEAK_ONLY) {
            return BiometricPrepareResult.Failed("Biometric authentication is unavailable on this device.")
        }

        val settings = _latestSettings.value
        if (cap == BiometricCapability.STRONG_CRYPTO && settings.vaultBiometricKeyBlob.isNotBlank()) {
            return when (val cipherResult = VaultCryptoManager.createBiometricDecryptCipher(settings.vaultBiometricKeyBlob)) {
                is BiometricCipherResult.Ready -> BiometricPrepareResult.Ready(cipherResult.cipher)
                BiometricCipherResult.KeyInvalidated -> {
                    viewModelScope.launch { prefs.clearVaultBiometricBinding() }
                    val msg = "Device biometrics changed. Unlock with PIN and re-enable biometric unlock."
                    _pinError.value = msg
                    BiometricPrepareResult.Failed(msg)
                }
                is BiometricCipherResult.Unavailable -> {
                    // Fall back to non-crypto strong/weak check only if blob wasn't bound
                    BiometricPrepareResult.Failed(cipherResult.reason)
                }
            }
        }
        return BiometricPrepareResult.Ready(null)
    }

    fun completeBiometricUnlock(cipher: Cipher?) {
        viewModelScope.launch {
            _isBusy.value = true
            val opened = if (cipher != null && _latestSettings.value.vaultBiometricKeyBlob.isNotBlank()) {
                vaultRepository.unlockWithBiometricCipher(cipher)
            } else {
                vaultRepository.unlockWithAppKeystore("Unlocked via BiometricPrompt")
            }
            _isBusy.value = false
            if (opened) {
                viewModelScope.launch { prefs.writePinGuard(0, 0L) }
                _pinError.value = null
                _isUnlocked.value = true
            } else {
                _pinError.value = "Biometric unlock could not verify Vault key. Enter PIN."
            }
        }
    }

    fun prepareBiometricEnrollment(): BiometricPrepareResult {
        val cap = VaultBiometricHelper.checkCapability(getApplication())
        _biometricCapability.value = cap
        return when (cap) {
            BiometricCapability.STRONG_CRYPTO -> {
                when (val res = VaultCryptoManager.createBiometricEncryptCipher()) {
                    is BiometricCipherResult.Ready -> BiometricPrepareResult.Ready(res.cipher)
                    BiometricCipherResult.KeyInvalidated -> BiometricPrepareResult.Failed("Biometric key invalidated. Try again.")
                    is BiometricCipherResult.Unavailable -> BiometricPrepareResult.Failed(res.reason)
                }
            }
            BiometricCapability.WEAK_ONLY -> BiometricPrepareResult.Ready(null)
            BiometricCapability.NOT_ENROLLED -> BiometricPrepareResult.Failed("No biometrics enrolled in Android Settings.")
            BiometricCapability.UNAVAILABLE -> BiometricPrepareResult.Failed("Biometric hardware unavailable.")
        }
    }

    fun completeBiometricEnrollment(cipher: Cipher?) {
        viewModelScope.launch {
            if (cipher != null) {
                val bound = vaultRepository.enrollBiometricWithCipher(cipher)
                if (!bound) {
                    _pinError.value = "Could not bind biometric key."
                }
            } else {
                prefs.setVaultBiometricEnabled(true)
            }
        }
    }

    fun disableBiometric() {
        viewModelScope.launch {
            VaultCryptoManager.deleteBiometricKey()
            prefs.clearVaultBiometricBinding()
        }
    }

    fun reportBiometricError(message: String) {
        _pinError.value = message
    }

    fun lockVault() {
        _isUnlocked.value = false
        _decryptedUris.value = emptyMap()
        _pinError.value = null
        viewModelScope.launch {
            vaultRepository.lockVault()
        }
    }

    private val decryptInFlight: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    private val decryptGate = kotlinx.coroutines.sync.Semaphore(2)

    fun ensureDecryptedPreview(item: MediaItemEntity) {
        if (!_isUnlocked.value || _decryptedUris.value.containsKey(item.id)) return
        // One decrypt per item at a time, and at most two in parallel, so tiles never
        // fight over the same temp file and large videos don't flood the cache.
        if (!decryptInFlight.add(item.id)) return
        viewModelScope.launch {
            try {
                val uri = decryptGate.withPermit { vaultRepository.decryptVaultPreview(item) }
                if (uri != null && _isUnlocked.value) {
                    _decryptedUris.update { it + (item.id to uri) }
                }
            } finally {
                decryptInFlight.remove(item.id)
            }
        }
    }

    fun moveItemsToVault(items: List<MediaItemEntity>, onComplete: (Int) -> Unit = {}) {
        if (items.isEmpty()) return
        viewModelScope.launch {
            _isBusy.value = true
            val count = vaultRepository.encryptAndStoreItems(items)
            _isBusy.value = false
            onComplete(count)
        }
    }

    fun removeFromVault(item: MediaItemEntity) {
        viewModelScope.launch {
            _isBusy.value = true
            vaultRepository.removeItemFromVault(item)
            _decryptedUris.value = _decryptedUris.value - item.id
            _isBusy.value = false
        }
    }

    override fun onCleared() {
        super.onCleared()
        AppStorageDirs.clearTempPreviews(getApplication())
    }

    private companion object {
        const val PIN_FREE_ATTEMPTS = 5
        val PIN_LOCK_STEPS_MS = longArrayOf(30_000L, 120_000L, 600_000L, 1_800_000L)
    }
}
