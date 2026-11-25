package com.example.security

import android.content.Context
import android.net.Uri
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.core.storage.AppStorageDirs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec

sealed interface BiometricCipherResult {
    data class Ready(val cipher: Cipher) : BiometricCipherResult
    data object KeyInvalidated : BiometricCipherResult
    data class Unavailable(val reason: String) : BiometricCipherResult
}

/**
 * Hardware-backed Android Keystore cryptography for:
 * 1) AES-256-GCM encryption of Hidden Vault media payloads
 * 2) Keystore-wrapped 256-bit SQLCipher database key
 * 3) BiometricPrompt CryptoObject AES-256-CBC key binding (STRONG biometric auth-per-use)
 * 4) Salted PBKDF2-HMAC-SHA256 PIN verification
 */
object VaultCryptoManager {

    private const val MEDIA_KEY_ALIAS = "folio_vault_aes256_key"
    private const val DB_WRAP_KEY_ALIAS = "folio_vault_db_wrap_key"
    private const val BIOMETRIC_KEY_ALIAS = "folio_vault_biometric_cbc_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private const val GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val CBC_TRANSFORMATION =
        "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_CBC}/${KeyProperties.ENCRYPTION_PADDING_PKCS7}"

    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12
    private const val CBC_IV_BYTES = 16
    private const val DB_KEY_BYTES = 32
    private const val PBKDF2_ITERATIONS = 15_000

