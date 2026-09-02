package com.example.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Biometric & Hardware Keystore Authentication Manager.
 * 
 * Enforces true hardware cryptographic binding:
 * Authentication is bound to a user-authenticated Keystore AES key (setUserAuthenticationRequired(true)).
 * An unlock key is unwrapped strictly via the authenticated Cipher in CryptoObject.
 */
class BiometricPromptManager(private val context: Context) {

    sealed interface AuthResult {
        data class Success(val unwrappedKey: SecretKey) : AuthResult
        data class Error(val message: String) : AuthResult
        object KeyInvalidated : AuthResult
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

    /**
     * Displays the Cryptographic Biometric authentication dialog and unwraps the real vault key.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Vault Biometric Unlock",
        subtitle: String = "Hardware-authenticated cryptographic unlock",
        onResult: (AuthResult) -> Unit
    ) {
        val cryptoObject = try {
            VaultKeyManager.getBiometricDecryptCryptoObject(context)
        } catch (e: KeyPermanentlyInvalidatedException) {
            onResult(AuthResult.KeyInvalidated)
            return
        } catch (e: Exception) {
            null
        }

        if (cryptoObject == null) {
            onResult(AuthResult.Error("Hardware cryptographic biometric key unavailable or not enrolled"))
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
                    val unwrappedKey = VaultKeyManager.unwrapBiometricSessionKey(context, authCipher)
                    if (unwrappedKey != null) {
                        onResult(AuthResult.Success(unwrappedKey))
                    } else {
                        onResult(AuthResult.Error("Cryptographic key unwrap failed: Authentication proof invalid"))
                    }
                } catch (e: KeyPermanentlyInvalidatedException) {
                    onResult(AuthResult.KeyInvalidated)
                } catch (e: UserNotAuthenticatedException) {
                    onResult(AuthResult.Error("User authentication expired or not confirmed"))
                } catch (e: Exception) {
                    onResult(AuthResult.Error("Cryptographic authorization unwrap error: ${e.message}"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onResult(AuthResult.Cancelled)
                } else if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                    errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT
                ) {
                    onResult(AuthResult.HardwareUnavailable)
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
