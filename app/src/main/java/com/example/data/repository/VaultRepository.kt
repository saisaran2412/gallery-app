package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.annotation.VisibleForTesting
import com.example.core.storage.AppStorageDirs
import com.example.core.storage.PreferencesManager
import com.example.data.local.AlbumDatabase
import com.example.data.local.MediaItemEntity
import com.example.data.local.vault.VaultAuditLogEntity
import com.example.data.local.vault.VaultDatabase
import com.example.data.local.vault.toVaultItemEntity
import com.example.security.VaultCryptoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.crypto.Cipher

/**
 * Repository owning the SQLCipher-encrypted Room VaultDatabase and Keystore-backed
 * encryption/decryption lifecycle for Hidden Vault media and audit logs.
 */
class VaultRepository(private val context: Context) {

    private val prefs = PreferencesManager(context)
    private val galleryDao = AlbumDatabase.getInstance(context).galleryDao()

    private val _activeDb = MutableStateFlow<VaultDatabase?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val vaultMedia: Flow<List<MediaItemEntity>> = _activeDb.flatMapLatest { db ->
        db?.vaultDao()?.observeVaultItems()?.map { items ->
            items.map { it.toMediaItemEntity() }
        } ?: flowOf(emptyList())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val auditLogs: Flow<List<VaultAuditLogEntity>> = _activeDb.flatMapLatest { db ->
        db?.vaultDao()?.observeRecentAuditLogs() ?: flowOf(emptyList())
    }

    private suspend fun ensureDatabaseKeyBytes(): ByteArray {
        val settings = prefs.settingsFlow.first()
        val existingBlob = settings.vaultDbKeyBlob
        if (existingBlob.isNotBlank()) {
            val unsealed = VaultCryptoManager.unsealDatabaseKeyWithAppKeystore(existingBlob)
            if (unsealed != null && unsealed.isNotEmpty()) {
                return unsealed
            }
        }
        val freshKey = VaultCryptoManager.generateRandomDatabaseKey()
        val sealedBlob = VaultCryptoManager.sealDatabaseKeyWithAppKeystore(freshKey)
        prefs.setVaultDbKeyBlob(sealedBlob)
        return freshKey
    }

    suspend fun unlockWithAppKeystore(reason: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val dbKey = ensureDatabaseKeyBytes()
            try {
                val db = VaultDatabase.openWithPassphrase(context, dbKey)
                migrateLegacyVaultItemsIfAny(db)
                db.vaultDao().insertAuditLog(
                    VaultAuditLogEntity(eventType = "UNLOCK", details = reason)
                )
                _activeDb.value = db
                true
            } finally {
                VaultCryptoManager.zeroOut(dbKey)
            }
        }.getOrDefault(false)
    }

    suspend fun unlockWithBiometricCipher(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val settings = prefs.settingsFlow.first()
        val bioBlob = settings.vaultBiometricKeyBlob
        val dbKey = VaultCryptoManager.unsealDatabaseKeyWithBiometricCipher(cipher, bioBlob)
            ?: return@withContext false
        return@withContext try {
            val db = VaultDatabase.openWithPassphrase(context, dbKey)
            migrateLegacyVaultItemsIfAny(db)
            db.vaultDao().insertAuditLog(
                VaultAuditLogEntity(
                    eventType = "UNLOCK",
                    details = "BiometricPrompt CryptoObject unlock"
                )
            )
            _activeDb.value = db
            true
        } finally {
            VaultCryptoManager.zeroOut(dbKey)
        }
    }

    suspend fun enrollBiometricWithCipher(cipher: Cipher): Boolean = withContext(Dispatchers.IO) {
        val dbKey = ensureDatabaseKeyBytes()
        try {
            val bioBlob = VaultCryptoManager.sealDatabaseKeyWithBiometricCipher(cipher, dbKey)
            prefs.setVaultBiometricKeyBlob(bioBlob)
            _activeDb.value?.vaultDao()?.insertAuditLog(
                VaultAuditLogEntity(
                    eventType = "SECURITY",
                    details = "Bound SQLCipher key to BiometricPrompt CryptoObject"
                )
            )
            true
        } catch (_: Exception) {
            false
        } finally {
            VaultCryptoManager.zeroOut(dbKey)
        }
    }

