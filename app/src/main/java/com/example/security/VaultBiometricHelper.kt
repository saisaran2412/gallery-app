package com.example.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

enum class BiometricCapability {
    STRONG_CRYPTO,
    WEAK_ONLY,
    NOT_ENROLLED,
    UNAVAILABLE
}

/**
 * Encapsulates AndroidX BiometricManager capability checks and BiometricPrompt flows,
 * supporting both hardware-backed CryptoObject (Class 3 / BIOMETRIC_STRONG) and fallback
 * Class 2 biometrics when strong hardware is absent.
 */
object VaultBiometricHelper {

    fun checkCapability(context: Context): BiometricCapability {
        val manager = BiometricManager.from(context)
        val strongStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (strongStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            return BiometricCapability.STRONG_CRYPTO
        }
        val weakStatus = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        return when (weakStatus) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.WEAK_ONLY
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
            else -> BiometricCapability.UNAVAILABLE
        }
    }

    fun authenticateForUnlock(
        activity: FragmentActivity,
        cipher: Cipher?,
        onSuccess: (Cipher?) -> Unit,
        onCancelOrUsePin: () -> Unit,
        onError: (String) -> Unit,
        onFailedAttempt: () -> Unit = {}
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result.cryptoObject?.cipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onCancelOrUsePin()
                        else -> onError(errString.toString())
                    }
                }

                override fun onAuthenticationFailed() {
                    onFailedAttempt()
                }
            }
        )

        val authenticators = if (cipher != null) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Hidden Vault")
            .setSubtitle("Authenticate to unlock the encrypted vault database")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Use PIN")
            .setConfirmationRequired(false)
            .build()

        if (cipher != null) {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } else {
            prompt.authenticate(info)
        }
    }

    fun authenticateForEnrollment(
        activity: FragmentActivity,
        cipher: Cipher?,
        onSuccess: (Cipher?) -> Unit,
        onCancel: () -> Unit,
        onError: (String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess(result.cryptoObject?.cipher)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    when (errorCode) {
                        BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                        BiometricPrompt.ERROR_USER_CANCELED,
                        BiometricPrompt.ERROR_CANCELED -> onCancel()
                        else -> onError(errString.toString())
                    }
                }
            }
        )

        val authenticators = if (cipher != null) {
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        }

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Enable Biometric Unlock")
            .setSubtitle("Bind biometric authentication to your encrypted Vault key")
            .setAllowedAuthenticators(authenticators)
            .setNegativeButtonText("Cancel")
            .build()

        if (cipher != null) {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
        } else {
            prompt.authenticate(info)
        }
    }
}
