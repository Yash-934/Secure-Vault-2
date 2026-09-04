package com.quantumvault.wkqpx.security

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages authenticated sentinels to cryptographically verify unwrapped Vault Root Keys (VRK).
 * Prevents unauthorized session access using random or incorrectly unwrapped keys.
 */
object VaultSentinelManager {
    private const val TAG = "VaultSentinelManager"

    private const val REAL_SENTINEL_FILE = "vault_sentinel.bin"
    private const val DECOY_SENTINEL_FILE = "decoy_vault_sentinel.bin"

    private val REAL_SENTINEL_PLAINTEXT = "QUANTUM_VAULT_REAL_SENTINEL_OK_V1".toByteArray(Charsets.UTF_8)
    private val REAL_SENTINEL_AAD = "QUANTUM_VAULT_REAL_SENTINEL_AAD".toByteArray(Charsets.UTF_8)

    private val DECOY_SENTINEL_PLAINTEXT = "QUANTUM_VAULT_DECOY_SENTINEL_OK_V1".toByteArray(Charsets.UTF_8)
    private val DECOY_SENTINEL_AAD = "QUANTUM_VAULT_DECOY_SENTINEL_AAD".toByteArray(Charsets.UTF_8)

    private const val REAL_CONTEXT = "quantum_vault_real_sentinel_context"
    private const val DECOY_CONTEXT = "quantum_vault_decoy_sentinel_context"

    private const val GCM_TAG_LENGTH_BITS = 128
    private const val IV_SIZE_BYTES = 12

    private fun deriveSentinelKey(vrk: ByteArray, isDecoy: Boolean): SecretKeySpec {
        val md = MessageDigest.getInstance("SHA-256")
        val context = if (isDecoy) DECOY_CONTEXT else REAL_CONTEXT
        md.update(context.toByteArray(Charsets.UTF_8))
        md.update(vrk)
        return SecretKeySpec(md.digest(), "AES")
    }

    /**
     * Creates and atomically writes the authenticated sentinel for the given VRK.
     */
    fun createSentinel(context: Context, vrk: ByteArray, isDecoy: Boolean = false): Boolean {
        return try {
            val key = deriveSentinelKey(vrk, isDecoy)
            val iv = ByteArray(IV_SIZE_BYTES).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(if (isDecoy) DECOY_SENTINEL_AAD else REAL_SENTINEL_AAD)

            val plaintext = if (isDecoy) DECOY_SENTINEL_PLAINTEXT else REAL_SENTINEL_PLAINTEXT
            val ciphertext = cipher.doFinal(plaintext)

            val fileName = if (isDecoy) DECOY_SENTINEL_FILE else REAL_SENTINEL_FILE
            val targetFile = File(context.filesDir, fileName)
            val tempFile = File(context.filesDir, "$fileName.tmp")

            FileOutputStream(tempFile).use { fos ->
                fos.write(iv.size)
                fos.write(iv)
                fos.write(ciphertext)
                fos.flush()
                fos.fd.sync()
            }

            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sentinel (isDecoy=$isDecoy)", e)
            false
        }
    }

    /**
     * Cryptographically verifies the given VRK against the stored sentinel.
     * Returns true ONLY if the key successfully authenticates and decrypts the sentinel payload.
     */
    fun verifyVrk(context: Context, vrk: ByteArray, isDecoy: Boolean = false): Boolean {
        if (vrk.size != 32) return false
        val fileName = if (isDecoy) DECOY_SENTINEL_FILE else REAL_SENTINEL_FILE
        val sentinelFile = File(context.filesDir, fileName)

        // If sentinel does not exist yet (first initialization or upgrade), initialize it
        if (!sentinelFile.exists() || sentinelFile.length() < (1 + IV_SIZE_BYTES + 16)) {
            return createSentinel(context, vrk, isDecoy)
        }

        return try {
            val bytes = sentinelFile.readBytes()
            if (bytes.size < 1 + IV_SIZE_BYTES + 16) return false

            val ivLen = bytes[0].toInt() and 0xFF
            if (ivLen != IV_SIZE_BYTES || bytes.size <= 1 + ivLen) return false

            val iv = bytes.copyOfRange(1, 1 + ivLen)
            val ciphertext = bytes.copyOfRange(1 + ivLen, bytes.size)

            val key = deriveSentinelKey(vrk, isDecoy)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
            cipher.updateAAD(if (isDecoy) DECOY_SENTINEL_AAD else REAL_SENTINEL_AAD)

            val decrypted = cipher.doFinal(ciphertext)
            val expectedPlaintext = if (isDecoy) DECOY_SENTINEL_PLAINTEXT else REAL_SENTINEL_PLAINTEXT
            val matches = decrypted.contentEquals(expectedPlaintext)
            decrypted.fill(0)
            matches
        } catch (e: Exception) {
            Log.w(TAG, "Sentinel verification failed (isDecoy=$isDecoy): ${e.message}")
            false
        }
    }

    /**
     * Removes the decoy sentinel when the decoy PIN is disabled.
     */
    fun removeDecoySentinel(context: Context) {
        try {
            File(context.filesDir, DECOY_SENTINEL_FILE).delete()
        } catch (_: Exception) {}
    }
}
