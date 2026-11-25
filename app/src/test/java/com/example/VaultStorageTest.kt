package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.MediaItemEntity
import com.example.data.local.MediaType
import com.example.data.local.vault.VaultAuditLogEntity
import com.example.data.local.vault.VaultDatabase
import com.example.data.local.vault.toVaultItemEntity
import com.example.security.BiometricCapability
import com.example.security.VaultCryptoManager
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.FolioAlbumTheme
import com.example.ui.viewmodel.BiometricPrepareResult
import com.example.ui.viewmodel.VaultUiState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.IvParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultStorageTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun vaultDatabase_storesItemsAndAuditLogsAndMapsEntities() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = VaultDatabase.openWithCustomFactory(context, inMemory = true)
        try {
            val dao = db.vaultDao()
            val media = MediaItemEntity(
                id = "vault_test_1",
                mediaStoreId = 99L,
                uriString = "content://media/external/images/media/99",
                mediaType = MediaType.PHOTO,
                displayName = "passport.jpg",
                mimeType = "image/jpeg",
                dateTakenMillis = 1_700_000_000_000L,
                dateAddedMillis = 1_700_000_000_000L,
                dateModifiedMillis = 1_700_000_000_000L,
                width = 1600,
                height = 1200,
                sizeBytes = 420_000L,
                note = "Confidential scan"
            )

            val vaultEntity = media.toVaultItemEntity("vault_secret_1.enc")
            dao.upsertVaultItem(vaultEntity)
            dao.insertAuditLog(VaultAuditLogEntity(eventType = "ENCRYPT", details = "Encrypted 1 item"))

            val items = dao.observeVaultItems().first()
            assertEquals(1, items.size)
            val restored = items.first().toMediaItemEntity()
            assertEquals("vault_test_1", restored.id)
            assertEquals("vault_secret_1.enc", restored.vaultFileName)
            assertTrue(restored.isInVault)
            assertEquals("Confidential scan", restored.note)

            val logs = dao.observeRecentAuditLogs().first()
            assertEquals(1, logs.size)
            assertEquals("ENCRYPT", logs.first().eventType)

            dao.deleteVaultItemById("vault_test_1")
            assertNull(dao.getVaultItemById("vault_test_1"))
        } finally {
            VaultDatabase.lockAndClose()
        }
    }

    @Test
    fun vaultCryptoManager_sealsUnsealsBiometricKeyBlobAndZerosMemory() {
        val rawKey = VaultCryptoManager.generateRandomDatabaseKey()
        assertEquals(32, rawKey.size)
        val originalCopy = rawKey.copyOf()

        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val testSecretKey = keyGen.generateKey()

        val encCipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.ENCRYPT_MODE, testSecretKey)
        }
        val blob = VaultCryptoManager.sealDatabaseKeyWithBiometricCipher(encCipher, rawKey)

        val iv = android.util.Base64.decode(blob, android.util.Base64.NO_WRAP).copyOfRange(0, 16)
        val decCipher = Cipher.getInstance("AES/CBC/PKCS5Padding").apply {
            init(Cipher.DECRYPT_MODE, testSecretKey, IvParameterSpec(iv))
        }
        val unsealed = VaultCryptoManager.unsealDatabaseKeyWithBiometricCipher(decCipher, blob)
        assertArrayEquals(originalCopy, unsealed)

        VaultCryptoManager.zeroOut(rawKey)
        assertTrue(rawKey.all { it == 0.toByte() })
    }

    @Test
    fun vaultScreen_pinSetupAndBiometricGateRenderProperly() {
        var submittedPin = ""

        composeRule.setContent {
            FolioAlbumTheme {
                VaultScreen(
                    state = VaultUiState(
                        isUnlocked = false,
                        hasPinConfigured = false,
                        biometricEnabled = false,
                        biometricCapability = BiometricCapability.STRONG_CRYPTO
                    ),
                    onBack = {},
                    onSetupPin = { submittedPin = it },
                    onVerifyPin = {},
                    onPrepareBiometricUnlock = { BiometricPrepareResult.Ready(null) },
                    onBiometricUnlockSuccess = {},
                    onPrepareBiometricEnrollment = { BiometricPrepareResult.Ready(null) },
                    onBiometricEnrollmentSuccess = {},
                    onDisableBiometric = {},
                    onBiometricError = {},
                    onLockVault = {},
                    onRequestDecrypt = {},
                    onRemoveFromVault = {}
                )
            }
        }

        composeRule.onNodeWithText("Create Vault PIN").assertIsDisplayed()
        composeRule.onNodeWithTag("pin_key_1").performScrollTo().performClick()
        composeRule.onNodeWithTag("pin_key_2").performScrollTo().performClick()
        composeRule.onNodeWithTag("pin_key_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("pin_key_4").performScrollTo().performClick()
        composeRule.onNodeWithTag("vault_submit_pin_button").performScrollTo().performClick()

        assertEquals("1234", submittedPin)
    }

    @Test
    fun vaultScreen_unlockedDisplaysSqlCipherAndBiometricToggle() {
        composeRule.setContent {
            FolioAlbumTheme {
                VaultScreen(
                    state = VaultUiState(
                        isUnlocked = true,
                        hasPinConfigured = true,
                        biometricEnabled = true,
                        biometricBoundWithCrypto = true,
                        biometricCapability = BiometricCapability.STRONG_CRYPTO
                    ),
                    onBack = {},
                    onSetupPin = {},
                    onVerifyPin = {},
                    onPrepareBiometricUnlock = { BiometricPrepareResult.Ready(null) },
                    onBiometricUnlockSuccess = {},
                    onPrepareBiometricEnrollment = { BiometricPrepareResult.Ready(null) },
                    onBiometricEnrollmentSuccess = {},
                    onDisableBiometric = {},
                    onBiometricError = {},
                    onLockVault = {},
                    onRequestDecrypt = {},
                    onRemoveFromVault = {}
                )
            }
        }

        composeRule.onNodeWithText("Room + SQLCipher • Keystore AES-256").assertIsDisplayed()
        composeRule.onNodeWithText("Bound to hardware CryptoObject key").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_biometric_switch").assertIsDisplayed()
        composeRule.onNodeWithTag("vault_audit_log_button").assertIsDisplayed()
    }
}
