package com.example.security

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Biometric & Device Credential Authentication Manager.
 * 
 * Security Logic:
 * Uses AndroidX Biometric library to require authenticating via Fingerprint, Face ID,
 * or Device PIN/Pattern/Password before unlocking vault access.
 */
class BiometricPromptManager(private val context: Context) {

    sealed interface AuthResult {
        object Success : AuthResult
        data class Error(val message: String) : AuthResult
        object HardwareUnavailable : AuthResult
        object NotEnrolled : AuthResult
        object Cancelled : AuthResult
    }

    private val allowedAuthenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BIOMETRIC_STRONG or BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_STRONG or BIOMETRIC_WEAK
    }

    /**
     * Checks if Biometric or Device PIN authentication is available on the device.
     */
    fun canAuthenticate(): Boolean {
        val biometricManager = BiometricManager.from(context)
        val status = biometricManager.canAuthenticate(allowedAuthenticators)
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Displays the Biometric / Device PIN authentication dialog.
     */
    fun showBiometricPrompt(
        activity: FragmentActivity,
        title: String = "Secure Vault Unlock",
        subtitle: String = "Authenticate to access encrypted files",
        onResult: (AuthResult) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onResult(AuthResult.Success)
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
                onResult(AuthResult.Error("Authentication failed. Please try again."))
            }
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(allowedAuthenticators)

        // For Android API < 30 without DEVICE_CREDENTIAL in authenticators, set negative button
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
            (allowedAuthenticators and DEVICE_CREDENTIAL) == 0
        ) {
            promptInfoBuilder.setNegativeButtonText("Cancel")
        }

        val promptInfo = promptInfoBuilder.build()
        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}