    suspend fun lockVault() = withContext(Dispatchers.IO) {
        _activeDb.value?.let { db ->
            runCatching {
                if (db.isOpen) {
                    db.vaultDao().insertAuditLog(
                        VaultAuditLogEntity(eventType = "LOCK", details = "Vault locked & closed")
                    )
                }
            }
        }
        _activeDb.value = null
        VaultDatabase.lockAndClose()
        AppStorageDirs.clearTempPreviews(context)
    }

    suspend fun encryptAndStoreItems(items: List<MediaItemEntity>): Int = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext 0
        val wasAlreadyUnlocked = _activeDb.value != null
        val dbKey = if (wasAlreadyUnlocked) null else ensureDatabaseKeyBytes()
        val db = _activeDb.value ?: VaultDatabase.openWithPassphrase(context, requireNotNull(dbKey))

        try {
            var moved = 0
            for (item in items) {
                runCatching {
                    val vaultFile = "vault_${UUID.randomUUID()}.enc"
                    VaultCryptoManager.encryptUriToVault(context, Uri.parse(item.uriString), vaultFile)
                    db.vaultDao().upsertVaultItem(item.toVaultItemEntity(vaultFile))
                    // Keep only an in_vault suppression flag in unencrypted AlbumDatabase;
                    // vaultFileName and private metadata live strictly inside SQLCipher VaultDatabase.
                    galleryDao.setVaultState(item.id, inVault = true, vaultFileName = "")
                    moved++
                }
            }
            if (moved > 0) {
                db.vaultDao().insertAuditLog(
                    VaultAuditLogEntity(eventType = "ENCRYPT", details = "Encrypted $moved item(s) into Vault")
                )
            }
            moved
        } finally {
            VaultCryptoManager.zeroOut(dbKey)
            if (!wasAlreadyUnlocked) {
                VaultDatabase.lockAndClose()
            }
        }
    }

    suspend fun removeItemFromVault(item: MediaItemEntity) = withContext(Dispatchers.IO) {
        val db = _activeDb.value ?: return@withContext
        val stored = db.vaultDao().getVaultItemById(item.id)
        val fileName = stored?.vaultFileName?.ifEmpty { item.vaultFileName } ?: item.vaultFileName
        if (fileName.isNotEmpty()) {
            VaultCryptoManager.deleteVaultFile(context, fileName)
        }
        db.vaultDao().deleteVaultItemById(item.id)
        db.vaultDao().insertAuditLog(
            VaultAuditLogEntity(eventType = "RESTORE", details = "Restored ${item.displayName} to Gallery")
        )

        val existing = galleryDao.getMediaById(item.id)
        if (existing != null) {
            galleryDao.setVaultState(item.id, inVault = false, vaultFileName = "")
        } else {
            galleryDao.upsertSingleMedia(item.copy(isInVault = false, vaultFileName = ""))
        }
    }

    suspend fun decryptVaultPreview(item: MediaItemEntity): Uri? = withContext(Dispatchers.IO) {
        if (_activeDb.value == null) return@withContext null
        val fileName = item.vaultFileName
        if (fileName.isEmpty()) return@withContext null
        val ext = item.displayName.substringAfterLast('.', if (item.isVideo) "mp4" else "jpg")
        val decrypted = VaultCryptoManager.decryptVaultFileToTemp(context, fileName, ext)
            ?: return@withContext null
        Uri.fromFile(decrypted)
    }

    private suspend fun migrateLegacyVaultItemsIfAny(db: VaultDatabase) {
        val legacyItems = galleryDao.getAllMediaSnapshot()
            .filter { it.isInVault && it.vaultFileName.isNotEmpty() }
        if (legacyItems.isEmpty()) return

        db.vaultDao().upsertVaultItems(
            legacyItems.map { it.toVaultItemEntity(it.vaultFileName) }
        )
        legacyItems.forEach { legacy ->
            galleryDao.setVaultState(legacy.id, inVault = true, vaultFileName = "")
        }
        db.vaultDao().insertAuditLog(
            VaultAuditLogEntity(
                eventType = "MIGRATE",
                details = "Migrated ${legacyItems.size} vault record(s) into SQLCipher DB"
            )
        )
    }

    @VisibleForTesting
    internal fun setActiveDatabaseForTesting(db: VaultDatabase?) {
        _activeDb.value = db
    }

    companion object {
        @Volatile
        private var INSTANCE: VaultRepository? = null

        fun getInstance(context: Context): VaultRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VaultRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
