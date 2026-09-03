package com.example.security

import android.content.Context
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.UserNotAuthenticatedException
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricPromptManager(private val context: Context) {

    enum class BiometricStatus {
        AVAILABLE,
        NO_BIOMETRIC_ENROLLED,
        HARDWARE_NOT_PRESENT,
        HARDWARE_UNAVAILABLE,
        DEVICE_NOT_SECURE,
        SECURITY_UPDATE_REQUIRED,
        UNSUPPORTED
    }

    sealed interface AuthResult {
        object Success : AuthResult
        data class Error(val status: BiometricStatus? = null, val message: String) : AuthResult
        object EnvelopeMissing : AuthResult
        object KeyInvalidated : AuthResult
        object AuthenticationRequired : AuthResult
        object Cancelled : AuthResult
    }

    private val allowedAuthenticators = BIOMETRIC_STRONG

    fun canAuthenticate(): BiometricStatus {
        val biometricManager = BiometricManager.from(context)
        val status = biometricManager.canAuthenticate(allowedAuthenticators)
        return when (status) {
            BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.AVAILABLE
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NO_BIOMETRIC_ENROLLED
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.HARDWARE_NOT_PRESENT
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> BiometricStatus.HARDWARE_UNAVAILABLE
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> BiometricStatus.SECURITY_UPDATE_REQUIRED
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> BiometricStatus.UNSUPPORTED
            else -> BiometricStatus.DEVICE_NOT_SECURE
        }
    }

    fun getStatusMessage(status: BiometricStatus): String {
        return when (status) {
            BiometricStatus.AVAILABLE -> "Biometric authentication available."
            BiometricStatus.NO_BIOMETRIC_ENROLLED -> "Enroll a strong biometric in Android Settings, then enable Biometric Unlock."
            BiometricStatus.HARDWARE_NOT_PRESENT -> "Strong biometric hardware is unavailable on this device."
            BiometricStatus.HARDWARE_UNAVAILABLE -> "Biometric hardware is currently unavailable. Try again later."
            BiometricStatus.DEVICE_NOT_SECURE -> "Device does not meet security requirements for strong biometrics."
            BiometricStatus.SECURITY_UPDATE_REQUIRED -> "A security update is required to use biometric authentication."
            BiometricStatus.UNSUPPORTED -> "Biometric authentication is not supported on this device."
        }
    }

    fun showBiometricEnrollPrompt(
        activity: FragmentActivity,
        onResult: (AuthResult) -> Unit
    ) {
        val status = canAuthenticate()
        if (status != BiometricStatus.AVAILABLE) {
            onResult(AuthResult.Error(status, getStatusMessage(status)))
            return
        }

        val cryptoObject = try {
            val obj = VaultKeyManager.getBiometricEnrollCryptoObject(context)
            if (obj == null && !VaultKeyManager.isRealVaultAuthorized()) {
                onResult(AuthResult.AuthenticationRequired)
                return
            }
            obj
        } catch (e: KeyPermanentlyInvalidatedException) {
            onResult(AuthResult.KeyInvalidated)
            return
        } catch (e: Exception) {
            null
        }

        if (cryptoObject == null) {
            onResult(AuthResult.Error(null, "Failed to initialize biometric prompt."))
            return
        }

        showPrompt(activity, "Enroll Biometric Unlock", "Authenticate to provision hardware vault key", cryptoObject, isEnrollment = true, onResult = onResult)
    }

    fun showBiometricUnlockPrompt(
        activity: FragmentActivity,
        onResult: (AuthResult) -> Unit
    ) {
        val status = canAuthenticate()
        if (status != BiometricStatus.AVAILABLE) {
            onResult(AuthResult.Error(status, getStatusMessage(status)))
            return
        }

        if (!VaultKeyManager.hasBiometricEnvelope(context)) {
            onResult(AuthResult.EnvelopeMissing)
            return
        }

        val cryptoObject = try {
            VaultKeyManager.getBiometricDecryptCryptoObject(context)
        } catch (e: KeyPermanentlyInvalidatedException) {
            onResult(AuthResult.KeyInvalidated)
            return
        } catch (e: Exception) {
            null
        }

        if (cryptoObject == null) {
            onResult(AuthResult.Error(null, "Hardware cryptographic key unavailable or corrupted."))
            return
        }

        showPrompt(activity, "Vault Biometric Unlock", "Hardware-authenticated cryptographic unlock", cryptoObject, isEnrollment = false, onResult = onResult)
    }

    fun showPrompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cryptoObject: BiometricPrompt.CryptoObject,
        isEnrollment: Boolean = false,
        onResult: (AuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                val authCipher = result.cryptoObject?.cipher
                if (authCipher == null) {
                    onResult(AuthResult.Error(null, "Cryptographic object missing in biometric callback"))
                    return
                }

                try {
                    if (isEnrollment) {
                        val success = VaultKeyManager.provisionBiometricEnvelope(context, authCipher)
                        if (success) {
                            onResult(AuthResult.Success)
                        } else {
                            onResult(AuthResult.Error(null, "Failed to provision biometric envelope."))
                        }
                    } else {
                        val success = VaultKeyManager.unwrapBiometricSessionKey(context, authCipher)
                        if (success) {
                            onResult(AuthResult.Success)
                        } else {
                            onResult(AuthResult.Error(null, "Cryptographic authorization unwrap error: Sentinel check failed."))
                        }
                    }
                } catch (e: KeyPermanentlyInvalidatedException) {
                    onResult(AuthResult.KeyInvalidated)
                } catch (e: UserNotAuthenticatedException) {
                    onResult(AuthResult.Error(null, "User authentication expired or not confirmed"))
                } catch (e: Exception) {
                    onResult(AuthResult.Error(null, "Cryptographic authorization unwrap error: ${e.message}"))
                }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                ) {
                    onResult(AuthResult.Cancelled)
                } else {
                    onResult(AuthResult.Error(null, errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                onResult(AuthResult.Error(null, "Biometric authentication rejected by sensor"))
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
