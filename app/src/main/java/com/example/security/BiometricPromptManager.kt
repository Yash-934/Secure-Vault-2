package com.example.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/**
 * Biometric & Hardware Keystore Authentication Manager.
 * 
 * Enforces hardware cryptographic binding:
 * Authentication is bound to a user-authenticated Keystore AES key (setUserAuthenticationRequired(true)).
 * An unlock payload is derived strictly via the authenticated Cipher in CryptoObject.
 */
class BiometricPromptManager(private val context: Context) {

    sealed interface AuthResult {
        data class Success(val authPayload: ByteArray) : AuthResult
        data class Error(val message: String) : AuthResult
        object HardwareUnavailable : AuthResult
        object NotEnrolled : AuthResult
        object Cancelled : AuthResult
    }

    private val allowedAuthenticators = BIOMETRIC_STRONG

    /**
     * Checks if Strong Biometric authentication is available on the device.
     */
    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val status = biometricManager.canAuthenticate(allowedAuthenticators)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun createBiometricCryptoObject(): BiometricPrompt.CryptoObject? {
        return try {
            val key = VaultKeyManager.getOrCreateBiometricMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key)
            BiometricPrompt.CryptoObject(cipher)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Displays the Cryptographic Biometric authentication dialog.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Vault Biometric Unlock",
        subtitle: String = "Hardware-authenticated cryptographic unlock",
        onResult: (AuthResult) -> Unit
    ) {
        val cryptoObject = createBiometricCryptoObject()
        if (cryptoObject == null) {
            onResult(AuthResult.Error("Hardware cryptographic biometric key unavailable"))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val authCipher = result.cryptoObject?.cipher
                if (authCipher == null) {
                    onResult(AuthResult.Error("Cryptographic object missing in biometric callback"))
                    return
                }

                try {
                    val challenge = "QVLT_BIOMETRIC_AUTH_CHALLENGE_${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)
                    val proof = authCipher.doFinal(challenge)
                    if (proof != null && proof.isNotEmpty()) {
                        onResult(AuthResult.Success(proof))
                    } else {
                        onResult(AuthResult.Error("Cryptographic authorization verification returned empty proof"))
                    }
                } catch (e: Exception) {
                    onResult(AuthResult.Error("Cryptographic proof derivation failed: ${e.message}"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onResult(AuthResult.Cancelled)
                } else {
                    onResult(AuthResult.Error(errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(AuthResult.Error("Biometric authentication rejected by sensor"))
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)
            .setNegativeButtonText("Cancel")
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo, cryptoObject)
    }
}
