package com.quantumvault.wkqpx.security

import android.util.Base64
import android.util.Log
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Isolated legacy password migration mechanism.
 * Handles detection and one-time decryption of historical password records created
 * with earlier application key formats before Vault Root Key (VRK) isolation.
 *
 * NOTE: This legacy key is NEVER used for newly created records.
 */
object LegacyPasswordMigrator {
    private const val TAG = "LegacyPasswordMigrator"

    const val LEGACY_PREFIX = "LEGACY:"
    const val LEGACY_PLAIN_B64_PREFIX = "ENC_PLAIN_B64:"

    // Isolated historical key for migrating pre-VRK records
    private val LEGACY_STATIC_KEY = SecretKeySpec(
        "QuantumVaultPasswordStaticKey2024!".toByteArray(Charsets.UTF_8).copyOf(32),
        "AES"
    )

    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val TAG_LENGTH_BIT = 128
    private const val IV_LENGTH_BYTE = 12

    /**
     * Checks if the ciphertext blob matches a known legacy format.
     */
    fun isLegacyBlob(blob: String): Boolean {
        if (blob.isBlank()) return false
        if (blob.startsWith(LEGACY_PREFIX) || blob.startsWith(LEGACY_PLAIN_B64_PREFIX)) return true
        if (blob.startsWith(PasswordCryptoHelper.FORMAT_V2_PREFIX)) return false
        // Try decoding base64 to see if it could be a legacy payload
        return try {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            bytes.size >= (IV_LENGTH_BYTE + 16)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Attempts to decrypt a legacy record.
     * Returns the plaintext string if successfully decrypted and authenticated, or null otherwise.
     */
    fun decryptLegacyRecord(blob: String): String? {
        if (blob.isBlank()) return ""

        // Format 1: ENC_PLAIN_B64:
        if (blob.startsWith(LEGACY_PLAIN_B64_PREFIX)) {
            return try {
                val rawB64 = blob.removePrefix(LEGACY_PLAIN_B64_PREFIX)
                val decoded = Base64.decode(rawB64, Base64.NO_WRAP)
                String(decoded, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode legacy plain B64 record", e)
                null
            }
        }

        // Format 2: LEGACY:<base64> or raw base64 with historical static key
        val cleanBlob = if (blob.startsWith(LEGACY_PREFIX)) {
            blob.removePrefix(LEGACY_PREFIX)
        } else {
            blob
        }

        return try {
            val decoded = Base64.decode(cleanBlob, Base64.NO_WRAP)
            if (decoded.size < IV_LENGTH_BYTE + 16) return null

            val iv = decoded.copyOfRange(0, IV_LENGTH_BYTE)
            val cipherText = decoded.copyOfRange(IV_LENGTH_BYTE, decoded.size)

            val cipher = Cipher.getInstance(ALGORITHM)
            val spec = GCMParameterSpec(TAG_LENGTH_BIT, iv)
            cipher.init(Cipher.DECRYPT_MODE, LEGACY_STATIC_KEY, spec)
            val decryptedBytes = cipher.doFinal(cipherText)
            val plaintext = String(decryptedBytes, Charsets.UTF_8)
            decryptedBytes.fill(0)
            plaintext
        } catch (e: GeneralSecurityException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error in legacy decrypt", e)
            null
        }
    }

    /**
     * Helper strictly for testing migration workflows.
     */
    @androidx.annotation.VisibleForTesting
    fun encryptWithLegacyKeyForTesting(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH_BYTE)
        java.security.SecureRandom().nextBytes(iv)
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, LEGACY_STATIC_KEY, GCMParameterSpec(TAG_LENGTH_BIT, iv))
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + encrypted.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encrypted, 0, combined, iv.size, encrypted.size)
        return LEGACY_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }
}