    private fun getOrCreateGcmKey(alias: String): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun getOrCreateBiometricKey(forceRecreate: Boolean = false): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (forceRecreate && keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
            keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
        }
        val existing = keyStore.getKey(BIOMETRIC_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun deleteBiometricKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(BIOMETRIC_KEY_ALIAS)) {
                keyStore.deleteEntry(BIOMETRIC_KEY_ALIAS)
            }
        }
    }

    fun generateRandomDatabaseKey(): ByteArray {
        val key = ByteArray(DB_KEY_BYTES)
        SecureRandom().nextBytes(key)
        return key
    }

    fun zeroOut(bytes: ByteArray?) {
        bytes?.fill(0)
    }

    /**
     * Encrypts the raw 256-bit SQLCipher database key using Android Keystore AES-256-GCM.
     * Returned blob format: Base64(12-byte IV || ciphertext).
     */
    fun sealDatabaseKeyWithAppKeystore(rawKey: ByteArray): String {
        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateGcmKey(DB_WRAP_KEY_ALIAS))
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES)
        val encrypted = cipher.doFinal(rawKey)
        val payload = ByteArray(GCM_IV_BYTES + encrypted.size)
        System.arraycopy(iv, 0, payload, 0, GCM_IV_BYTES)
        System.arraycopy(encrypted, 0, payload, GCM_IV_BYTES, encrypted.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * Decrypts the Keystore-wrapped SQLCipher database key back into raw key bytes.
     */
    fun unsealDatabaseKeyWithAppKeystore(blobBase64: String): ByteArray? {
        if (blobBase64.isBlank()) return null
        return runCatching {
            val payload = Base64.decode(blobBase64, Base64.NO_WRAP)
            if (payload.size <= GCM_IV_BYTES) return null
            val iv = payload.copyOfRange(0, GCM_IV_BYTES)
            val encrypted = payload.copyOfRange(GCM_IV_BYTES, payload.size)
            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateGcmKey(DB_WRAP_KEY_ALIAS),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )
            cipher.doFinal(encrypted)
        }.getOrNull()
    }

    /**
     * Initializes an encryption Cipher bound to a biometric-authenticated Keystore key
     * for use inside BiometricPrompt.CryptoObject when enrolling biometric unlock.
     */
    fun createBiometricEncryptCipher(): BiometricCipherResult {
        return try {
            val key = getOrCreateBiometricKey(forceRecreate = true)
            val cipher = Cipher.getInstance(CBC_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            BiometricCipherResult.Ready(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteBiometricKey()
            BiometricCipherResult.KeyInvalidated
        } catch (e: Exception) {
            BiometricCipherResult.Unavailable(e.message ?: "Biometric key setup unavailable")
        }
    }

    /**
     * Initializes a decryption Cipher bound to the biometric-authenticated Keystore key
     * using the IV embedded in [biometricBlobBase64].
     */
    fun createBiometricDecryptCipher(biometricBlobBase64: String): BiometricCipherResult {
        if (biometricBlobBase64.isBlank()) {
            return BiometricCipherResult.Unavailable("Biometric key not enrolled")
        }
        return try {
            val payload = Base64.decode(biometricBlobBase64, Base64.NO_WRAP)
            if (payload.size <= CBC_IV_BYTES) {
                return BiometricCipherResult.Unavailable("Corrupted biometric key envelope")
            }
            val iv = payload.copyOfRange(0, CBC_IV_BYTES)
            val key = getOrCreateBiometricKey(forceRecreate = false)
            val cipher = Cipher.getInstance(CBC_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            BiometricCipherResult.Ready(cipher)
        } catch (e: KeyPermanentlyInvalidatedException) {
            deleteBiometricKey()
            BiometricCipherResult.KeyInvalidated
        } catch (e: Exception) {
            BiometricCipherResult.Unavailable(e.message ?: "Biometric key unavailable")
        }
    }

    /**
     * Seals the raw SQLCipher DB key using the BiometricPrompt-authenticated [cipher].
     */
    fun sealDatabaseKeyWithBiometricCipher(cipher: Cipher, rawKey: ByteArray): String {
        val iv = cipher.iv
        require(iv.size == CBC_IV_BYTES)
        val encrypted = cipher.doFinal(rawKey)
        val payload = ByteArray(CBC_IV_BYTES + encrypted.size)
        System.arraycopy(iv, 0, payload, 0, CBC_IV_BYTES)
        System.arraycopy(encrypted, 0, payload, CBC_IV_BYTES, encrypted.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    /**
     * Unseals the raw SQLCipher DB key using the BiometricPrompt-authenticated [cipher].
     */
    fun unsealDatabaseKeyWithBiometricCipher(cipher: Cipher, biometricBlobBase64: String): ByteArray? {
        if (biometricBlobBase64.isBlank()) return null
        return runCatching {
            val payload = Base64.decode(biometricBlobBase64, Base64.NO_WRAP)
            if (payload.size <= CBC_IV_BYTES) return null
            val encrypted = payload.copyOfRange(CBC_IV_BYTES, payload.size)
            cipher.doFinal(encrypted)
        }.getOrNull()
    }

    fun generateSaltBase64(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashPin(pin: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derived = factory.generateSecret(spec).encoded
        return Base64.encodeToString(derived, Base64.NO_WRAP)
    }

    fun verifyPin(pin: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (saltBase64.isEmpty() || expectedHashBase64.isEmpty()) return false
        val actual = hashPin(pin, saltBase64).toByteArray(Charsets.UTF_8)
        val expected = expectedHashBase64.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actual, expected)
    }

    /**
     * Encrypts [sourceUri] using Android Keystore AES-256-GCM into files/private_vault/[vaultFileName].
     */
    fun encryptUriToVault(context: Context, sourceUri: Uri, vaultFileName: String): File {
        val vaultDir = AppStorageDirs.privateVaultDir(context)
        val safeName = File(vaultFileName).name
        val targetFile = File(vaultDir, safeName)

        val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateGcmKey(MEDIA_KEY_ALIAS))
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES)

        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            FileOutputStream(targetFile).use { fos ->
                fos.write(iv)
                CipherOutputStream(fos, cipher).use { cos ->
                    input.copyTo(cos, bufferSize = 16 * 1024)
                }
            }
        } ?: error("Unable to open source media stream for vault encryption")

        return targetFile
    }

    /**
     * Decrypts a vault file into a temporary cache file inside cache/temp/ for viewing.
     */
    fun decryptVaultFileToTemp(context: Context, vaultFileName: String, extension: String): File? {
        val vaultDir = AppStorageDirs.privateVaultDir(context)
        val safeName = File(vaultFileName).name
        val sourceFile = File(vaultDir, safeName)
        if (!sourceFile.exists()) return null

        val tempDir = AppStorageDirs.tempCacheDir(context)
        val cleanExt = extension.replace(Regex("[^a-zA-Z0-9]"), "").ifEmpty { "bin" }
        val outFile = File(tempDir, "${safeName.removeSuffix(".enc")}.$cleanExt")
        if (outFile.exists() && outFile.length() > 0L) return outFile
        val partFile = File(tempDir, "${outFile.name}.${System.nanoTime()}.part")
        partFile.delete()

        FileInputStream(sourceFile).use { fis ->
            val iv = ByteArray(GCM_IV_BYTES)
            val read = fis.read(iv)
            if (read != GCM_IV_BYTES) return null

            val cipher = Cipher.getInstance(GCM_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateGcmKey(MEDIA_KEY_ALIAS),
                GCMParameterSpec(GCM_TAG_BITS, iv)
            )

            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(partFile).use { fos ->
                    cis.copyTo(fos, bufferSize = 16 * 1024)
                }
            }
        }
        if (!partFile.renameTo(outFile)) {
            partFile.delete()
            return if (outFile.exists() && outFile.length() > 0L) outFile else null
        }
        return outFile
    }

    fun deleteVaultFile(context: Context, vaultFileName: String) {
        val safeName = File(vaultFileName).name
        File(AppStorageDirs.privateVaultDir(context), safeName).delete()
    }
}
