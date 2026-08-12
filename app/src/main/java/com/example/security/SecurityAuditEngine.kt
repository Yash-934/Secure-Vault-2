package com.example.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Diagnostic engine that performs local, non-invasive security environment scans.
 * Guaranteed zero user data access and zero network usage.
 */
@Singleton
class SecurityAuditEngine @Inject constructor(
    private val context: Context
) {

    /**
     * Performs a 5-point security diagnostic audit of the local Android environment.
     *
     * @return AuditResult containing PASS/FAIL status, individual check results, and timestamp.
     */
    fun performSecurityAudit(): AuditResult {
        val internetCheck = checkInternetPermissionDenied()
        val keystoreCheck = checkKeystoreIntegrity()
        val cryptoCheck = checkAesGcmCryptoTest()
        val biometricCheck = checkBiometricAvailability()
        val storageCheck = checkPrivateStoragePath()
        val rootEnvironmentCheck = !RootDetectionManager.isDeviceRooted(context)

        val checkResults = mapOf(
            "Internet Permission Denied (Network Isolation)" to internetCheck,
            "Android Keystore Key Integrity" to keystoreCheck,
            "AES-256-GCM Buffer Encryption Test" to cryptoCheck,
            "Biometric Hardware & Prompt Availability" to biometricCheck,
            "App-Private Storage Path Isolation" to storageCheck,
            "Root / Custom ROM Tamper Protection" to rootEnvironmentCheck
        )

        val allPassed = checkResults.values.all { it }
        val status = if (allPassed) "PASS" else "FAIL"

        return AuditResult(
            status = status,
            checkResults = checkResults,
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * 1. Check if INTERNET permission is granted (Must return 'SECURE' / true if denied).
     */
    fun checkInternetPermissionDenied(): Boolean {
        return try {
            val permissionState = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.INTERNET
            )
            // SECURE state means INTERNET permission is denied (NOT granted)
            permissionState != PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 2. Check Keystore for key existence and integrity.
     */
    fun checkKeystoreIntegrity(): Boolean {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "SecureVaultAES256MasterKey"
            if (!keyStore.containsAlias(alias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore"
                )
                val keyGenSpec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build()

                keyGenerator.init(keyGenSpec)
                keyGenerator.generateKey()
            }
            keyStore.containsAlias(alias)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 3. Perform a "dummy" AES-256-GCM encryption/decryption test on a string buffer.
     */
    fun checkAesGcmCryptoTest(): Boolean {
        return try {
            val sampleText = "VAULT_SECURITY_INTEGRITY_AUDIT_BUFFER_2026"
            val sampleBytes = sampleText.toByteArray(Charsets.UTF_8)

            val keyGenerator = KeyGenerator.getInstance("AES")
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()

            val cipherEncrypt = Cipher.getInstance("AES/GCM/NoPadding")
            cipherEncrypt.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipherEncrypt.iv
            val cipherText = cipherEncrypt.doFinal(sampleBytes)

            val cipherDecrypt = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipherDecrypt.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            val decryptedBytes = cipherDecrypt.doFinal(cipherText)

            val decryptedText = String(decryptedBytes, Charsets.UTF_8)
            decryptedText == sampleText
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 4. Verify Biometric prompt availability using BiometricManager.
     */
    fun checkBiometricAvailability(): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK or
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
            } else {
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        BiometricManager.Authenticators.BIOMETRIC_WEAK
            }
            val status = biometricManager.canAuthenticate(authenticators)
            status == BiometricManager.BIOMETRIC_SUCCESS ||
                    status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 5. Verify internal storage path is using app-private directories.
     */
    fun checkPrivateStoragePath(): Boolean {
        return try {
            val filesDir = context.filesDir
            val absolutePath = filesDir.absolutePath
            val isAppPrivate = (absolutePath.startsWith("/data/") || absolutePath.startsWith("/user/")) &&
                    absolutePath.contains(context.packageName)
            val isWritable = filesDir.exists() && filesDir.canWrite()
            isAppPrivate && isWritable
        } catch (e: Exception) {
            false
        }
    }
}
